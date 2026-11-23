package com.example.questionnaire.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Set;

/**
 * Loads the OIDC user (claims come from the ID token; no userinfo call is needed) and replaces
 * its authorities with the app roles from the {@code roles} claim. Refuses a login that carries
 * no known role.
 */
public class CloudstackOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    public static final String ACCESS_DENIED = "access_denied";

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    public CloudstackOidcUserService() {
        this(new OidcUserService());
    }

    public CloudstackOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.delegate = delegate;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser user = delegate.loadUser(userRequest);
        Object roles = user.getClaims().get("roles");
        if (roles == null) {
            roles = user.getClaims().get("groups");
        }
        Set<GrantedAuthority> authorities = RoleMapper.map(roles);
        if (authorities.isEmpty()) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ACCESS_DENIED,
                    "You have no role in the questionnaire app", null));
        }
        String nameAttr = user.getClaims().containsKey("preferred_username") ? "preferred_username" : "sub";
        return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), nameAttr);
    }
}
