package com.example.questionnaire.ui.component;

import com.example.questionnaire.dto.QuestionDTO;
import com.example.questionnaire.dto.AnswerOptionDTO;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.component.ComponentEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuestionComponent extends VerticalLayout {
    private H3 questionText;
    private Span requiredIndicator;
    private Component answerComponent;
    private QuestionDTO currentQuestion;
    private Div questionContainer;
    
    // Track component state to prevent race conditions
    private boolean isAnimating = false;
    private String componentId;
    
    // Track registrations for cleanup
    private List<Registration> registrations = new ArrayList<>();

    public QuestionComponent() {
        addClassName("question-component");
        setPadding(true);
        setSpacing(true);
        
        // Generate unique ID for this component instance
        componentId = "question-component-" + UUID.randomUUID().toString().substring(0, 8);
        getElement().setAttribute("id", componentId);
        questionContainer = new Div();
        questionContainer.addClassName("question-container");
        
        questionText = new H3();
        questionText.addClassName("question-text");
        
        requiredIndicator = new Span(" *");
        requiredIndicator.addClassName("required-indicator");
        
        questionContainer.add(questionText);
        add(questionContainer);
        
        // Add skip link for keyboard users
        Span skipLink = new Span("Skip to answer field");
        skipLink.addClassName("skip-link");
        skipLink.getElement().setAttribute("tabindex", "0");
        Registration registration = skipLink.addClickListener(e -> {
            try {
                focusAnswerField();
            } catch (Exception ex) {
                // Log the error but don't crash
                System.err.println("Error focusing answer field: " + ex.getMessage());
            }
        });
        registrations.add(registration);
        add(skipLink);
    }
    
    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        
        try {
            // Add keyboard shortcut for focusing the answer field
            getElement().getNode().runWhenAttached(ui -> {
                Registration registration = ui.addShortcutListener(
                    () -> {
                        try {
                            focusAnswerField();
                        } catch (Exception e) {
                            // Log the error but don't crash
                            System.err.println("Error in keyboard shortcut: " + e.getMessage());
                        }
                    },
                    Key.KEY_F, KeyModifier.ALT);
                
                // Add to registrations for proper cleanup
                registrations.add(registration);
            });
        } catch (Exception e) {
            // Ensure any errors don't crash the application
            System.err.println("Error in onAttach: " + e.getMessage());
        }
    }
    
    @Override
    protected void onDetach(DetachEvent detachEvent) {
        try {
            // Clean up JavaScript listeners to prevent memory leaks
            getElement().executeJs(
                "if (this._questionComponentListeners) {" +
                "  this._questionComponentListeners.forEach(listener => {" +
                "    if (listener.element && listener.type && listener.fn) {" +
                "      listener.element.removeEventListener(listener.type, listener.fn);" +
                "    }" +
                "  });" +
                "  delete this._questionComponentListeners;" +
                "}"
            );
            
            // Clean up all registered event listeners
            for (Registration registration : registrations) {
                try {
                    registration.remove();
                } catch (Exception e) {
                    // Just log the error and continue with cleanup
                    System.err.println("Error removing registration: " + e.getMessage());
                }
            }
            registrations.clear();
            
            super.onDetach(detachEvent);
        } catch (Exception e) {
            System.err.println("Error in onDetach: " + e.getMessage());
            // We don't call super.onDetach again to avoid duplicate calls
        }
    }

    private void focusAnswerField() {
        try {
            if (answerComponent instanceof Focusable) {
                ((Focusable<?>) answerComponent).focus();
            }
        } catch (Exception e) {
            // Log error but don't crash
            System.err.println("Error focusing answer field: " + e.getMessage());
        }
    }

    public void setQuestion(QuestionDTO question) {
        if (question == null) {
            System.err.println("Warning: Attempted to set null question");
            return;
        }
        
        // Get UI instance for thread-safe access
        final UI ui = UI.getCurrent();
        if (ui == null) {
            System.err.println("Error: UI instance is null, cannot update component");
            return;
        }
        
        // Use synchronized block to ensure animation state is thread-safe
        synchronized (this) {
            try {
                // Prevent concurrent animations which can cause UI issues
                if (isAnimating) {
                    // Clean up any pending animations to prevent conflicts
                    ui.access(() -> {
                        try {
                            getElement().executeJs(
                                "clearTimeout(this._animationTimeout);" +
                                "const exitElements = this.querySelectorAll('.question-exit');" +
                                "exitElements.forEach(el => el.remove());"
                            );
                        } catch (Exception e) {
                            System.err.println("Error cleaning up animations: " + e.getMessage());
                        }
                    });
                }
                
                isAnimating = true;
                this.currentQuestion = question;
                
                final String thisComponentId = componentId; // Capture for closures
                
                // Perform UI updates in UI thread
                ui.access(() -> {
                    // Add exit animation to current components with proper selectors to avoid conflicts
                    if (answerComponent != null) {
                        try {
                            answerComponent.getElement().getClassList().add("question-exit");
                            questionText.getElement().getClassList().add("question-exit");
                            
                            // Set unique IDs for animation targets to avoid conflicts with other instances
                            String exitId = "exit-" + UUID.randomUUID().toString().substring(0, 8);
                            answerComponent.getElement().setAttribute("id", exitId);
                        } catch (Exception e) {
                            // Log but continue if animation fails - not critical
                            System.err.println("Animation error: " + e.getMessage());
                        }
                    }
                });
            
                // Use safer client-side script to manage animations
                getElement().getNode().runWhenAttached(elementUi -> {
                    ui.access(() -> {
                        try {
                            elementUi.beforeClientResponse(this, context -> {
                                try {
                                    getElement().executeJs(
                                        // Store animation timeout ID for cleanup
                                        "this._animationTimeout = setTimeout(() => {" +
                                        "  const container = document.getElementById('" + thisComponentId + "');" +
                                        "  if (container) {" +
                                        "    const exitElements = container.querySelectorAll('.question-exit');" +
                                        "    exitElements.forEach(el => {" +
                                        "      if (el) el.style.display = 'none';" +
                                        "    });" +
                                        "  }" +
                                        "}, 200);"
                                    );
                                } catch (Exception e) {
                                    System.err.println("Error in animation script: " + e.getMessage());
                                    // Clear animation elements directly as fallback
                                    try {
                                        removeExitElements();
                                    } catch (Exception ex) {
                                        System.err.println("Fallback cleanup failed: " + ex.getMessage());
                                    }
                                }
                            });
                        } catch (Exception e) {
                            System.err.println("Error in UI beforeClientResponse: " + e.getMessage());
                        }
                    });
                });

                // Update question text and required indicator in UI thread
                ui.access(() -> {
                    try {
                        // Create new components to avoid state issues
                        questionText = new H3();
                        questionText.addClassName("question-text");
                        questionText.setText(question.getText());
                        questionText.getElement().setAttribute("role", "heading");
                        questionText.getElement().setAttribute("aria-level", "2");
                        
                        // Clear and update container
                        questionContainer.removeAll();
                        questionContainer.add(questionText);
                        
                        if (question.isRequired()) {
                            requiredIndicator = new Span(" *");
                            requiredIndicator.addClassName("required-indicator");
                            requiredIndicator.getElement().setAttribute("aria-label", "Required field");
                            questionContainer.add(requiredIndicator);
                        }
                        
                        // Create and add new answer component
                        Component newAnswerComponent = createAnswerComponent(question);
                        
                        // Add ARIA labeling
                        if (questionText.getElement().getAttribute("id") != null) {
                            newAnswerComponent.getElement().setAttribute("aria-labelledby", 
                                questionText.getElement().getAttribute("id"));
                        }
                        
                        // Store reference and add to layout
                        answerComponent = newAnswerComponent;
                        add(answerComponent);
                    } catch (Exception e) {
                        System.err.println("Error setting up question components: " + e.getMessage());
                        // Try to recover by creating a basic component
                        try {
                            if (answerComponent == null) {
                                answerComponent = createTextField(question);
                                add(answerComponent);
                            }
                        } catch (Exception ex) {
                            System.err.println("Recovery failed: " + ex.getMessage());
                        }
                    }
                });
                // Use safer client-side cleanup script
                getElement().getNode().runWhenAttached(elementUi -> {
                    ui.access(() -> {
                        try {
                            elementUi.beforeClientResponse(this, context -> {
                                try {
                                    getElement().executeJs(
                                        "setTimeout(() => {" +
                                        "  const container = document.getElementById('" + thisComponentId + "');" +
                                        "  if (container) {" +
                                        "    const exitElements = container.querySelectorAll('.question-exit');" +
                                        "    exitElements.forEach(el => el.remove());" +
                                        "    // Store this as a client-side listener for cleanup" +
                                        "    if (!container._questionComponentListeners) {" +
                                        "      container._questionComponentListeners = [];" +
                                        "    }" +
                                        "  }" +
                                        "}, 300);"
                                    );
                                } catch (Exception e) {
                                    System.err.println("Error in cleanup script: " + e.getMessage());
                                }
                            });
                        } catch (Exception e) {
                            System.err.println("Error in UI beforeClientResponse: " + e.getMessage());
                        }
                    });
                });
                
                // Reset animation state once animations are complete
                getElement().getNode().runWhenAttached(elementUi -> {
                    ui.access(() -> {
                        try {
                            elementUi.beforeClientResponse(this, context -> {
                                try {
                                    getElement().executeJs(
                                        "setTimeout(() => { this._isAnimating = false; }, 500);"
                                    );
                                } catch (Exception e) {
                                    System.err.println("Error in animation reset script: " + e.getMessage());
                                }
                            });
                        } catch (Exception e) {
                            System.err.println("Error in UI beforeClientResponse: " + e.getMessage());
                        }
                    });
                });
                
                // Schedule a final cleanup after all animations
                ui.access(() -> {
                    try {
                        // Set timeout to reset animation state if it hasn't been done by JavaScript
                        java.util.concurrent.ScheduledExecutorService executor = 
                            java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
                        executor.schedule(() -> {
                            ui.access(() -> {
                                synchronized (this) {
                                    isAnimating = false;
                                }
                                executor.shutdown();
                            });
                        }, 600, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        System.err.println("Error scheduling animation cleanup: " + e.getMessage());
                        // Make sure animation state is reset even if scheduling fails
                        synchronized (this) {
                            isAnimating = false;
                        }
                    }
                });
            } catch (Exception e) {
                System.err.println("Error in setQuestion: " + e.getMessage());
                // Make sure animation state is reset
                synchronized (this) {
                    isAnimating = false;
                }
            }
        }
    }

    /**
     * Helper method to remove exit animation elements
     */
    private void removeExitElements() {
        if (getElement() != null) {
            getElement().executeJs(
                "this.querySelectorAll('.question-exit').forEach(el => el.remove());"
            );
        }
    }

    private Component createAnswerComponent(QuestionDTO question) {
        if ("text".equalsIgnoreCase(question.getType())) {
            if (question.getText().length() > 100) {
                return createTextArea(question);
            }
            return createTextField(question);
        } else if ("radio".equalsIgnoreCase(question.getType())) {
            return createRadioButtons(question);
        }
        return createTextField(question);
    }
    
    private TextField createTextField(QuestionDTO question) {
        TextField textField = new TextField();
        textField.setWidthFull();
        textField.setRequired(question.isRequired());
        textField.setErrorMessage("This field is required");
        textField.setValueChangeMode(ValueChangeMode.EAGER);
        textField.addClassName("answer-field");
        
        // Add ARIA attributes
        textField.getElement().setAttribute("aria-required", 
            String.valueOf(question.isRequired()));
        
        // Add validation
        textField.addValueChangeListener(event -> {
            if (question.isRequired() && event.getValue().trim().isEmpty()) {
                textField.setInvalid(true);
            } else {
                textField.setInvalid(false);
            }
        });
        
        // Add keyboard navigation
        textField.addKeyDownListener(Key.ENTER, event -> {
            if (event.getModifiers().contains(KeyModifier.SHIFT)) {
                fireEvent(new QuestionNavigationEvent(this, true));
            } else {
                fireEvent(new QuestionNavigationEvent(this, false));
            }
        });
        
        return textField;
    }

    private TextArea createTextArea(QuestionDTO question) {
        TextArea textArea = new TextArea();
        textArea.setWidthFull();
        textArea.setMinHeight("150px");
        textArea.setRequired(question.isRequired());
        textArea.setErrorMessage("This field is required");
        textArea.setValueChangeMode(ValueChangeMode.EAGER);
        textArea.addClassName("answer-field");
        
        // Add ARIA attributes
        textArea.getElement().setAttribute("aria-required", 
            String.valueOf(question.isRequired()));
        
        // Add validation
        textArea.addValueChangeListener(event -> {
            if (question.isRequired() && event.getValue().trim().isEmpty()) {
                textArea.setInvalid(true);
            } else {
                textArea.setInvalid(false);
            }
        });
        
        // Add keyboard navigation (Ctrl+Enter to advance)
        textArea.addKeyDownListener(Key.ENTER, event -> {
            if (event.getModifiers().contains(KeyModifier.CONTROL)) {
                fireEvent(new QuestionNavigationEvent(this, false));
            }
        });
        
        return textArea;
    }

    private RadioButtonGroup<AnswerOptionDTO> createRadioButtons(QuestionDTO question) {
        RadioButtonGroup<AnswerOptionDTO> group = new RadioButtonGroup<>();
        group.setItems(question.getOptions());
        group.setItemLabelGenerator(AnswerOptionDTO::getText);
        group.setRequired(question.isRequired());
        group.setErrorMessage("Please select an option");
        group.addClassName("radio-group");
        
        // Make radio buttons more touch-friendly
        group.getElement().getStyle().set("--lumo-size-m", "2.5em");
        
        // Add ARIA attributes
        group.getElement().setAttribute("aria-required", 
            String.valueOf(question.isRequired()));
        
        // Add validation
        group.addValueChangeListener(event -> {
            group.setInvalid(question.isRequired() && event.getValue() == null);
        });
        // Add keyboard navigation
        group.getElement().executeJs(
            "this.addEventListener('keydown', function(event) {" +
            "  if (event.key === 'Enter') {" +
            "    event.preventDefault();" +
            "    this.dispatchEvent(new CustomEvent('navigation-next', {bubbles: true, composed: true}));" +
            "  }" +
            "});"
        );
        
        // Add listener for the custom event
        group.getElement().addEventListener("navigation-next", e -> 
            fireEvent(new QuestionNavigationEvent(this, false)));
        return group;
    }

    public String getAnswer() {
        if (answerComponent instanceof TextField) {
            return ((TextField) answerComponent).getValue();
        } else if (answerComponent instanceof TextArea) {
            return ((TextArea) answerComponent).getValue();
        } else if (answerComponent instanceof RadioButtonGroup) {
            @SuppressWarnings("unchecked")
            RadioButtonGroup<AnswerOptionDTO> group = (RadioButtonGroup<AnswerOptionDTO>) answerComponent;
            AnswerOptionDTO selectedOption = group.getValue();
            return selectedOption != null ? selectedOption.getText() : "";
        }
        return "";
    }

    public void setAnswer(String answer) {
        if (answer == null) return;
        
        if (answerComponent instanceof TextField) {
            ((TextField) answerComponent).setValue(answer);
        } else if (answerComponent instanceof TextArea) {
            ((TextArea) answerComponent).setValue(answer);
        } else if (answerComponent instanceof RadioButtonGroup) {
            @SuppressWarnings("unchecked")
            RadioButtonGroup<AnswerOptionDTO> group = (RadioButtonGroup<AnswerOptionDTO>) answerComponent;
            currentQuestion.getOptions().stream()
                .filter(option -> option.getText().equals(answer))
                .findFirst()
                .ifPresent(group::setValue);
        }
    }

    // Navigation event class
    public static class QuestionNavigationEvent extends ComponentEvent<QuestionComponent> {
        private final boolean previous;
        
        public QuestionNavigationEvent(QuestionComponent source, boolean previous) {
            super(source, false);
            this.previous = previous;
        }
        
        public boolean isPrevious() {
            return previous;
        }
    }

    // Add listener registration method
    public Registration addQuestionNavigationListener(
            ComponentEventListener<QuestionNavigationEvent> listener) {
        return addListener(QuestionNavigationEvent.class, listener);
    }
}
