package com.example.questionnaire.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcSettingsTest {

    @Test
    void deployedConfigurationNeedsIssuerAndClient() {
        assertThatThrownBy(() -> new OidcSettings("", null, "questionnaire", "s").validate())
                .hasMessageContaining("OIDC_ISSUER");
        assertThatThrownBy(() -> new OidcSettings("https://auth.hitorro.com", null, "questionnaire", "").validate())
                .hasMessageContaining("OIDC_CLIENT_SECRET");
    }

    @Test
    void backchannelDefaultsToTheIssuer() {
        OidcSettings s = new OidcSettings("https://auth.hitorro.com/", null, "q", "s");
        assertThat(s.issuerBase()).isEqualTo("https://auth.hitorro.com");
        assertThat(s.backchannelBase()).isEqualTo("https://auth.hitorro.com");
        assertThat(new OidcSettings("https://auth.hitorro.com", "https://auth.hitorro.com:8443", "q", "s").backchannelBase())
                .isEqualTo("https://auth.hitorro.com:8443");
    }
}
