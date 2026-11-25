package com.example.questionnaire.respondent;

import com.example.questionnaire.model.Campaign;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.webhook.WebhookService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The whole respondent surface, all under {@code /r/} (the proxy serves only {@code /r/} without
 * sign-in). Nothing here reveals other campaigns, results or admin data.
 * <pre>
 * GET  /r/{token}             the survey page (HTML)
 * GET  /r/{token}/form        the questions (JSON)
 * POST /r/{token}/responses   submit answers (JSON; needs header X-Respondent: 1)
 * GET  /r/assets/{file}       the page's script and stylesheet
 * </pre>
 * Unknown token → 404, closed campaign → 410. A {@code requireLogin} campaign needs a signed-in
 * user with any questionnaire role (the page redirects to the login, the JSON calls answer 401).
 */
@RestController
public class RespondentController {

    static final int MAX_BODY = 64 * 1024;
    static final String DONE_COOKIE = "qr_done";
    private static final Set<String> ASSETS = Set.of("respond.js", "respond.css");
    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "connect-src 'self'; img-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";

    private final RespondentService service;
    private final ObjectMapper json;
    private final WebhookService webhooks;

    public RespondentController(RespondentService service, ObjectMapper json, WebhookService webhooks) {
        this.service = service;
        this.json = json;
        this.webhooks = webhooks;
    }

    @GetMapping(value = "/r/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String token, Authentication auth) {
        Campaign c;
        try {
            c = service.open(token);
        } catch (ResponseStatusException e) {
            return html(HttpStatus.valueOf(e.getStatusCode().value()), errorPage(e));
        }
        requireLoginIfNeeded(c, auth);
        String body = "<!doctype html><html lang=en><head><meta charset=utf-8>"
                + "<meta name=viewport content=\"width=device-width, initial-scale=1\">"
                + "<meta name=referrer content=no-referrer>"
                + "<title>" + HtmlUtils.htmlEscape(c.getTitle()) + "</title>"
                + "<link rel=stylesheet href=\"/r/assets/respond.css\"></head><body>"
                + "<main id=app data-token=\"" + HtmlUtils.htmlEscape(token) + "\">"
                + "<h1>" + HtmlUtils.htmlEscape(c.getTitle()) + "</h1><p>Loading…</p></main>"
                + "<noscript>This survey needs JavaScript.</noscript>"
                + "<script src=\"/r/assets/respond.js\"></script></body></html>";
        return html(HttpStatus.OK, body);
    }

    @GetMapping(value = "/r/{token}/form", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RespondentForm> form(@PathVariable String token, Authentication auth) {
        requireLoginIfNeeded(service.open(token), auth);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.form(token));
    }

    @PostMapping("/r/{token}/responses")
    public ResponseEntity<Map<String, String>> submit(@PathVariable String token, Authentication auth,
                                                      HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // CSRF: this endpoint is exempt from the token check; a cross-site page can send neither a
        // JSON content type nor a custom header without a CORS preflight (and no CORS is allowed).
        if (!"1".equals(request.getHeader("X-Respondent"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "missing X-Respondent header");
        }
        MediaType type;
        try {
            type = MediaType.parseMediaType(String.valueOf(request.getContentType()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "application/json required");
        }
        if (!MediaType.APPLICATION_JSON.includes(type)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "application/json required");
        }
        if (request.getContentLengthLong() > MAX_BODY) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "body too large");
        }
        Campaign c = service.open(token);
        requireLoginIfNeeded(c, auth);

        byte[] raw;
        try (InputStream in = request.getInputStream()) {
            raw = in.readNBytes(MAX_BODY + 1);
        }
        if (raw.length > MAX_BODY) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "body too large");
        }
        Submission submission;
        try {
            submission = json.readValue(raw, Submission.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed JSON");
        }

        String respondentId;
        if (c.isRequireLogin()) {
            // One response per signed-in user.
            respondentId = "user:" + CurrentUser.from(auth).id();
        } else {
            // Anonymous: nothing about the browser or user is stored; a cookie scoped to this link
            // only discourages accidental double submissions.
            if (hasDoneCookie(request)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "already answered from this browser");
            }
            respondentId = "anon:" + UUID.randomUUID();
        }
        service.submit(token, submission, respondentId);
        // Committed: queue the campaign's webhook (if any). Sending happens on another thread, and
        // a failure here is logged, never shown to the respondent.
        Map<String, Object> respondent = c.isRequireLogin() ? respondentInfo(auth) : null;
        WebhookService.safely("response for campaign " + c.getId(),
                () -> webhooks.responseRecorded(c.getId(), respondentId, respondent));
        if (!c.isRequireLogin()) {
            Cookie done = new Cookie(DONE_COOKIE, "1");
            done.setPath("/r/" + token);
            done.setHttpOnly(true);
            done.setSecure(request.isSecure());
            done.setMaxAge((int) Duration.ofDays(365).toSeconds());
            done.setAttribute("SameSite", "Strict");
            response.addCookie(done);
        }
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(Map.of("status", "recorded"));
    }

    @GetMapping("/r/assets/{file}")
    public ResponseEntity<byte[]> asset(@PathVariable String file) throws IOException {
        if (!ASSETS.contains(file)) {
            return ResponseEntity.notFound().build();
        }
        byte[] body;
        try (InputStream in = new ClassPathResource("respondent/" + file).getInputStream()) {
            body = in.readAllBytes();
        }
        MediaType type = file.endsWith(".js") ? MediaType.valueOf("text/javascript;charset=UTF-8")
                : MediaType.valueOf("text/css;charset=UTF-8");
        return ResponseEntity.ok().contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(body);
    }

    /** Who answered a requireLogin campaign, for its webhook: {id, name, email}. */
    private static Map<String, Object> respondentInfo(Authentication auth) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        CurrentUser u = CurrentUser.from(auth);
        m.put("name", u == null ? null : u.name());
        m.put("email", auth != null && auth.getPrincipal() instanceof OidcUser o ? o.getEmail() : null);
        return m;
    }

    /** Anonymous access to a requireLogin campaign starts the login (page) or answers 401 (JSON). */
    private static void requireLoginIfNeeded(Campaign c, Authentication auth) {
        if (c.isRequireLogin() && CurrentUser.from(auth) == null) {
            throw new InsufficientAuthenticationException("This survey requires sign-in");
        }
        if (c.isRequireLogin() && CurrentUser.from(auth).roles().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "no questionnaire role");
        }
    }

    private static boolean hasDoneCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (Cookie k : request.getCookies()) {
            if (DONE_COOKIE.equals(k.getName())) {
                return true;
            }
        }
        return false;
    }

    private static String errorPage(ResponseStatusException e) {
        String msg = e.getStatusCode().value() == 410 ? "This survey is closed and no longer accepts answers."
                : "This survey link is not valid.";
        return "<!doctype html><html lang=en><head><meta charset=utf-8>"
                + "<meta name=viewport content=\"width=device-width, initial-scale=1\">"
                + "<title>Survey</title><link rel=stylesheet href=\"/r/assets/respond.css\"></head>"
                + "<body><main><h1>Survey unavailable</h1><p>" + msg + "</p></main></body></html>";
    }

    private static ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", CSP)
                .body(body);
    }
}
