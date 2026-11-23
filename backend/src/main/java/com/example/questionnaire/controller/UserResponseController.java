package com.example.questionnaire.controller;

import com.example.questionnaire.dto.UserResponseDTO;
import com.example.questionnaire.service.UserResponseService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only lookup of stored answers. Respondents submit through the share link
 * ({@code POST /r/{token}/responses}); the old open {@code POST /api/responses} is gone.
 */
@RestController
@RequestMapping("/api/responses")
public class UserResponseController {
    private final UserResponseService userResponseService;

    public UserResponseController(UserResponseService userResponseService) {
        this.userResponseService = userResponseService;
    }

    @GetMapping("/user/{userId}")
    public List<UserResponseDTO> getUserResponses(@PathVariable String userId) {
        return userResponseService.getUserResponses(userId);
    }
}
