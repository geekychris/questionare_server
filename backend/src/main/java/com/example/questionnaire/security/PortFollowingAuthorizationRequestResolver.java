package com.example.questionnaire.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Spring's default resolver with PKCE (S256, required by the provider for every client) plus the
 * follow-the-request-port rule applied to the authorization URI.
 */
public class PortFollowingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final String issuerBase;

    public PortFollowingAuthorizationRequestResolver(ClientRegistrationRepository repo, String issuerBase) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repo,
                OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        this.delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        this.issuerBase = issuerBase;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return adjust(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return adjust(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest adjust(OAuth2AuthorizationRequest req, HttpServletRequest request) {
        if (req == null || RequestPortFollower.publicPort(request) < 0) {
            return req;
        }
        return OAuth2AuthorizationRequest.from(req)
                .authorizationUri(RequestPortFollower.apply(req.getAuthorizationUri(), issuerBase, request))
                .authorizationRequestUri(RequestPortFollower.apply(req.getAuthorizationRequestUri(), issuerBase, request))
                .build();
    }
}
