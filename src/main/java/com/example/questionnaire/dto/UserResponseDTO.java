package com.example.questionnaire.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserResponseDTO {
    private Long id;
    private Long questionId;
    private Long selectedOptionId;
    private String textResponse;
    private LocalDateTime submissionDate;
    private String userId;
}

