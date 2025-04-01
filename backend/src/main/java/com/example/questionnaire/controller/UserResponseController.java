package com.example.questionnaire.controller;

import com.example.questionnaire.dto.UserResponseDTO;
import com.example.questionnaire.service.UserResponseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/responses")
public class UserResponseController {
    @Autowired
    private UserResponseService userResponseService;

    @GetMapping("/user/{userId}")
    public List<UserResponseDTO> getUserResponses(@PathVariable String userId) {
        return userResponseService.getUserResponses(userId);
    }

    @PostMapping
    public UserResponseDTO submitResponse(@RequestBody UserResponseDTO responseDTO) {
        return userResponseService.submitResponse(responseDTO);
    }
}

