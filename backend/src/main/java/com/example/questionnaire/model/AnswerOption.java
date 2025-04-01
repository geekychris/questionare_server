package com.example.questionnaire.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class AnswerOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String text;
    private int orderIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    private Question question;
}

