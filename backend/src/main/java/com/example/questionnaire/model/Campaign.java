package com.example.questionnaire.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "campaigns")
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;
    
    private String description;
    
    @Column(name = "start_date")
    private LocalDateTime startDate;
    
    @Column(name = "end_date")
    private LocalDateTime endDate;
    
    private boolean active;

    /**
     * Unguessable share token (128 random bits, base64url) for the respondent link
     * {@code /r/<shareToken>}. Rotating it invalidates the old link. See docs/RESPONDENTS.md.
     */
    @Column(name = "share_token", unique = true, length = 64)
    private String shareToken;

    /** When true, {@code /r/<token>} requires an OIDC login (any questionnaire role). */
    @Column(name = "require_login", nullable = false)
    @ColumnDefault("false")
    private boolean requireLogin;

    /** OIDC subject of the creator; null for legacy/seeded campaigns (admin-only). */
    @Column(name = "owner_sub", length = 128)
    private String ownerSub;

    /** Display name (preferred_username) of the creator. */
    @Column(name = "owner_name", length = 128)
    private String ownerName;

    /**
     * Optional "on response" webhook (README "Webhooks"): where to POST signed event JSON.
     * Must match the site's WEBHOOK_ALLOWED_HOSTS; null = none.
     */
    @Column(name = "webhook_url", length = 512)
    private String webhookUrl;

    /** Comma-separated events the webhook gets; null = response.created only. */
    @Column(name = "webhook_events", length = 128)
    private String webhookEvents;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<Question> questions = new ArrayList<>();

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getShareToken() {
        return shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    public boolean isRequireLogin() {
        return requireLogin;
    }

    public void setRequireLogin(boolean requireLogin) {
        this.requireLogin = requireLogin;
    }

    public String getOwnerSub() {
        return ownerSub;
    }

    public void setOwnerSub(String ownerSub) {
        this.ownerSub = ownerSub;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookEvents() {
        return webhookEvents;
    }

    public void setWebhookEvents(String webhookEvents) {
        this.webhookEvents = webhookEvents;
    }

    /** Whether the share link currently accepts responses. */
    public boolean isAcceptingResponses(LocalDateTime now) {
        if (!active) {
            return false;
        }
        if (startDate != null && now.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !now.isAfter(endDate);
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }
    
    // Helper methods
    public void addQuestion(Question question) {
        questions.add(question);
        question.setCampaign(this);
    }
    
    public void removeQuestion(Question question) {
        questions.remove(question);
        question.setCampaign(null);
    }
}

