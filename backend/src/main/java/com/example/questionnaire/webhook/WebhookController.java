package com.example.questionnaire.webhook;

import com.example.questionnaire.model.Campaign;
import com.example.questionnaire.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A campaign's webhook (owner or admin; README "Webhooks"):
 * <pre>
 * GET  /api/campaigns/{id}/webhook        {enabled, allowedHosts, url, events, knownEvents, deliveries}
 * PUT  /api/campaigns/{id}/webhook        {url, events}; url "" or null removes it
 * POST /api/campaigns/{id}/webhook/test   queue a "ping" delivery
 * </pre>
 */
@RestController
@RequestMapping("/api/campaigns/{id}/webhook")
public class WebhookController {

    private final WebhookService webhooks;

    public WebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    public record Settings(String url, List<String> events) {}

    @GetMapping
    public Map<String, Object> get(@PathVariable Long id, Authentication auth) {
        return view(webhooks.campaign(id, CurrentUser.from(auth)));
    }

    @PutMapping
    public Map<String, Object> put(@PathVariable Long id, @RequestBody Settings body, Authentication auth) {
        return view(webhooks.update(id, CurrentUser.from(auth), body.url(), body.events()));
    }

    @PostMapping("/test")
    public Map<String, Object> test(@PathVariable Long id, Authentication auth) {
        return delivery(webhooks.ping(id, CurrentUser.from(auth)));
    }

    private Map<String, Object> view(Campaign c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", webhooks.enabled());
        out.put("allowedHosts", webhooks.allowedHosts());
        out.put("url", c.getWebhookUrl());
        out.put("events", c.getWebhookUrl() == null ? List.of() : WebhookService.events(c));
        out.put("knownEvents", WebhookService.EVENTS);
        out.put("deliveries", webhooks.recent(c.getId(), 20).stream().map(WebhookController::delivery).toList());
        return out;
    }

    public static Map<String, Object> delivery(WebhookDelivery d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("event", d.getEvent());
        m.put("url", d.getUrl());
        m.put("status", d.getStatus().name());
        m.put("attempts", d.getAttempts());
        m.put("lastStatusCode", d.getLastStatusCode());
        m.put("lastError", d.getLastError());
        m.put("createdAt", d.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        m.put("updatedAt", d.getUpdatedAt() == null ? null : d.getUpdatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        m.put("nextAttemptAt", d.getNextAttemptAt() == null ? null : d.getNextAttemptAt().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        return m;
    }
}
