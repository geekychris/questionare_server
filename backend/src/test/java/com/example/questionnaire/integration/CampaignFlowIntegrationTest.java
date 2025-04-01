package com.example.questionnaire.integration;

import com.example.questionnaire.dto.AnswerOptionDTO;
import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.dto.UserResponseDTO;
import com.example.questionnaire.repository.CampaignRepository;
import com.example.questionnaire.repository.QuestionRepository;
import com.example.questionnaire.repository.UserResponseRepository;
import com.example.questionnaire.service.CampaignService;
import com.example.questionnaire.service.UserResponseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;


//https://stackoverflow.com/questions/56662861/its-possible-to-load-specific-data-sql-for-each-springboottest-annotation


@SpringBootTest
@AutoConfigureMockMvc
public class CampaignFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private UserResponseService userResponseService;

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private UserResponseRepository userResponseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private CampaignDTO testCampaignDTO;
    private String testUserId = "user123";

    @BeforeEach
    void setUp() {
        // Clean up any existing data
        userResponseRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up after tests
        userResponseRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    @Test
    void testCompleteCampaignFlow() throws Exception {
        // 1. Create a campaign with questions and answer options
        CampaignDTO createdCampaign = createTestCampaign();
        
        // Verify campaign was created
        assertNotNull(createdCampaign.getId());
        assertEquals(2, createdCampaign.getQuestions().size());
        
        // Get the campaign ID and question IDs for later use
        Long campaignId = createdCampaign.getId();
        List<QuestionDTO> questions = createdCampaign.getQuestions();
        
        // 2. Submit user responses for each question
        for (QuestionDTO question : questions) {
            UserResponseDTO responseDTO = new UserResponseDTO();
            responseDTO.setQuestionId(question.getId());
            responseDTO.setUserId(testUserId);
            
            if (!question.getOptions().isEmpty()) {
                // For multiple choice questions, select the first option
                responseDTO.setSelectedOptionId(question.getOptions().get(0).getId());
            } else {
                // For text questions
                responseDTO.setTextResponse("Test answer for question: " + question.getText());
            }
            
            // Submit the response
            UserResponseDTO savedResponse = userResponseService.submitResponse(responseDTO);
            
            // Verify response was saved
            assertNotNull(savedResponse.getId());
            assertEquals(question.getId(), savedResponse.getQuestionId());
            assertEquals(testUserId, savedResponse.getUserId());
        }
        
        // 3. Verify responses were saved correctly
        List<UserResponseDTO> userResponses = userResponseService.getUserResponses(testUserId);
        
        // Verify number of responses
        assertEquals(questions.size(), userResponses.size());
        
        // Verify each response corresponds to a question in the campaign
        for (UserResponseDTO response : userResponses) {
            boolean questionFound = questions.stream()
                    .anyMatch(q -> q.getId().equals(response.getQuestionId()));
            assertTrue(questionFound, "Response is for a question not in the campaign");
        }
        
        // Verify API endpoints
        // Get the campaign via API
        mockMvc.perform(get("/api/campaigns/" + campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(campaignId))
                .andExpect(jsonPath("$.title").value("Test Campaign"))
                .andExpect(jsonPath("$.questions.length()").value(2));

        // Verify user responses via API
        mockMvc.perform(get("/api/responses/user/" + testUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /**
     * Helper method to create a test campaign with questions and answer options
     */
    private CampaignDTO createTestCampaign() {
        // Create a new campaign DTO
        CampaignDTO campaignDTO = new CampaignDTO();
        campaignDTO.setTitle("Test Campaign");
        campaignDTO.setDescription("This is a test campaign");
        campaignDTO.setStartDate(LocalDateTime.now());
        campaignDTO.setEndDate(LocalDateTime.now().plusDays(7));
        campaignDTO.setActive(true);

        // Create questions with their answer options
        List<QuestionDTO> questions = new ArrayList<>();
        
        // Multiple choice question
        QuestionDTO mcQuestion = new QuestionDTO();
        mcQuestion.setText("What is your favorite color?");
        mcQuestion.setType("MULTIPLE_CHOICE");
        mcQuestion.setRequired(true);
        mcQuestion.setOrderIndex(1);
        
        List<AnswerOptionDTO> colorOptions = new ArrayList<>();
        
        AnswerOptionDTO redOption = new AnswerOptionDTO();
        redOption.setText("Red");
        redOption.setOrderIndex(1);
        
        AnswerOptionDTO blueOption = new AnswerOptionDTO();
        blueOption.setText("Blue");
        blueOption.setOrderIndex(2);
        
        AnswerOptionDTO greenOption = new AnswerOptionDTO();
        greenOption.setText("Green");
        greenOption.setOrderIndex(3);
        
        colorOptions.add(redOption);
        colorOptions.add(blueOption);
        colorOptions.add(greenOption);
        
        mcQuestion.setOptions(colorOptions);
        questions.add(mcQuestion);
        
        // Text question
        QuestionDTO textQuestion = new QuestionDTO();
        textQuestion.setText("Please provide additional feedback");
        textQuestion.setType("TEXT");
        textQuestion.setRequired(false);
        textQuestion.setOrderIndex(2);
        textQuestion.setOptions(new ArrayList<>()); // No options for text question
        
        questions.add(textQuestion);
        
        campaignDTO.setQuestions(questions);
        List<QuestionDTO> q= campaignDTO.getQuestions();
        // Save the campaign using the service
        return campaignService.createCampaign(campaignDTO);
    }
}

