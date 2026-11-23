package com.example.questionnaire.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Settings for signing in against the site's cloud-stack OpenID Connect provider.
 * Bound from {@code app.oidc.*}, which application.yml maps to the {@code OIDC_*}
 * environment variables. See README "Authentication".
 *
 * @param issuer           OIDC_ISSUER, e.g. https://auth.hitorro.com (browser-facing; also the expected {@code iss})
 * @param backchannelUrl   OIDC_BACKCHANNEL_URL, base for token/JWKS/userinfo calls; defaults to the issuer
 * @param clientId         OIDC_CLIENT_ID
 * @param clientSecret     OIDC_CLIENT_SECRET
 */
@ConfigurationProperties(prefix = "app.oidc")
public record OidcSettings(String issuer, String backchannelUrl, String clientId, String clientSecret) {

    public static final String REGISTRATION_ID = "cloudstack";

    public String issuerBase() {
        return trimSlash(issuer);
    }

    public String backchannelBase() {
        return StringUtils.hasText(backchannelUrl) ? trimSlash(backchannelUrl) : issuerBase();
    }

    /** Fails fast (the app must not start without a provider outside the dev profile). */
    public void validate() {
        if (!StringUtils.hasText(issuer)) {
            throw new IllegalStateException("OIDC_ISSUER is required (or run with the 'dev' profile for local sign-in)");
        }
        if (!StringUtils.hasText(clientId)) {
            throw new IllegalStateException("OIDC_CLIENT_ID is required");
        }
        if (!StringUtils.hasText(clientSecret)) {
            throw new IllegalStateException("OIDC_CLIENT_SECRET is required");
        }
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}
