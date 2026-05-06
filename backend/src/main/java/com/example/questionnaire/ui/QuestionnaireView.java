package com.example.questionnaire.ui;

import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.dto.UserResponseDTO;
import com.example.questionnaire.service.QuestionService;
import com.example.questionnaire.service.UserResponseService;
import com.example.questionnaire.ui.component.QuestionComponent;
import com.example.questionnaire.ui.component.QuestionComponent.QuestionNavigationEvent;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.DetachEvent;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.PageTitle;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
@Route(value = "questionnaire", layout = MainLayout.class)
@PageTitle("Questionnaire")
public class QuestionnaireView extends VerticalLayout {
    // Component fields
    private H2 title;
    private ProgressBar progressBar;
    private QuestionComponent questionComponent;
    private Button previousButton;
    private Button nextButton;
    private Div navigationDiv;

    // Data fields
    private List<QuestionDTO> questions;
    private int currentQuestionIndex = 0;
    private Map<Long, String> answers = new HashMap<>();
    private String userId;
    
    // Executor for async tasks - needs to be cleaned up on detach
    private java.util.concurrent.ScheduledExecutorService animationExecutor;
    private java.util.concurrent.ScheduledExecutorService resetExecutor;

    // Service fields
    private final QuestionService questionService;
    private final UserResponseService userResponseService;

    @Autowired
    public QuestionnaireView(QuestionService questionService, UserResponseService userResponseService) {
        this.questionService = questionService;
        this.userResponseService = userResponseService;

        // Initialize user ID
        this.userId = UUID.randomUUID().toString();

        // Setup UI components first
        setupLayout();
        
        try {
            // Load questions
            loadQuestions();
            
            // Load the first question if questions were loaded successfully
            if (questions != null && !questions.isEmpty()) {
                loadCurrentQuestion();
            } else {
                showErrorNotification("No questions available. Please try again later.");
            }
        } catch (Exception e) {
            showErrorNotification("Failed to load questions. Please refresh the page.");
        }
    }

    private void loadQuestions() {
        try {
            // Verify service is available
            if (questionService == null) {
                questions = java.util.Collections.emptyList();
                throw new RuntimeException("Question service is not initialized");
            }
            
            try {
                // Get questions for the default campaign (ID 1)
                // This could be parameterized in a real application
                questions = questionService.getQuestionsByCampaign(1L);
                
                // Initialize empty list if null to prevent NullPointerException
                if (questions == null) {
                    questions = java.util.Collections.emptyList();
                    throw new RuntimeException("No questions returned from service");
                }
                
                // Validate returned questions
                for (QuestionDTO question : questions) {
                    if (question == null || question.getId() == null) {
                        System.err.println("Warning: Invalid question found in results (null or missing ID)");
                    }
                }
            } catch (Exception e) {
                System.err.println("Error loading questions: " + e.getMessage());
                questions = java.util.Collections.emptyList();
                throw e; // Re-throw to be handled by the caller
            }
        } catch (Exception e) {
            System.err.println("Critical error in loadQuestions: " + e.getMessage());
            questions = java.util.Collections.emptyList();
            throw e; // Re-throw to be handled by the caller
        }
    }

    private void setupLayout() {
        addClassName("questionnaire-view");
        setPadding(true);
        setSpacing(true);
        
        // Create and add the title
        title = new H2("Questionnaire");
        title.addClassName("questionnaire-title");
        add(title);

        // Create and add the progress section
        Div progressSection = new Div();
        progressSection.addClassName("progress-bar");
        
        progressBar = new ProgressBar();
        progressBar.setMin(0);
        progressBar.setMax(1);
        progressBar.setValue(0);
        progressBar.setWidth("100%");
        
        progressSection.add(progressBar);
        add(progressSection);

        // Create and add the question component
        questionComponent = new QuestionComponent();
        questionComponent.addClassName("question-component");
        add(questionComponent);

        setupNavigation();
        updateNavigationButtons();
    }

    private void setupNavigation() {
        // Create the navigation container
        navigationDiv = new Div();
        navigationDiv.addClassName("navigation-buttons");
        
        // Create the previous button
        previousButton = new Button("Previous", e -> showPreviousQuestion());
        previousButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        previousButton.setIcon(new Icon(VaadinIcon.ARROW_LEFT));
        previousButton.addClassName("previous-button");
        
        // Create the next button
        nextButton = new Button("Next", e -> showNextQuestion());
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        nextButton.setIconAfterText(true);
        nextButton.setIcon(new Icon(VaadinIcon.ARROW_RIGHT));
        nextButton.addClassName("next-button");
        
        navigationDiv.add(previousButton, nextButton);
        add(navigationDiv);
    }

    private void loadCurrentQuestion() {
        try {
            if (questions == null || questions.isEmpty()) {
                showErrorNotification("No questions available.");
                return;
            }
            
            if (currentQuestionIndex < 0 || currentQuestionIndex >= questions.size()) {
                showErrorNotification("Invalid question index.");
                return;
            }
            
            QuestionDTO currentQuestion = questions.get(currentQuestionIndex);
            if (currentQuestion == null) {
                showErrorNotification("Question data is invalid.");
                return;
            }
            
            questionComponent.setQuestion(currentQuestion);
            
            // Update progress bar and question counter
            double progress = (double) (currentQuestionIndex + 1) / questions.size();
            progressBar.setValue(progress);
            
            // Add question counter above progress bar
            if (progressBar.getParent().isPresent() && progressBar.getParent().get() instanceof Div) {
                Div progressSection = (Div) progressBar.getParent().get();
                progressSection.removeAll();
                
                // Create question counter text
                Span questionCounter = new Span(String.format("Question %d of %d", 
                    currentQuestionIndex + 1, questions.size()));
                questionCounter.addClassName("question-counter");
                
                // Create progress percentage
                Span progressPercentage = new Span(String.format("%.0f%%", progress * 100));
                progressPercentage.addClassName("progress-percentage");
                
                // Add counter and progress bar
                HorizontalLayout counterLayout = new HorizontalLayout(questionCounter, progressPercentage);
                counterLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
                counterLayout.setWidthFull();
                
                progressSection.add(counterLayout, progressBar);
            }
            
            // Update navigation buttons
            updateNavigationButtons();
            
            // Load existing answer if available
            if (answers.containsKey(currentQuestion.getId())) {
                questionComponent.setAnswer(answers.get(currentQuestion.getId()));
            }
            
            try {
                // Load previous response if it exists
                UserResponseDTO previousResponse = userResponseService.getLatestResponse(userId, currentQuestion.getId());
                if (previousResponse != null) {
                    if (previousResponse.getTextResponse() != null) {
                        questionComponent.setAnswer(previousResponse.getTextResponse());
                    } else if (previousResponse.getSelectedOptionId() != null && 
                               currentQuestion.getOptions() != null) {
                        currentQuestion.getOptions().stream()
                            .filter(opt -> opt != null && opt.getId() != null && 
                                   opt.getId().equals(previousResponse.getSelectedOptionId()))
                            .findFirst()
                            .ifPresent(opt -> questionComponent.setAnswer(opt.getText()));
                    }
                }
            } catch (Exception e) {
                // Log the error but continue - previous responses are not critical
                System.err.println("Error loading previous responses: " + e.getMessage());
            }
            
            // Add entrance animation class safely
            if (questionComponent != null && questionComponent.getElement() != null) {
                questionComponent.getElement().getClassList().add("question-enter");
                
                // Use a safer asynchronous approach for removing the animation class
                final UI ui = UI.getCurrent();
                if (ui != null) {
                    // Schedule a task to remove the class after animation completes
                    // Use a synchronized block to avoid concurrent modification issues
                    java.util.concurrent.ScheduledExecutorService executor = 
                        java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                    
                    // Store the executor in a class field for cleanup
                    animationExecutor = executor;
                    
                    // Schedule the task to run after animation completes
                    executor.schedule(() -> {
                        ui.access(() -> {
                            if (questionComponent != null && questionComponent.getElement() != null) {
                                questionComponent.getElement().getClassList().remove("question-enter");
                            }
                            
                            // Shutdown the executor after use
                            executor.shutdown();
                        });
                    }, 300, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            showErrorNotification("Error loading question: " + e.getMessage());
        }
    }

    private void showPreviousQuestion() {
        saveCurrentAnswer();
        if (currentQuestionIndex > 0) {
            currentQuestionIndex--;
            loadCurrentQuestion();
        }
    }

    private void showNextQuestion() {
        if (!validateCurrentAnswer()) {
            showErrorNotification("Please answer the required question");
            return;
        }
        
        saveCurrentAnswer();
        if (currentQuestionIndex < questions.size() - 1) {
            currentQuestionIndex++;
            loadCurrentQuestion();
        } else {
            submitQuestionnaire();
        }
    }

    private void showErrorNotification(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private void showSuccessNotification(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private boolean validateCurrentAnswer() {
        try {
            if (questions == null || questions.isEmpty() || 
                currentQuestionIndex < 0 || currentQuestionIndex >= questions.size()) {
                return false;
            }
            
            QuestionDTO currentQuestion = questions.get(currentQuestionIndex);
            if (currentQuestion == null) {
                return false;
            }
            
            if (currentQuestion.isRequired()) {
                String answer = questionComponent.getAnswer();
                return answer != null && !answer.trim().isEmpty();
            }
            return true;
        } catch (Exception e) {
            showErrorNotification("Error validating answer: " + e.getMessage());
            return false;
        }
    }

    private void saveCurrentAnswer() {
        try {
            if (questions == null || questions.isEmpty() || 
                currentQuestionIndex < 0 || currentQuestionIndex >= questions.size() ||
                questionComponent == null) {
                return;
            }
            
            String answer = questionComponent.getAnswer();
            QuestionDTO currentQuestion = questions.get(currentQuestionIndex);
            
            if (currentQuestion == null) {
                return;
            }
            
            if (answer != null && !answer.isEmpty()) {
                answers.put(currentQuestion.getId(), answer);
                
                // Submit the individual response
                UserResponseDTO responseDTO = new UserResponseDTO();
                responseDTO.setQuestionId(currentQuestion.getId());
                responseDTO.setUserId(userId);
                
                if ("text".equalsIgnoreCase(currentQuestion.getType())) {
                    responseDTO.setTextResponse(answer);
                } else if ("radio".equalsIgnoreCase(currentQuestion.getType()) || 
                           "checkbox".equalsIgnoreCase(currentQuestion.getType())) {
                    // For radio button or checkbox responses, find the selected option ID
                    if (currentQuestion.getOptions() != null) {
                        currentQuestion.getOptions().stream()
                            .filter(opt -> opt != null && opt.getText() != null && 
                                   opt.getText().equals(answer))
                            .findFirst()
                            .ifPresent(opt -> responseDTO.setSelectedOptionId(opt.getId()));
                    }
                }
                
                try {
                    userResponseService.submitResponse(responseDTO);
                } catch (Exception e) {
                    System.err.println("Failed to save response: " + e.getMessage());
                    // Continue even if saving fails - don't interrupt the user experience
                    // But log the error for debugging
                }
            }
        } catch (Exception e) {
            System.err.println("Error in saveCurrentAnswer: " + e.getMessage());
            // Don't show notification here as it might disrupt the UI flow
            // Just log the error and continue
        }
    }

    private void updateNavigationButtons() {
        previousButton.setEnabled(currentQuestionIndex > 0);
        
        boolean isLastQuestion = currentQuestionIndex == questions.size() - 1;
        nextButton.setText(isLastQuestion ? "Submit" : "Next");
        nextButton.setIcon(new Icon(isLastQuestion ? VaadinIcon.CHECK : VaadinIcon.ARROW_RIGHT));
        
        if (isLastQuestion) {
            nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        } else {
            nextButton.removeThemeVariants(ButtonVariant.LUMO_SUCCESS);
            nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        }
    }

    private void submitQuestionnaire() {
        try {
            if (!validateCurrentAnswer()) {
                showErrorNotification("Please answer all required questions before submitting");
                return;
            }

            saveCurrentAnswer();
            
            showSuccessNotification("Thank you for completing the questionnaire!");
            
            // Safely add success animation
            if (questionComponent != null && questionComponent.getElement() != null) {
                try {
                    questionComponent.getElement().executeJs(
                        "this.animate([" +
                        "  { transform: 'scale(1)' }," +
                        "  { transform: 'scale(1.02)' }," +
                        "  { transform: 'scale(1)' }" +
                        "], {" +
                        "  duration: 300," +
                        "  iterations: 1" +
                        "})");
                } catch (Exception e) {
                    // Animation is non-critical, continue if it fails
                    System.err.println("Animation error: " + e.getMessage());
                }
            }
            
            // Use a safer async approach with scheduled executor
            final UI ui = UI.getCurrent();
            if (ui != null) {
                // Create a scheduler for the delay
                java.util.concurrent.ScheduledExecutorService executor = 
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                
                // Store the executor for cleanup
                resetExecutor = executor;
                
                // Schedule reset after delay without blocking UI thread
                executor.schedule(() -> {
                    ui.access(() -> {
                        try {
                            resetQuestionnaire();
                        } finally {
                            // Clean up executor after use
                            executor.shutdown();
                        }
                    });
                }, 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } else {
                // If UI is null for some reason, reset immediately
                resetQuestionnaire();
            }
        } catch (Exception e) {
            System.err.println("Error in submitQuestionnaire: " + e.getMessage());
            showErrorNotification("Error submitting questionnaire. Please try again.");
        }
    }
    
    private void resetQuestionnaire() {
        try {
            // Generate a new user ID for the next submission
            this.userId = UUID.randomUUID().toString();
            
            // Clear all saved answers
            answers.clear();
            
            // Reset to the first question
            currentQuestionIndex = 0;
            
            // Reload the first question
            loadCurrentQuestion();
            
            // Safely scroll to top
            try {
                if (getElement() != null) {
                    getElement().executeJs("window.scrollTo(0, 0)");
                }
            } catch (Exception e) {
                // Non-critical operation, just log the error
                System.err.println("Error scrolling to top: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Error resetting questionnaire: " + e.getMessage());
            // Try to handle the error gracefully
            try {
                // If reset fails, try to at least show the first question again
                currentQuestionIndex = 0;
                if (questions != null && !questions.isEmpty()) {
                    questionComponent.setQuestion(questions.get(0));
                    updateNavigationButtons();
                }
            } catch (Exception ex) {
                // Last resort - show an error to the user
                showErrorNotification("An error occurred. Please refresh the page.");
            }
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        // Clean up resources when the view is detached
        try {
            // Make sure we don't leave any UI polling active
            UI ui = UI.getCurrent();
            if (ui != null) {
                ui.setPollInterval(-1);
            }
            
            // Clean up question component if needed
            if (questionComponent != null) {
                try {
                    // Remove any lingering class names that might cause issues
                    if (questionComponent.getElement() != null) {
                        questionComponent.getElement().getClassList().remove("question-enter");
                    }
                } catch (Exception ex) {
                    System.err.println("Error cleaning up question component: " + ex.getMessage());
                }
            }
            
            // Clean up any running executors
            if (animationExecutor != null && !animationExecutor.isShutdown()) {
                try {
                    animationExecutor.shutdownNow();
                    animationExecutor = null;
                } catch (Exception ex) {
                    System.err.println("Error shutting down animation executor: " + ex.getMessage());
                }
            }
            
            if (resetExecutor != null && !resetExecutor.isShutdown()) {
                try {
                    resetExecutor.shutdownNow();
                    resetExecutor = null;
                } catch (Exception ex) {
                    System.err.println("Error shutting down reset executor: " + ex.getMessage());
                }
            }
            
            // Clear any cached data
            answers.clear();
            
            // Call super at the end to ensure our cleanup runs first
            super.onDetach(detachEvent);
        } catch (Exception e) {
            System.err.println("Error in onDetach: " + e.getMessage());
            try {
                super.onDetach(detachEvent);
            } catch (Exception ex) {
                System.err.println("Error in super.onDetach: " + ex.getMessage());
            }
        }
    }
}
