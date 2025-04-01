package com.example.questionnaire.service;

import com.example.questionnaire.dto.UserResponseDTO;
import com.example.questionnaire.model.Question;
import com.example.questionnaire.model.UserResponse;
import com.example.questionnaire.model.AnswerOption;
import com.example.questionnaire.repository.QuestionRepository;
import com.example.questionnaire.repository.UserResponseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserResponseService {
    @Autowired
    private UserResponseRepository userResponseRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Transactional(readOnly = true)
    public List<UserResponseDTO> getUserResponses(String userId) {
        return userResponseRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponseDTO submitResponse(UserResponseDTO responseDTO) {
        UserResponse response = new UserResponse();
        
        Question question = questionRepository.findById(responseDTO.getQuestionId())
                .orElseThrow(() -> new RuntimeException("Question not found"));
        
        response.setQuestion(question);
        response.setTextResponse(responseDTO.getTextResponse());
        response.setSubmissionDate(LocalDateTime.now());
        response.setUserId(responseDTO.getUserId());
        
        if (responseDTO.getSelectedOptionId() != null) {
            // Find the selected option from the question's options
            AnswerOption selectedOption = question.getOptions().stream()
                    .filter(option -> option.getId().equals(responseDTO.getSelectedOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Answer option not found"));
            
            response.setSelectedOption(selectedOption);
        }
        
        response = userResponseRepository.save(response);
        return convertToDTO(response);
    }

    private UserResponseDTO convertToDTO(UserResponse response) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(response.getId());
        dto.setQuestionId(response.getQuestion().getId());
        dto.setTextResponse(response.getTextResponse());
        dto.setSubmissionDate(response.getSubmissionDate());
        dto.setUserId(response.getUserId());
        
        if (response.getSelectedOption() != null) {
            dto.setSelectedOptionId(response.getSelectedOption().getId());
        }
        
        return dto;
    }
}

