package com.example.questionnaire.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RoleMapperTest {

    private static Set<String> names(Object claim) {
        return RoleMapper.map(claim).stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    @Test
    void mapsKnownRolesToAuthorities() {
        assertThat(names(List.of("admin"))).containsExactly("ROLE_ADMIN");
        assertThat(names(List.of("creator", "respondent"))).containsExactlyInAnyOrder("ROLE_CREATOR", "ROLE_RESPONDENT");
    }

    @Test
    void isCaseInsensitiveAndIgnoresUnknownRoles() {
        assertThat(names(List.of("Admin", "labeler", "superuser"))).containsExactly("ROLE_ADMIN");
    }

    @Test
    void noOrUnknownRolesMapToNothing() {
        assertThat(names(null)).isEmpty();
        assertThat(names(List.of())).isEmpty();
        assertThat(names(List.of("labeler"))).isEmpty();
        assertThat(names(42)).isEmpty();
    }

    @Test
    void acceptsSpaceOrCommaSeparatedString() {
        assertThat(names("creator, respondent")).containsExactlyInAnyOrder("ROLE_CREATOR", "ROLE_RESPONDENT");
    }
}
