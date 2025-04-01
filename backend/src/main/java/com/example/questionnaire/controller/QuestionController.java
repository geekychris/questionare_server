package com.example.questionnaire.controller;

import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.dto.AnswerOptionDTO;
import com.example.questionnaire.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {
    @Autowired
    private QuestionService questionService;

    @GetMapping("/campaign/{campaignId}")
    public List<QuestionDTO> getQuestionsByCampaign(@PathVariable Long campaignId) {
        return questionService.getQuestionsByCampaign(campaignId);
    }

    @PostMapping
    public QuestionDTO createQuestion(@RequestBody QuestionDTO questionDTO) {
        return questionService.createQuestion(questionDTO);
    }

    @PutMapping("/{id}")
    public QuestionDTO updateQuestion(@PathVariable Long id, @RequestBody QuestionDTO questionDTO) {
        return questionService.updateQuestion(id, questionDTO);
    }

    @PostMapping("/{questionId}/options")
    public AnswerOptionDTO addAnswerOption(
            @PathVariable Long questionId,
            @RequestBody AnswerOptionDTO optionDTO) {
        return questionService.addAnswerOption(questionId, optionDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestion(id);
        return ResponseEntity.ok().build();
    }
}
