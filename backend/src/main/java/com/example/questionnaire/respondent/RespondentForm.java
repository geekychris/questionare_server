package com.example.questionnaire.respondent;

import java.util.List;

/** What a respondent sees: only this campaign's title, description and questions. */
public record RespondentForm(String title, String description, boolean requireLogin, List<Question> questions) {

    public record Question(Long id, String text, String type, boolean required, List<Option> options) {
    }

    public record Option(Long id, String text) {
    }
}
