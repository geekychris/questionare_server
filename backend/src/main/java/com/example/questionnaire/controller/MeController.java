package com.example.questionnaire.controller;

import com.example.questionnaire.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class MeController {

    /** The signed-in user: {@code {username, roles}} (roles lower-case: admin, creator, respondent). */
    @GetMapping("/api/me")
    public Map<String, Object> me(Authentication auth) {
        CurrentUser u = CurrentUser.from(auth);
        return Map.of("username", u.name(), "roles", u.roles());
    }

    /** CSRF token for JSON API clients: send {@code token} in the header named {@code headerName}. */
    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
}
