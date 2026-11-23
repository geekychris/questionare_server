package com.example.questionnaire.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PortFollowingTest {

    private static final String ISSUER = "https://auth.hitorro.com";

    private static MockHttpServletRequest request(String scheme, String host, int port) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/oauth2/authorization/cloudstack");
        r.setServletPath("/oauth2/authorization/cloudstack");
        r.setScheme(scheme);
        r.setServerName(host);
        r.setServerPort(port);
        r.setSecure("https".equals(scheme));
        return r;
    }

    @Test
    void appliesExplicitNon443PortToProviderUrls() {
        MockHttpServletRequest r = request("https", "survey.hitorro.com", 8443);
        assertThat(RequestPortFollower.apply(ISSUER + "/oidc/authorize?x=a%3Ab", ISSUER, r))
                .isEqualTo("https://auth.hitorro.com:8443/oidc/authorize?x=a%3Ab");
    }

    @Test
    void leavesUrlsAloneOnDefaultPortPlainHttpOrForeignHost() {
        assertThat(RequestPortFollower.apply(ISSUER + "/oidc/authorize", ISSUER, request("https", "survey.hitorro.com", 443)))
                .isEqualTo(ISSUER + "/oidc/authorize");
        assertThat(RequestPortFollower.apply(ISSUER + "/oidc/authorize", ISSUER, request("http", "localhost", 8080)))
                .isEqualTo(ISSUER + "/oidc/authorize");
        assertThat(RequestPortFollower.apply("https://survey.hitorro.com/", ISSUER, request("https", "survey.hitorro.com", 8443)))
                .isEqualTo("https://survey.hitorro.com/");
        assertThat(RequestPortFollower.apply("https://auth.hitorro.com:9443/x", ISSUER, request("https", "survey.hitorro.com", 8443)))
                .isEqualTo("https://auth.hitorro.com:9443/x");
    }

    @Test
    void resolverAddsPkceAndFollowsThePort() {
        ClientRegistration reg = ClientRegistration.withRegistrationId("cloudstack")
                .clientId("questionnaire").clientSecret("s")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri(ISSUER + "/oidc/authorize")
                .tokenUri("https://auth.hitorro.com:8443/oidc/token")
                .jwkSetUri("https://auth.hitorro.com:8443/oidc/keys")
                .build();
        var resolver = new PortFollowingAuthorizationRequestResolver(new InMemoryClientRegistrationRepository(reg), ISSUER);

        OAuth2AuthorizationRequest lan = resolver.resolve(request("https", "survey.hitorro.com", 8443));
        assertThat(lan.getAuthorizationUri()).isEqualTo("https://auth.hitorro.com:8443/oidc/authorize");
        assertThat(lan.getAuthorizationRequestUri())
                .startsWith("https://auth.hitorro.com:8443/oidc/authorize?")
                .contains("code_challenge_method=S256")
                .contains("code_challenge=")
                .contains("redirect_uri=https://survey.hitorro.com:8443/login/oauth2/code/cloudstack");
        assertThat(lan.getRedirectUri()).isEqualTo("https://survey.hitorro.com:8443/login/oauth2/code/cloudstack");

        OAuth2AuthorizationRequest wan = resolver.resolve(request("https", "survey.hitorro.com", 443));
        assertThat(wan.getAuthorizationRequestUri())
                .startsWith("https://auth.hitorro.com/oidc/authorize?")
                .contains("code_challenge_method=S256")
                .contains("redirect_uri=https://survey.hitorro.com/login/oauth2/code/cloudstack");
    }
}
