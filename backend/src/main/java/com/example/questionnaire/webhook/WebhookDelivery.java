package com.example.questionnaire.webhook;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One event sent (or being sent) to a campaign's webhook, with its retry state. */
@Entity
@Table(name = "webhook_deliveries", indexes = {
        @Index(name = "idx_webhook_due", columnList = "status,next_attempt_at"),
        @Index(name = "idx_webhook_campaign", columnList = "campaign_id,created_at")})
public class WebhookDelivery {

    public enum Status { PENDING, SUCCEEDED, FAILED }

    /** UUID; sent as X-Questionnaire-Delivery and Idempotency-Key. */
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(nullable = false, length = 32)
    private String event;

    @Column(nullable = false, length = 512)
    private String url;

    /**
     * The exact body that is signed and sent. The length makes it MEDIUMTEXT on MySQL (an
     * unsized @Lob is TINYTEXT, 255 bytes); {@link WebhookSchemaFix} widens tables made before.
     */
    @Lob
    @Column(nullable = false, length = WebhookSchemaFix.PAYLOAD_LENGTH)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    private int attempts;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Integer getLastStatusCode() { return lastStatusCode; }
    public void setLastStatusCode(Integer lastStatusCode) { this.lastStatusCode = lastStatusCode; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
}
