package com.example.questionnaire.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Machine access to {@code /api/**} (README "API tokens"): {@code API_TOKENS} holds
 * {@code label=token} pairs separated by commas. A request with {@code Authorization: Bearer <token>}
 * acts as the service principal {@code api:<label>} with the creator role.
 * <p>
 * Only SHA-256 hashes are kept in memory; a presented token is hashed and compared with every
 * configured hash in constant time. Tokens shorter than {@link #MIN_LENGTH} are refused at startup.
 */
@Component
public class ApiTokens {

    private static final Logger log = LoggerFactory.getLogger(ApiTokens.class);

    public static final int MIN_LENGTH = 24;
    private static final Pattern LABEL = Pattern.compile("[A-Za-z0-9._-]{1,40}");

    private record Entry(String label, byte[] hash) { }

    private final List<Entry> entries;

    public ApiTokens(@Value("${app.api-tokens:}") String spec) {
        this.entries = parse(spec);
        if (!entries.isEmpty()) {
            log.info("API tokens enabled for: {}", entries.stream().map(Entry::label).toList());
        }
    }

    static List<Entry> parse(String spec) {
        List<Entry> out = new ArrayList<>();
        if (spec == null || spec.isBlank()) return out;
        for (String part : spec.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            int eq = p.indexOf('=');
            String label = eq > 0 ? p.substring(0, eq).trim() : "api";
            String token = eq > 0 ? p.substring(eq + 1).trim() : p;
            if (!LABEL.matcher(label).matches()) {
                throw new IllegalStateException("API_TOKENS: bad label (letters, digits, . _ - only)");
            }
            if (token.length() < MIN_LENGTH) {
                throw new IllegalStateException("API_TOKENS: the token for '" + label + "' is shorter than " + MIN_LENGTH + " characters");
            }
            out.add(new Entry(label, sha256(token)));
        }
        return out;
    }

    public boolean enabled() {
        return !entries.isEmpty();
    }

    /** The label of the matching token, or empty. Every configured hash is compared (no early exit). */
    public Optional<String> match(String token) {
        if (token == null || token.isEmpty() || entries.isEmpty()) return Optional.empty();
        byte[] h = sha256(token);
        String found = null;
        for (Entry e : entries) {
            if (MessageDigest.isEqual(h, e.hash()) && found == null) {
                found = e.label();
            }
        }
        return Optional.ofNullable(found);
    }

    static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
