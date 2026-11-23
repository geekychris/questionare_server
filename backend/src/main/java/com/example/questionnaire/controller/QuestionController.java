package com.example.questionnaire.controller;

import com.example.questionnaire.dto.AnswerOptionDTO;
import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.service.QuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Per-question editing for campaigns the caller may manage. */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {
    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping("/campaign/{campaignId}")
    public List<QuestionDTO> getQuestionsByCampaign(@PathVariable Long campaignId, Authentication auth) {
        return questionService.getQuestionsByCampaign(campaignId, CurrentUser.from(auth));
    }

    @PostMapping
    public QuestionDTO createQuestion(@RequestBody QuestionDTO questionDTO, Authentication auth) {
        return questionService.createQuestion(questionDTO, CurrentUser.from(auth));
    }

    @PutMapping("/{id}")
    public QuestionDTO updateQuestion(@PathVariable Long id, @RequestBody QuestionDTO questionDTO, Authentication auth) {
        return questionService.updateQuestion(id, questionDTO, CurrentUser.from(auth));
    }

    @PostMapping("/{questionId}/options")
    public AnswerOptionDTO addAnswerOption(@PathVariable Long questionId, @RequestBody AnswerOptionDTO optionDTO,
                                           Authentication auth) {
        return questionService.addAnswerOption(questionId, optionDTO, CurrentUser.from(auth));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id, Authentication auth) {
        questionService.deleteQuestion(id, CurrentUser.from(auth));
        return ResponseEntity.noContent().build();
    }
}
