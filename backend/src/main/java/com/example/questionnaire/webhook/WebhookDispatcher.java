package com.example.questionnaire.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends queued webhook deliveries: every couple of seconds it takes the due ones, POSTs each with
 * its signature, and records the outcome. A 2xx is success; anything else (or no answer) is retried
 * with backoff up to {@code maxAttempts}, then marked failed. Redirects are not followed, so a
 * delivery cannot be bounced past the host allowlist. Assumes one replica (the deployments run one);
 * two would only double-send, since the receiver can dedupe on the delivery id.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookDeliveryRepository deliveries;
    private final WebhookService service;
    private final WebhookProperties props;
    private final HttpClient http;

    public WebhookDispatcher(WebhookDeliveryRepository deliveries, WebhookService service, WebhookProperties props) {
        this.deliveries = deliveries;
        this.service = service;
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Scheduled(fixedDelayString = "${app.webhooks.poll-millis:2000}", initialDelay = 2000)
    public void deliverDue() {
        if (!props.enabled()) {
            return;
        }
        List<WebhookDelivery> due = deliveries.findDue(WebhookDelivery.Status.PENDING, LocalDateTime.now(), PageRequest.of(0, 20));
        for (WebhookDelivery d : due) {
            attempt(d);
        }
    }

    /** One try at one delivery; saves the outcome. */
    void attempt(WebhookDelivery d) {
        Integer code = null;
        String error = null;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(d.getUrl()))
                    .timeout(Duration.ofMillis(props.getTimeoutMillis()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "questionnaire-webhooks/1")
                    .header("X-Questionnaire-Event", d.getEvent())
                    .header("X-Questionnaire-Delivery", d.getId())
                    .header("X-Questionnaire-Signature", service.sign(d.getPayload()))
                    // Retries of one delivery reuse it, so a receiver such as a FaaS route runs it once.
                    .header("Idempotency-Key", d.getId())
                    .POST(HttpRequest.BodyPublishers.ofString(d.getPayload(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            code = resp.statusCode();
            if (code < 200 || code >= 300) {
                String body = resp.body() == null ? "" : resp.body().strip();
                error = "HTTP " + code + (body.isEmpty() ? "" : ": " + body);
            }
        } catch (IOException | IllegalArgumentException e) {
            error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        d.setAttempts(d.getAttempts() + 1);
        d.setLastStatusCode(code);
        d.setUpdatedAt(now);
        if (error == null) {
            d.setStatus(WebhookDelivery.Status.SUCCEEDED);
            d.setLastError(null);
            d.setNextAttemptAt(null);
        } else {
            d.setLastError(error.length() > 500 ? error.substring(0, 500) + "…" : error);
            if (d.getAttempts() >= props.getMaxAttempts()) {
                d.setStatus(WebhookDelivery.Status.FAILED);
                d.setNextAttemptAt(null);
                log.warn("webhook: delivery {} ({} for campaign {}) failed after {} tries: {}",
                        d.getId(), d.getEvent(), d.getCampaignId(), d.getAttempts(), d.getLastError());
            } else {
                d.setNextAttemptAt(now.plus(WebhookService.backoff(d.getAttempts())));
            }
        }
        deliveries.save(d);
    }
}
