package com.example.questionnaire.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

/** Share tokens: 128 random bits, base64url without padding (22 characters). */
public final class ShareTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SHAPE = Pattern.compile("[A-Za-z0-9_-]{22}");

    private ShareTokens() {
    }

    public static String generate() {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Cheap shape check before touching the database. */
    public static boolean looksValid(String token) {
        return token != null && SHAPE.matcher(token).matches();
    }
}
