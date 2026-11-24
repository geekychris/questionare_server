package com.example.questionnaire.config;

import com.example.questionnaire.security.CloudstackOidcUserService;
import com.example.questionnaire.security.OidcSettings;
import com.example.questionnaire.security.PortFollowingAuthorizationRequestResolver;
import com.example.questionnaire.security.PortFollowingLogoutSuccessHandler;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * Security model (README "Authentication", docs/RESPONDENTS.md):
 * <ul>
 *   <li>Admin/creator UI (Vaadin, {@code /}) and JSON API ({@code /api/**}): OIDC login against the
 *       site's cloud-stack provider (registrationId {@code cloudstack}); roles from the {@code roles} claim.</li>
 *   <li>Respondent surface {@code /r/**}: public; the controller enforces per-campaign
 *       {@code requireLogin} and token state.</li>
 *   <li>{@code /actuator/health} and {@code GET /api/mail/status} ({@code {enabled}}): public for probes.</li>
 * </ul>
 * Browsers without a session are redirected to {@code /oauth2/authorization/cloudstack};
 * {@code /api/**} and the respondent JSON calls answer 401 instead.
 * <p>
 * CSRF: on for everything except Vaadin's internal requests (Vaadin has its own token) and the
 * respondent submit {@code POST /r/{token}/responses}, which instead requires
 * {@code Content-Type: application/json} plus the custom {@code X-Respondent: 1} header —
 * neither can be sent cross-site without a CORS preflight, and no CORS is allowed.
 * JSON API clients fetch the token from {@code GET /api/csrf} and send it as {@code X-CSRF-TOKEN}.
 * <p>
 * The {@code dev} profile ({@code app.auth.dev-login=true}) replaces OIDC with a local form login.
 */
@EnableWebSecurity
@Configuration
@EnableConfigurationProperties(OidcSettings.class)
public class SecurityConfig extends VaadinWebSecurity {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Requests that get a 401 rather than a login redirect. */
    public static final RequestMatcher API_REQUESTS = new OrRequestMatcher(
            antMatcher("/api/**"),
            antMatcher("/r/*/form"),
            antMatcher("/r/*/responses"));

    private final OidcSettings oidc;
    private final ObjectProvider<ClientRegistrationRepository> registrations;
    private final boolean devLogin;

    public SecurityConfig(OidcSettings oidc,
                          ObjectProvider<ClientRegistrationRepository> registrations,
                          @Value("${app.auth.dev-login:false}") boolean devLogin) {
        this.oidc = oidc;
        this.registrations = registrations;
        this.devLogin = devLogin;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(a -> a
                .requestMatchers(antMatcher("/actuator/health"), antMatcher("/actuator/health/**")).permitAll()
                .requestMatchers(antMatcher("/r/**")).permitAll()
                .requestMatchers(antMatcher("/error")).permitAll()
                // Only {enabled}: lets probes and the deploy's smoke test see whether invites can be emailed.
                .requestMatchers(antMatcher(HttpMethod.GET, "/api/mail/status")).permitAll()
                .requestMatchers(antMatcher("/actuator/**")).denyAll()
                .requestMatchers(antMatcher("/api/me"), antMatcher("/api/csrf")).authenticated()
                .requestMatchers(antMatcher(HttpMethod.DELETE, "/api/campaigns/*")).hasRole("ADMIN")
                .requestMatchers(antMatcher("/api/responses/**")).hasRole("ADMIN")
                .requestMatchers(antMatcher("/api/**")).hasAnyRole("ADMIN", "CREATOR")
                // Admin UI routes (Vaadin views also carry @RolesAllowed).
                .requestMatchers(antMatcher("/"), antMatcher("/campaigns/**")).hasAnyRole("ADMIN", "CREATOR"));

        // API calls get 401; every other unauthenticated request goes to the login (explicitly, so
        // it does not depend on the client's Accept header).
        String loginUrl = devLogin ? "/login" : "/oauth2/authorization/" + OidcSettings.REGISTRATION_ID;
        http.exceptionHandling(e -> e
                .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API_REQUESTS)
                .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint(loginUrl), AnyRequestMatcher.INSTANCE));

        http.csrf(c -> c.ignoringRequestMatchers(antMatcher(HttpMethod.POST, "/r/*/responses")));

        http.headers(h -> h.frameOptions(f -> f.sameOrigin()));

        if (devLogin) {
            log.warn("app.auth.dev-login=true: local form login, NOT for deployment");
            http.formLogin(f -> f.permitAll());
            http.logout(l -> l.logoutSuccessUrl("/"));
        } else {
            oidc.validate();
            ClientRegistrationRepository repo = registrations.getObject();
            http.oauth2Login(o -> o
                    .authorizationEndpoint(ae -> ae.authorizationRequestResolver(
                            new PortFollowingAuthorizationRequestResolver(repo, oidc.issuerBase())))
                    .userInfoEndpoint(u -> u.oidcUserService(new CloudstackOidcUserService()))
                    .failureHandler(loginFailureHandler()));
            http.logout(l -> l.logoutSuccessHandler(new PortFollowingLogoutSuccessHandler(repo, oidc.issuerBase())));
        }

        // Vaadin's defaults: framework requests permitted, Vaadin CSRF, request cache,
        // view access control, and finally anyRequest().authenticated().
        super.configure(http);
    }

    /** A login without any questionnaire role (or any other failure) ends on a plain 403 page, not a loop. */
    private static AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String code = exception instanceof OAuth2AuthenticationException o ? o.getError().getErrorCode() : "login_failed";
            log.info("OIDC login refused: {}", code);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/html;charset=UTF-8");
            String msg = CloudstackOidcUserService.ACCESS_DENIED.equals(code)
                    ? "Your account has no role in the questionnaire app."
                    : "Sign-in failed.";
            response.getOutputStream().write(("<!doctype html><meta charset=utf-8><title>Access denied</title>"
                    + "<h1>Access denied</h1><p>" + msg + "</p>").getBytes(StandardCharsets.UTF_8));
        };
    }

    /** OIDC client built from env (no discovery at startup, so the app starts while the provider is down). */
    @Bean
    @ConditionalOnProperty(name = "app.auth.dev-login", havingValue = "false", matchIfMissing = true)
    public ClientRegistrationRepository clientRegistrationRepository(OidcSettings settings) {
        settings.validate();
        String issuer = settings.issuerBase();
        String back = settings.backchannelBase();
        ClientRegistration reg = ClientRegistration.withRegistrationId(OidcSettings.REGISTRATION_ID)
                .clientId(settings.clientId())
                .clientSecret(settings.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .issuerUri(issuer)
                .authorizationUri(issuer + "/oidc/authorize")
                .tokenUri(back + "/oidc/token")
                .jwkSetUri(back + "/oidc/keys")
                .userNameAttributeName("preferred_username")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", issuer + "/oidc/end_session"))
                .clientName("cloud-stack")
                .build();
        log.info("OIDC client '{}' issuer={} backchannel={}", settings.clientId(), issuer, back);
        return new InMemoryClientRegistrationRepository(reg);
    }

    /** Dev profile only: local users admin / creator / respondent sharing one password. */
    @Bean
    @ConditionalOnProperty(name = "app.auth.dev-login", havingValue = "true")
    public UserDetailsService devUsers(@Value("${app.auth.dev-password:}") String password) {
        String pw = password;
        if (!StringUtils.hasText(pw)) {
            byte[] b = new byte[9];
            new SecureRandom().nextBytes(b);
            pw = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
            log.warn("Dev login password for users admin/creator/respondent (set DEV_PASSWORD to fix it): {}", pw);
        }
        PasswordEncoder enc = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        return new InMemoryUserDetailsManager(
                User.withUsername("admin").password(enc.encode(pw)).roles("ADMIN").build(),
                User.withUsername("creator").password(enc.encode(pw)).roles("CREATOR").build(),
                User.withUsername("respondent").password(enc.encode(pw)).roles("RESPONDENT").build());
    }
}
