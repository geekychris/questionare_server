package com.example.questionnaire.respondent;

import java.util.List;

/** {@code POST /r/{token}/responses} body: one answer per question (optionId for choice questions, text for text). */
public record Submission(List<Answer> answers) {

    public record Answer(Long questionId, Long optionId, String text) {
    }
}
