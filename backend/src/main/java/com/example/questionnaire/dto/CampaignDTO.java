package com.example.questionnaire.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CampaignDTO {
    private Long id;
    private String title;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean active;
    private boolean requireLogin;
    /** Read-only: managed by the share endpoints (rotate/close/open). */
    private String shareToken;
    /** Read-only: the respondent link, absolute when APP_PUBLIC_URL is set, else {@code /r/<token>}. */
    private String shareUrl;
    /** Read-only: whether the share link currently accepts responses. */
    private boolean acceptingResponses;
    /** Read-only: creator's username. */
    private String ownerName;
    /** Read-only: number of submissions. */
    private long responseCount;
    private List<QuestionDTO> questions = new ArrayList<>();

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

    public boolean isRequireLogin() {
        return requireLogin;
    }

    public void setRequireLogin(boolean requireLogin) {
        this.requireLogin = requireLogin;
    }

    public String getShareToken() {
        return shareToken;
    }

    public void setShareToken(String shareToken) {
        this.shareToken = shareToken;
    }

    public String getShareUrl() {
        return shareUrl;
    }

    public void setShareUrl(String shareUrl) {
        this.shareUrl = shareUrl;
    }

    public boolean isAcceptingResponses() {
        return acceptingResponses;
    }

    public void setAcceptingResponses(boolean acceptingResponses) {
        this.acceptingResponses = acceptingResponses;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public long getResponseCount() {
        return responseCount;
    }

    public void setResponseCount(long responseCount) {
        this.responseCount = responseCount;
    }

    public List<QuestionDTO> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionDTO> questions) {
        this.questions = questions;
    }
}

