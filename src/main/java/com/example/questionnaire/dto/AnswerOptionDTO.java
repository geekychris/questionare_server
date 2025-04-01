package com.example.questionnaire.dto;

import lombok.Data;

@Data
public class AnswerOptionDTO {
    private Long id;
    private String text;
    private int orderIndex;
}

