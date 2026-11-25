package com.example.questionnaire.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates {@code /api/**} requests that carry a valid {@code Authorization: Bearer} token
 * ({@link ApiTokens}). The authentication lives only for the request: nothing is stored in a
 * session. A missing or unknown token leaves the request alone, so it is handled exactly as before
 * (session login, else 401). Requests authenticated here are marked with {@link #ATTRIBUTE}, which
 * the CSRF filter uses to skip them; a bearer header alone does not.
 */
public class ApiTokenFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = ApiTokenFilter.class.getName() + ".authenticated";
    public static final String PREFIX = "api:";

    private final ApiTokens tokens;

    public ApiTokenFilter(ApiTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !tokens.enabled() || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            tokens.match(header.substring(7).trim()).ifPresent(label -> {
                var auth = UsernamePasswordAuthenticationToken.authenticated(
                        PREFIX + label, null, List.of(new SimpleGrantedAuthority("ROLE_CREATOR")));
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(auth);
                SecurityContextHolder.setContext(ctx);
                request.setAttribute(ATTRIBUTE, Boolean.TRUE);
            });
        }
        chain.doFilter(request, response);
    }
}
