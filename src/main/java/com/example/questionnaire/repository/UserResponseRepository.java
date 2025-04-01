package com.example.questionnaire.repository;

import com.example.questionnaire.model.UserResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserResponseRepository extends JpaRepository<UserResponse, Long> {
    List<UserResponse> findByUserId(String userId);
}

