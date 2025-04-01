package com.example.questionnaire.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CampaignDTO {
    private Long id;
    private String title;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean active;
    private List<QuestionDTO> questions;
}

