package com.example.questionnaire.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.List;
import java.util.Locale;

/**
 * The signed-in user as the app sees it.
 *
 * @param id    stable id: the OIDC {@code sub} (e.g. pau_...) or, in the dev profile, the username
 * @param name  display name ({@code preferred_username})
 * @param roles lower-case app roles (admin, creator, respondent)
 */
public record CurrentUser(String id, String name, List<String> roles) {

    public boolean isAdmin() {
        return roles.contains("admin");
    }

    public boolean isCreator() {
        return roles.contains("creator");
    }

    public boolean canManageCampaigns() {
        return isAdmin() || isCreator();
    }

    public static CurrentUser from(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5).toLowerCase(Locale.ROOT))
                .filter(r -> RoleMapper.KNOWN.contains(r.toUpperCase(Locale.ROOT)))
                .distinct()
                .toList();
        if (auth.getPrincipal() instanceof OidcUser oidc) {
            String name = oidc.getPreferredUsername() != null ? oidc.getPreferredUsername() : oidc.getSubject();
            return new CurrentUser(oidc.getSubject(), name, roles);
        }
        return new CurrentUser(auth.getName(), auth.getName(), roles);
    }

    public static CurrentUser current() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }
}
