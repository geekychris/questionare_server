package com.example.questionnaire.service;

import com.example.questionnaire.dto.UserResponseDTO;
import com.example.questionnaire.model.UserResponse;
import com.example.questionnaire.model.Question;
import com.example.questionnaire.model.AnswerOption;
import com.example.questionnaire.repository.UserResponseRepository;
import com.example.questionnaire.repository.QuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserResponseService {
    private final UserResponseRepository userResponseRepository;
    private final QuestionRepository questionRepository;

    @Autowired
    public UserResponseService(UserResponseRepository userResponseRepository, 
                             QuestionRepository questionRepository) {
        this.userResponseRepository = userResponseRepository;
        this.questionRepository = questionRepository;
    }

    @Transactional
    public UserResponseDTO submitResponse(UserResponseDTO responseDTO) {
        Question question = questionRepository.findById(responseDTO.getQuestionId())
            .orElseThrow(() -> new RuntimeException("Question not found with ID: " + responseDTO.getQuestionId()));

        UserResponse response = new UserResponse();
        response.setQuestion(question);
        response.setUserId(responseDTO.getUserId());
        response.setSubmissionDate(LocalDateTime.now());

        // Handle different question types
        if ("text".equalsIgnoreCase(question.getType())) {
            response.setTextResponse(responseDTO.getTextResponse());
        } else if ("radio".equalsIgnoreCase(question.getType()) && responseDTO.getSelectedOptionId() != null) {
            // Find and set the selected option for radio button questions
            AnswerOption selectedOption = question.getOptions().stream()
                .filter(option -> option.getId().equals(responseDTO.getSelectedOptionId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Answer option not found"));
            
            response.setSelectedOption(selectedOption);
        }

        response = userResponseRepository.save(response);
        return convertToDTO(response);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> getUserResponses(String userId) {
        return userResponseRepository.findByUserId(userId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> getUserResponsesForCampaign(String userId, Long campaignId) {
        return userResponseRepository.findByUserIdAndQuestion_Campaign_IdOrderBySubmissionDateDesc(userId, campaignId)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponseDTO getLatestResponse(String userId, Long questionId) {
        return userResponseRepository.findByUserIdAndQuestionIdOrderBySubmissionDateDesc(userId, questionId)
            .stream()
            .findFirst()
            .map(this::convertToDTO)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean hasUserCompletedCampaign(String userId, Long campaignId) {
        List<UserResponse> responses = userResponseRepository
            .findByUserIdAndQuestion_Campaign_IdOrderBySubmissionDateDesc(userId, campaignId);
        return !responses.isEmpty();
    }

    private UserResponseDTO convertToDTO(UserResponse response) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(response.getId());
        dto.setQuestionId(response.getQuestion().getId());
        dto.setUserId(response.getUserId());
        dto.setTextResponse(response.getTextResponse());
        if (response.getSelectedOption() != null) {
            dto.setSelectedOptionId(response.getSelectedOption().getId());
        }
        dto.setSubmissionDate(response.getSubmissionDate());
        return dto;
    }
}

