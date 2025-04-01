package com.example.questionnaire.dto;

import java.util.ArrayList;
import java.util.List;

public class QuestionDTO {
    private Long id;
    private String text;
    private String type;
    private boolean required;
    private int orderIndex;
    private Long campaignId;
    private List<AnswerOptionDTO> options = new ArrayList<>();

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public List<AnswerOptionDTO> getOptions() {
        return options;
    }

    public void setOptions(List<AnswerOptionDTO> options) {
        this.options = options;
    }
}

