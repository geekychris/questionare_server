import React, { useState, useEffect } from 'react';
import { 
  Box, 
  Typography, 
  Button, 
  Paper, 
  Stepper, 
  Step, 
  StepLabel, 
  CircularProgress, 
  Alert, 
  Container, 
  LinearProgress 
} from '@mui/material';
import { useParams, useNavigate } from 'react-router-dom';
import { Campaign, Question, QuestionType, SurveyResponse } from '../types';
import { getCampaignById, submitSurveyResponse } from '../services/api';

// Component to render a text question
const TextQuestion: React.FC<{
  question: Question;
  value: string;
  onChange: (value: string) => void;
  error?: string;
}> = ({ question, value, onChange, error }) => {
  return (
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" gutterBottom>
        {question.text} {question.required && <span style={{ color: 'red' }}>*</span>}
      </Typography>
      <textarea
        style={{
          width: '100%',
          padding: '10px',
          minHeight: '100px',
          borderRadius: '4px',
          border: error ? '1px solid red' : '1px solid #ccc',
        }}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="Enter your answer here..."
      />
      {error && (
        <Typography color="error" variant="caption" sx={{ mt: 0.5, display: 'block' }}>
          {error}
        </Typography>
      )}
    </Box>
  );
};

// Component to render a multiple choice question
const MultipleChoiceQuestion: React.FC<{
  question: Question;
  value: string;
  onChange: (value: string) => void;
  error?: string;
}> = ({ question, value, onChange, error }) => {
  return (
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" gutterBottom>
        {question.text} {question.required && <span style={{ color: 'red' }}>*</span>}
      </Typography>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {question.options?.map((option, index) => (
          <Box
            key={index}
            onClick={() => onChange(option)}
            sx={{
              display: 'flex',
              alignItems: 'center',
              padding: 1,
              border: '1px solid #ccc',
              borderRadius: 1,
              cursor: 'pointer',
              backgroundColor: value === option ? '#e3f2fd' : 'white',
              '&:hover': {
                backgroundColor: value === option ? '#e3f2fd' : '#f5f5f5',
              },
            }}
          >
            <Box
              sx={{
                width: 20,
                height: 20,
                borderRadius: '50%',
                border: '1px solid #1976d2',
                mr: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {value === option && (
                <Box
                  sx={{
                    width: 12,
                    height: 12,
                    borderRadius: '50%',
                    backgroundColor: '#1976d2',
                  }}
                />
              )}
            </Box>
            <Typography>{option}</Typography>
          </Box>
        ))}
      </Box>
      {error && (
        <Typography color="error" variant="caption" sx={{ mt: 0.5, display: 'block' }}>
          {error}
        </Typography>
      )}
    </Box>
  );
};

// Main component for taking a survey
const TakeSurvey: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  
  const [loading, setLoading] = useState<boolean>(true);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  
  const [campaign, setCampaign] = useState<Campaign | null>(null);
  const [activeStep, setActiveStep] = useState<number>(0);
  const [answers, setAnswers] = useState<Record<number, string>>({});
  const [validationErrors, setValidationErrors] = useState<Record<number, string>>({});
  
  // Fetch campaign data on component mount
  useEffect(() => {
    const fetchCampaign = async () => {
      try {
        setLoading(true);
        setError(null);
        
        if (!id) {
          throw new Error('Campaign ID is required');
        }
        
        const campaignData = await getCampaignById(parseInt(id));
        setCampaign(campaignData);
        
        // Initialize answers object
        const initialAnswers: Record<number, string> = {};
        campaignData.questions.forEach((question) => {
          initialAnswers[question.id] = '';
        });
        setAnswers(initialAnswers);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load survey');
      } finally {
        setLoading(false);
      }
    };
    
    fetchCampaign();
  }, [id]);
  
  // Calculate progress percentage
  const calculateProgress = () => {
    if (!campaign) return 0;
    const totalSteps = campaign.questions.length;
    if (totalSteps === 0) return 100;
    return Math.round((activeStep / totalSteps) * 100);
  };
  
  // Handle answer change for a question
  const handleAnswerChange = (questionId: number, value: string) => {
    setAnswers((prev) => ({
      ...prev,
      [questionId]: value,
    }));
    
    // Clear validation error when user enters a value
    if (validationErrors[questionId]) {
      setValidationErrors((prev) => {
        const newErrors = { ...prev };
        delete newErrors[questionId];
        return newErrors;
      });
    }
  };
  
  // Validate the current step
  const validateStep = (): boolean => {
    if (!campaign) return false;
    
    const currentQuestion = campaign.questions[activeStep];
    if (!currentQuestion) return true;
    
    const currentAnswer = answers[currentQuestion.id] || '';
    
    // Check if question is required but not answered
    if (currentQuestion.required && !currentAnswer.trim()) {
      setValidationErrors((prev) => ({
        ...prev,
        [currentQuestion.id]: 'This question is required',
      }));
      return false;
    }
    
    return true;
  };
  
  // Handle going to next step
  const handleNext = () => {
    if (validateStep()) {
      if (campaign && activeStep < campaign.questions.length - 1) {
        setActiveStep((prev) => prev + 1);
      } else {
        handleSubmit();
      }
    }
  };
  
  // Handle going to previous step
  const handleBack = () => {
    setActiveStep((prev) => Math.max(0, prev - 1));
  };
  
  // Handle survey submission
  const handleSubmit = async () => {
    if (!campaign) return;
    
    // Validate all questions before submission
    let isValid = true;
    const newErrors: Record<number, string> = {};
    
    campaign.questions.forEach((question) => {
      const answer = answers[question.id] || '';
      if (question.required && !answer.trim()) {
        newErrors[question.id] = 'This question is required';
        isValid = false;
      }
    });
    
    if (!isValid) {
      setValidationErrors(newErrors);
      // Find the first question with an error and navigate to it
      const firstErrorIndex = campaign.questions.findIndex(
        (q) => newErrors[q.id] !== undefined
      );
      if (firstErrorIndex !== -1) {
        setActiveStep(firstErrorIndex);
      }
      return;
    }
    
    // Prepare submission data
    const surveyResponse: SurveyResponse = {
      campaignId: campaign.id,
      responses: Object.entries(answers).map(([questionId, answer]) => ({
        questionId: parseInt(questionId),
        answer,
      })),
      submittedAt: new Date().toISOString(),
    };
    
    try {
      setSubmitting(true);
      setError(null);
      
      await submitSurveyResponse(surveyResponse);
      
      setSuccessMessage('Survey submitted successfully!');
      
      // Reset form after successful submission
      setTimeout(() => {
        navigate('/campaigns');
      }, 2000);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to submit survey');
    } finally {
      setSubmitting(false);
    }
  };
  
  // Render current question
  const renderQuestion = () => {
    if (!campaign) return null;
    
    const currentQuestion = campaign.questions[activeStep];
    if (!currentQuestion) return null;
    
    const currentAnswer = answers[currentQuestion.id] || '';
    const error = validationErrors[currentQuestion.id];
    
    if (currentQuestion.type === QuestionType.TEXT) {
      return (
        <TextQuestion
          question={currentQuestion}
          value={currentAnswer}
          onChange={(value) => handleAnswerChange(currentQuestion.id, value)}
          error={error}
        />
      );
    } else if (currentQuestion.type === QuestionType.MULTIPLE_CHOICE) {
      return (
        <MultipleChoiceQuestion
          question={currentQuestion}
          value={currentAnswer}
          onChange={(value) => handleAnswerChange(currentQuestion.id, value)}
          error={error}
        />
      );
    }
    
    return null;
  };
  
  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 10 }}>
        <CircularProgress />
      </Box>
    );
  }
  
  if (error && !campaign) {
    return (
      <Box sx={{ mt: 4, px: 2 }}>
        <Alert severity="error">{error}</Alert>
        <Button
          variant="contained"
          sx={{ mt: 2 }}
          onClick={() => navigate('/campaigns')}
        >
          Go Back to Campaigns
        </Button>
      </Box>
    );
  }
  
  if (successMessage) {
    return (
      <Box sx={{ mt: 4, px: 2 }}>
        <Alert severity="success">{successMessage}</Alert>
        <Button
          variant="contained"
          sx={{ mt: 2 }}
          onClick={() => navigate('/campaigns')}
        >
          Return to Campaigns
        </Button>
      </Box>
    );
  }
  
  if (!campaign) {
    return null;
  }
  
  return (
    <Container maxWidth="md">
      <Paper elevation={3} sx={{ p: 4, mt: 4, mb: 4 }}>
        {/* Survey Header */}
        <Box sx={{ mb: 4 }}>
          <Typography variant="h4" gutterBottom>
            {campaign.title}
          </Typography>
          <Typography variant="body1" color="textSecondary">
            {campaign.description}
          </Typography>
        </Box>
        
        {/* Progress Bar */}
        <Box sx={{ mb: 4 }}>
          <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
            <Typography variant="body2">
              Question {activeStep + 1} of {campaign.questions.length}
            </Typography>
            <Typography variant="body2">{calculateProgress()}% Complete</Typography>
          </Box>
          <LinearProgress
            variant="determinate"
            value={calculateProgress()}
            sx={{ height: 8, borderRadius: 4 }}
          />
        </Box>
        
        {/* Question Content */}
        <Box sx={{ mb: 4 }}>
          {renderQuestion()}
        </Box>
        
        {/* Navigation Buttons */}
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 4 }}>
          <Button
            variant="outlined"
            onClick={handleBack}
            disabled={activeStep === 0 || submitting}
          >
            Back
          </Button>
          <Button
            variant="contained"
            onClick={handleNext}
            disabled={submitting}
          >
            {activeStep === campaign.questions.length - 1 ? 'Submit' : 'Next'}
            {submitting && (
              <CircularProgress size={24} sx={{ ml: 1, color: 'white' }} />
            )}
          </Button>
        </Box>
      </Paper>
    </Container>
  );
};

export default TakeSurvey;

