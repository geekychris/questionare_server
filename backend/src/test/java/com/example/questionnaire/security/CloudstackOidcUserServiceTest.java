package com.example.questionnaire.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudstackOidcUserServiceTest {

    private static OidcUser userWith(Map<String, Object> extraClaims) {
        OidcIdToken.Builder b = OidcIdToken.withTokenValue("t")
                .subject("pau_123").issuer("https://auth.example.test")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("preferred_username", "alice");
        extraClaims.forEach(b::claim);
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), b.build(), "sub");
    }

    private static OidcUser load(Map<String, Object> claims) {
        OidcUser fromDelegate = userWith(claims);
        return new CloudstackOidcUserService(req -> fromDelegate).loadUser((OidcUserRequest) null);
    }

    @Test
    void replacesAuthoritiesWithMappedRolesAndUsesPreferredUsername() {
        OidcUser u = load(Map.of("roles", List.of("creator")));
        assertThat(u.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_CREATOR");
        assertThat(u.getName()).isEqualTo("alice");
        CurrentUser cu = CurrentUser.from(new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(
                u, u.getAuthorities(), "cloudstack"));
        assertThat(cu.id()).isEqualTo("pau_123");
        assertThat(cu.name()).isEqualTo("alice");
        assertThat(cu.roles()).containsExactly("creator");
    }

    @Test
    void fallsBackToGroupsClaim() {
        assertThat(load(Map.of("groups", List.of("admin"))).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void refusesLoginWithoutAKnownRole() {
        assertThatThrownBy(() -> load(Map.of("roles", List.of("labeler"))))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo("access_denied"));
        assertThatThrownBy(() -> load(Map.of())).isInstanceOf(OAuth2AuthenticationException.class);
    }
}
