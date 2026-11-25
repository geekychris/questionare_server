package com.example.questionnaire.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiTokensTest {

    static final String T1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String T2 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void emptyIsOff() {
        assertThat(new ApiTokens("").enabled()).isFalse();
        assertThat(new ApiTokens(null).match(T1)).isEmpty();
    }

    @Test
    void matchesByLabel() {
        ApiTokens t = new ApiTokens("faas=" + T1 + ", other=" + T2);
        assertThat(t.match(T1)).contains("faas");
        assertThat(t.match(T2)).contains("other");
        assertThat(t.match(T1 + "x")).isEmpty();
        assertThat(t.match("")).isEmpty();
        assertThat(new ApiTokens(T1).match(T1)).contains("api");
    }

    @Test
    void refusesShortTokensAndBadLabels() {
        assertThatThrownBy(() -> new ApiTokens("faas=short")).hasMessageContaining("shorter");
        assertThatThrownBy(() -> new ApiTokens("bad label=" + T1)).hasMessageContaining("label");
    }
}
