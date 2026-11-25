package com.example.questionnaire.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Campaign webhooks (app.webhooks.*, or WEBHOOK_ALLOWED_HOSTS / WEBHOOK_SECRET). Webhooks are off
 * unless both an allowlist and a secret are set: the allowlist is what keeps a creator from
 * pointing the server at arbitrary internal addresses, and the secret signs every delivery.
 */
@Component
@ConfigurationProperties(prefix = "app.webhooks")
public class WebhookProperties {

    /**
     * Hosts a webhook URL may name, comma-separated: an exact host ({@code faas.lab}) or a leading-dot
     * suffix ({@code .hitorro.com}, which matches subdomains only). Empty: webhooks are off.
     */
    private List<String> allowedHosts = List.of();
    /** HMAC-SHA256 key for X-Questionnaire-Signature. Empty: webhooks are off. */
    private String secret = "";
    /** Tries per delivery before it is marked failed. */
    private int maxAttempts = 5;
    /** Connect and request timeout, milliseconds. */
    private int timeoutMillis = 10000;

    public List<String> getAllowedHosts() { return allowedHosts; }

    public void setAllowedHosts(List<String> hosts) {
        this.allowedHosts = hosts == null ? List.of() : hosts.stream()
                .flatMap(h -> Arrays.stream(h.split(",")))
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .filter(h -> !h.isEmpty() && !h.equals("."))
                .distinct().toList();
    }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret == null ? "" : secret.trim(); }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = Math.max(1, maxAttempts); }
    public int getTimeoutMillis() { return timeoutMillis; }
    public void setTimeoutMillis(int timeoutMillis) { this.timeoutMillis = timeoutMillis; }

    public boolean enabled() {
        return !allowedHosts.isEmpty() && !secret.isEmpty();
    }

    /** Whether host is on the allowlist (exact, or a subdomain of a leading-dot entry). */
    public boolean hostAllowed(String host) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        for (String a : allowedHosts) {
            if (a.startsWith(".") ? h.endsWith(a) && h.length() > a.length() : h.equals(a)) {
                return true;
            }
        }
        return false;
    }
}
