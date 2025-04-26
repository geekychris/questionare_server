package com.example.questionnaire.repository;

import com.example.questionnaire.model.UserResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserResponseRepository extends JpaRepository<UserResponse, Long> {
    List<UserResponse> findByUserId(String userId);
    List<UserResponse> findByQuestionId(Long questionId);
    List<UserResponse> findByUserIdAndQuestionIdOrderBySubmissionDateDesc(String userId, Long questionId);
    List<UserResponse> findByUserIdAndQuestion_Campaign_IdOrderBySubmissionDateDesc(String userId, Long campaignId);
}

