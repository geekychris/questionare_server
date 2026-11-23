package com.example.questionnaire.dto;

import java.util.ArrayList;
import java.util.List;

/** Aggregated answers of one campaign (admin/creator view). */
public class CampaignResultsDTO {
    private Long campaignId;
    private String title;
    private long submissions;
    private List<QuestionResult> questions = new ArrayList<>();

    public Long getCampaignId() { return campaignId; }
    public void setCampaignId(Long campaignId) { this.campaignId = campaignId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getSubmissions() { return submissions; }
    public void setSubmissions(long submissions) { this.submissions = submissions; }
    public List<QuestionResult> getQuestions() { return questions; }
    public void setQuestions(List<QuestionResult> questions) { this.questions = questions; }

    public static class QuestionResult {
        private Long questionId;
        private String text;
        private String type;
        private List<OptionCount> options = new ArrayList<>();
        private List<String> textAnswers = new ArrayList<>();

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<OptionCount> getOptions() { return options; }
        public void setOptions(List<OptionCount> options) { this.options = options; }
        public List<String> getTextAnswers() { return textAnswers; }
        public void setTextAnswers(List<String> textAnswers) { this.textAnswers = textAnswers; }
    }

    public static class OptionCount {
        private Long optionId;
        private String text;
        private long count;

        public OptionCount() {
        }

        public OptionCount(Long optionId, String text, long count) {
            this.optionId = optionId;
            this.text = text;
            this.count = count;
        }

        public Long getOptionId() { return optionId; }
        public void setOptionId(Long optionId) { this.optionId = optionId; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public long getCount() { return count; }
        public void setCount(long count) { this.count = count; }
    }
}
