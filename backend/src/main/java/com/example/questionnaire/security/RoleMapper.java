package com.example.questionnaire.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps the provider's {@code roles} claim (this app's role names) to Spring authorities.
 * <ul>
 *   <li>{@code admin} → ROLE_ADMIN: every campaign, delete, everything a creator can do</li>
 *   <li>{@code creator} → ROLE_CREATOR: create campaigns, edit/close/rotate and see results of their own</li>
 *   <li>{@code respondent} → ROLE_RESPONDENT: may only answer campaigns that require login</li>
 * </ul>
 * Unknown role names are ignored; a user with no known role is refused at login.
 */
public final class RoleMapper {

    public static final String ADMIN = "ADMIN";
    public static final String CREATOR = "CREATOR";
    public static final String RESPONDENT = "RESPONDENT";
    public static final List<String> KNOWN = List.of(ADMIN, CREATOR, RESPONDENT);

    private RoleMapper() {
    }

    public static Set<GrantedAuthority> map(Object rolesClaim) {
        Set<GrantedAuthority> out = new LinkedHashSet<>();
        if (rolesClaim instanceof Collection<?> c) {
            for (Object o : c) {
                add(out, o);
            }
        } else if (rolesClaim instanceof String s) {
            for (String part : s.split("[,\\s]+")) {
                add(out, part);
            }
        }
        return out;
    }

    private static void add(Set<GrantedAuthority> out, Object o) {
        if (o == null) {
            return;
        }
        String r = o.toString().trim().toUpperCase(Locale.ROOT);
        if (KNOWN.contains(r)) {
            out.add(new SimpleGrantedAuthority("ROLE_" + r));
        }
    }
}
