package com.example.questionnaire.service;

import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.dto.AnswerOptionDTO;
import com.example.questionnaire.model.Question;
import com.example.questionnaire.model.AnswerOption;
import com.example.questionnaire.model.Campaign;
import com.example.questionnaire.repository.QuestionRepository;
import com.example.questionnaire.repository.CampaignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuestionService {
    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @Transactional(readOnly = true)
    public List<QuestionDTO> getQuestionsByCampaign(Long campaignId) {
        return questionRepository.findByCampaignIdOrderByOrderIndexAsc(campaignId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public QuestionDTO createQuestion(QuestionDTO questionDTO) {
        Campaign campaign = campaignRepository.findById(questionDTO.getCampaignId())
                .orElseThrow(() -> new RuntimeException("Campaign not found"));

        Question question = convertToEntity(questionDTO);
        question.setCampaign(campaign);
        question = questionRepository.save(question);
        return convertToDTO(question);
    }

    @Transactional
    public QuestionDTO updateQuestion(Long id, QuestionDTO questionDTO) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Question not found"));
        
        question.setText(questionDTO.getText());
        question.setType(questionDTO.getType());
        question.setRequired(questionDTO.isRequired());
        question.setOrderIndex(questionDTO.getOrderIndex());
        
        question = questionRepository.save(question);
        return convertToDTO(question);
    }

    @Transactional
    public AnswerOptionDTO addAnswerOption(Long questionId, AnswerOptionDTO optionDTO) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found"));

        AnswerOption option = new AnswerOption();
        option.setText(optionDTO.getText());
        option.setOrderIndex(optionDTO.getOrderIndex());
        option.setQuestion(question);

        question.getOptions().add(option);
        questionRepository.save(question);

        return convertToDTO(option);
    }

    @Transactional
    public void deleteQuestion(Long id) {
        questionRepository.deleteById(id);
    }

    private QuestionDTO convertToDTO(Question question) {
        QuestionDTO dto = new QuestionDTO();
        dto.setId(question.getId());
        dto.setText(question.getText());
        dto.setType(question.getType());
        dto.setRequired(question.isRequired());
        dto.setOrderIndex(question.getOrderIndex());
        dto.setCampaignId(question.getCampaign().getId());
        dto.setOptions(question.getOptions().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()));
        return dto;
    }

    private AnswerOptionDTO convertToDTO(AnswerOption option) {
        AnswerOptionDTO dto = new AnswerOptionDTO();
        dto.setId(option.getId());
        dto.setText(option.getText());
        dto.setOrderIndex(option.getOrderIndex());
        return dto;
    }

    private Question convertToEntity(QuestionDTO dto) {
        Question question = new Question();
        question.setText(dto.getText());
        question.setType(dto.getType());
        question.setRequired(dto.isRequired());
        question.setOrderIndex(dto.getOrderIndex());
        return question;
    }
}

