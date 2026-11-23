package com.example.questionnaire.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * RP-initiated logout ({@code end_session} with {@code id_token_hint} and
 * {@code post_logout_redirect_uri={baseUrl}/}) that follows the request's port like the
 * authorization request does.
 */
public class PortFollowingLogoutSuccessHandler extends OidcClientInitiatedLogoutSuccessHandler {

    private final String issuerBase;

    public PortFollowingLogoutSuccessHandler(ClientRegistrationRepository repo, String issuerBase) {
        super(repo);
        this.issuerBase = issuerBase;
        setPostLogoutRedirectUri("{baseUrl}/");
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        return RequestPortFollower.apply(super.determineTargetUrl(request, response, authentication),
                issuerBase, request);
    }
}
