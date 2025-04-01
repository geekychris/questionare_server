package com.example.questionnaire.dto;

import lombok.Data;
import java.util.List;

@Data
public class QuestionDTO {
    private Long id;
    private String text;
    private String type;
    private boolean required;
    private int orderIndex;
    private Long campaignId;
    private List<AnswerOptionDTO> options;
}

