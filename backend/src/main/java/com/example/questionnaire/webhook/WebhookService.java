package com.example.questionnaire.webhook;

import com.example.questionnaire.model.Campaign;
import com.example.questionnaire.model.Question;
import com.example.questionnaire.model.UserResponse;
import com.example.questionnaire.repository.CampaignRepository;
import com.example.questionnaire.repository.UserResponseRepository;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.service.CampaignService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Campaign webhooks: validates a campaign's webhook settings and queues signed event deliveries.
 * Sending happens later on {@link WebhookDispatcher}'s thread, so a respondent is never slowed
 * or failed by a webhook (README "Webhooks").
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public static final String RESPONSE_CREATED = "response.created";
    public static final String CAMPAIGN_CLOSED = "campaign.closed";
    public static final String PING = "ping";
    /** Events a campaign can subscribe to (ping is sent only by "Send test"). */
    public static final List<String> EVENTS = List.of(RESPONSE_CREATED, CAMPAIGN_CLOSED);

    private final WebhookProperties props;
    private final WebhookDeliveryRepository deliveries;
    private final CampaignRepository campaigns;
    private final UserResponseRepository responses;
    private final CampaignService campaignService;
    private final ObjectMapper json;
    private final ObjectProvider<WebhookService> self;

    public WebhookService(WebhookProperties props, WebhookDeliveryRepository deliveries, CampaignRepository campaigns,
                          UserResponseRepository responses, CampaignService campaignService, ObjectMapper json,
                          ObjectProvider<WebhookService> self) {
        this.self = self;
        this.props = props;
        this.deliveries = deliveries;
        this.campaigns = campaigns;
        this.responses = responses;
        this.campaignService = campaignService;
        this.json = json;
    }

    /** This bean through its proxy, so @Transactional applies to calls from inside it. */
    private WebhookService self() {
        return self.getObject();
    }

    public boolean enabled() {
        return props.enabled();
    }

    public List<String> allowedHosts() {
        return props.getAllowedHosts();
    }

    /** A webhook URL, normalised, or 400: http(s), no user info, host on the allowlist. */
    public String validateUrl(String url) {
        return validateUrl(props, url);
    }

    public static String validateUrl(WebhookProperties props, String url) {
        String u = url == null ? "" : url.trim();
        if (!props.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Webhooks are off on this site (WEBHOOK_ALLOWED_HOSTS and WEBHOOK_SECRET are not both set)");
        }
        if (u.length() > 512) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "webhook URL is too long (max 512)");
        }
        URI uri;
        try {
            uri = new URI(u);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "webhook URL is not a valid URL");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "webhook URL must be http or https");
        }
        if (uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "webhook URL may not contain user info or a fragment");
        }
        if (!props.hostAllowed(uri.getHost())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "webhook host " + uri.getHost() + " is not allowed (allowed: " + String.join(", ", props.getAllowedHosts()) + ")");
        }
        return uri.toString();
    }

    /** Events, validated; null or empty means response.created. */
    public static String eventsColumn(List<String> events) {
        if (events == null || events.isEmpty()) {
            return RESPONSE_CREATED;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String e : events) {
            String v = e == null ? "" : e.trim();
            if (!EVENTS.contains(v)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown webhook event " + v + " (known: " + String.join(", ", EVENTS) + ")");
            }
            out.add(v);
        }
        return String.join(",", out);
    }

    public static List<String> events(Campaign c) {
        if (!StringUtils.hasText(c.getWebhookEvents())) {
            return List.of(RESPONSE_CREATED);
        }
        return List.of(c.getWebhookEvents().split(","));
    }

    /**
     * Applies webhook settings from a create or update: url null leaves both unchanged, "" removes
     * the webhook, anything else is validated.
     */
    public void apply(Campaign c, String url, List<String> events) {
        apply(props, c, url, events);
    }

    public static void apply(WebhookProperties props, Campaign c, String url, List<String> events) {
        if (url == null) {
            if (events != null && c.getWebhookUrl() != null) {
                c.setWebhookEvents(eventsColumn(events));
            }
            return;
        }
        if (url.isBlank()) {
            c.setWebhookUrl(null);
            c.setWebhookEvents(null);
            return;
        }
        c.setWebhookUrl(validateUrl(props, url));
        c.setWebhookEvents(eventsColumn(events));
    }

    /**
     * Queues response.created for one stored submission, in its own transaction. Callers catch
     * what this throws: a respondent must never see a webhook problem ({@link #safely}).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void responseRecorded(Long campaignId, String respondentId, Map<String, Object> respondent) {
        Campaign c = campaigns.findById(campaignId).orElse(null);
        if (c == null || !subscribed(c, RESPONSE_CREATED)) {
            return;
        }
        List<UserResponse> rows = new ArrayList<>(responses.findByUserIdAndQuestion_Campaign_Id(respondentId, campaignId));
        if (rows.isEmpty()) {
            return;
        }
        rows.sort(Comparator.comparingInt((UserResponse r) -> r.getQuestion().getOrderIndex()).thenComparing(UserResponse::getId));
        List<Map<String, Object>> answers = new ArrayList<>();
        for (UserResponse r : rows) {
            Question q = r.getQuestion();
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("questionId", q.getId());
            a.put("question", q.getText());
            boolean text = r.getSelectedOption() == null;
            a.put("type", text ? "text" : "choice");
            a.put("value", text ? r.getTextResponse() : r.getSelectedOption().getText());
            if (!text) {
                a.put("optionId", r.getSelectedOption().getId());
            }
            answers.add(a);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", rows.get(0).getId());
        response.put("submittedAt", rows.get(0).getSubmissionDate().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        response.put("respondent", c.isRequireLogin() ? respondent : null);
        response.put("answers", answers);
        queue(c, RESPONSE_CREATED, Map.of("response", response));
    }

    /** After a close commits (API, UI or an edit), queues campaign.closed if subscribed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCampaignClosed(CampaignService.CampaignClosed e) {
        safely("campaign.closed for campaign " + e.campaignId(), () -> self().campaignClosed(e.campaignId()));
    }

    /** Queues campaign.closed when the campaign subscribes to it, in its own transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void campaignClosed(Long campaignId) {
        Campaign c = campaigns.findById(campaignId).orElse(null);
        if (c != null && subscribed(c, CAMPAIGN_CLOSED)) {
            queue(c, CAMPAIGN_CLOSED, Map.of());
        }
    }

    /** Runs a queueing call, logging instead of throwing. */
    public static void safely(String what, Runnable call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            log.warn("webhook: could not queue {}: {}", what, e.toString());
        }
    }

    /** Sets (url non-blank) or removes (blank) a campaign's webhook, as its owner or an admin. */
    @Transactional
    public Campaign update(Long campaignId, CurrentUser user, String url, List<String> events) {
        Campaign c = campaignService.load(campaignId, user);
        apply(c, url == null ? "" : url, events);
        Campaign saved = campaigns.save(c);
        log.info("campaign {} webhook {} by {}", campaignId,
                saved.getWebhookUrl() == null ? "removed" : "set to " + saved.getWebhookUrl(), user.name());
        return saved;
    }

    /** The campaign (owner or admin), for showing its webhook. */
    @Transactional(readOnly = true)
    public Campaign campaign(Long campaignId, CurrentUser user) {
        return campaignService.load(campaignId, user);
    }

    /** "Send test": queues a ping, as the campaign's owner or an admin. */
    @Transactional
    public WebhookDelivery ping(Long campaignId, CurrentUser user) {
        return ping(campaignService.load(campaignId, user));
    }

    /** Queues a ping to the campaign's webhook (the "Send test" button); 400 when it has none. */
    @Transactional
    public WebhookDelivery ping(Campaign c) {
        if (!enabled() || !StringUtils.hasText(c.getWebhookUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "this campaign has no webhook");
        }
        return queue(c, PING, Map.of("message", "Test delivery from the questionnaire"));
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> recent(Long campaignId, int limit) {
        return deliveries.findByCampaignIdOrderByCreatedAtDesc(campaignId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    private boolean subscribed(Campaign c, String event) {
        return enabled() && StringUtils.hasText(c.getWebhookUrl()) && events(c).contains(event);
    }

    private WebhookDelivery queue(Campaign c, String event, Map<String, Object> extra) {
        WebhookDelivery d = new WebhookDelivery();
        d.setId(UUID.randomUUID().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event);
        body.put("deliveryId", d.getId());
        Map<String, Object> campaign = new LinkedHashMap<>();
        campaign.put("id", c.getId());
        campaign.put("title", c.getTitle());
        campaign.put("link", campaignService.shareUrl(c.getShareToken()));
        body.put("campaign", campaign);
        body.putAll(extra);
        try {
            d.setPayload(json.writeValueAsString(body));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        LocalDateTime now = LocalDateTime.now();
        d.setCampaignId(c.getId());
        d.setEvent(event);
        d.setUrl(c.getWebhookUrl());
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        d.setNextAttemptAt(now);
        return deliveries.save(d);
    }

    /** {@code sha256=<hex HMAC-SHA256 of body>} with the site secret. */
    public String sign(String body) {
        return signature(props.getSecret(), body);
    }

    public static String signature(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Delay before retry number {@code attempt} (1-based count of tries made so far). */
    static Duration backoff(int attempt) {
        return switch (attempt) {
            case 1 -> Duration.ofSeconds(10);
            case 2 -> Duration.ofSeconds(60);
            case 3 -> Duration.ofMinutes(5);
            default -> Duration.ofMinutes(30);
        };
    }
}
