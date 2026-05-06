/**
 * ----------------------------------------
 * Enums
 * ----------------------------------------
 */

/**
 * Enum representing question types
 */
export enum QuestionType {
  TEXT = 'TEXT',
  MULTIPLE_CHOICE = 'MULTIPLE_CHOICE',
  SINGLE_CHOICE = 'SINGLE_CHOICE',
  RATING = 'RATING',
  YES_NO = 'YES_NO'
}

/**
 * ----------------------------------------
 * Campaign & Question Interfaces
 * ----------------------------------------
 */

/**
 * Interface for a single option in multiple/single choice questions
 */
export interface QuestionOption {
  id: number;
  text: string;
  order: number;
}

/**
 * Interface representing a survey question
 */
export interface Question {
  id: number;
  campaignId: number; // Reference to parent campaign
  text: string; // Question text
  type: QuestionType;
  required: boolean;
  order: number; // For ordering questions in the survey
  options?: QuestionOption[]; // Options for choice questions
  minValue?: number; // For rating questions
  maxValue?: number; // For rating questions
  validationRules?: {
    minLength?: number;
    maxLength?: number;
    pattern?: string;
  };
}

/**
 * Interface representing a campaign/survey
 */
export interface Campaign {
  id: number;
  title: string;
  description: string;
  startDate: string;
  endDate: string;
  active: boolean;
  questions: Question[];
  createdAt: string;
  updatedAt: string;
}

/**
 * Interface for campaign analytics data
 */
export interface CampaignAnalytics {
  totalResponses: number;
  completionRate: number; // Percentage of users who completed the survey
  averageTimeToComplete: number; // In seconds
  questionStats: Array<{
    questionId: number;
    questionText: string;
    responseCount: number;
    responseRate: number; // Percentage of users who answered this question
    optionBreakdown?: Record<string, number>; // For choice questions: option text -> count
    averageRating?: number; // For rating questions
    textResponses?: string[]; // For text questions, sample of responses
  }>;
  demographicBreakdown?: Record<string, Record<string, number>>; // Optional demographic data
  deviceStats?: {
    desktop: number;
    mobile: number;
    tablet: number;
    other: number;
  };
  completionTrends?: {
    daily: Record<string, number>; // date string -> count
    hourly: Record<string, number>; // hour string -> count
  };
}

/**
 * ----------------------------------------
 * Survey Response Interfaces
 * ----------------------------------------
 */

/**
 * Interface for an answer to a question
 */
export interface Answer {
  questionId: number;
  value: string | string[] | number; // Text, choice IDs, or rating value
}

/**
 * Interface representing a survey response
 */
export interface SurveyResponse {
  id?: number;
  campaignId: number;
  answers: Answer[];
  submittedAt: string;
  startTime?: string;
  endTime?: string;
  userId?: string;
  respondentId?: string;
}

/**
 * Interface for question answer in a survey response (legacy)
 */
export interface QuestionAnswer {
  questionId: number;
  value: string;
}

/**
 * ----------------------------------------
 * API Request & Response Interfaces
 * ----------------------------------------
 */

/**
 * Generic API response
 */
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: string;
  message?: string;
}

/**
 * Paginated response for lists
 */
export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

/**
 * Interface for API errors
 */
export interface ApiError {
  statusCode: number;
  message: string;
  details?: any;
}

/**
 * ----------------------------------------
 * Campaign Management Request Interfaces
 * ----------------------------------------
 */

/**
 * Interface for creating a new campaign
 */
export interface CreateCampaignRequest {
  title: string;
  description: string;
  active: boolean;
  startDate: string;
  endDate: string;
  questions: Omit<Question, 'id' | 'campaignId'>[];
}

/**
 * Interface for updating an existing campaign
 */
export interface UpdateCampaignRequest {
  id: number;
  title?: string;
  description?: string;
  startDate?: string;
  endDate?: string;
  active?: boolean;
}

/**
 * Interface for deleting a campaign
 */
export interface DeleteCampaignRequest {
  id: number;
}

/**
 * ----------------------------------------
 * Question Management Request Interfaces
 * ----------------------------------------
 */

/**
 * Interface for creating a question
 */
export interface CreateQuestionRequest {
  text: string;
  type: QuestionType;
  required: boolean;
  order: number;
  options?: string[];
}

/**
 * Interface for updating a question
 */
export interface UpdateQuestionRequest extends CreateQuestionRequest {
  id: number;
}

/**
 * Interface for deleting a question
 */
export interface DeleteQuestionRequest {
  id: number;
}

/**
 * ----------------------------------------
 * Survey Response Management Interfaces
 * ----------------------------------------
 */

/**
 * Interface for submitting a survey response
 */
export interface SubmitSurveyResponseRequest {
  campaignId: number;
  answers: Answer[];
  userId?: string;
}

/**
 * ----------------------------------------
 * Form State Management Interfaces
 * ----------------------------------------
 */

/**
 * Interface for campaign form state
 */
export interface CampaignFormState {
  campaign: Omit<Campaign, 'id' | 'createdAt' | 'updatedAt' | 'questions'>;
  questions: Array<Omit<Question, 'id' | 'campaignId'>>;
  errors: {
    campaign?: Record<string, string>;
    questions?: Record<number, Record<string, string>>;
  };
  isSubmitting: boolean;
  isValid: boolean;
}

/**
 * Interface for the survey form state
 */
export interface SurveyFormState {
  campaignId: number;
  answers: Answer[];
  errors: Record<number, string>;
  isSubmitting: boolean;
  isValid: boolean;
  currentPage: number;
  totalPages: number;
}

/**
 * ----------------------------------------
 * UI State Interfaces
 * ----------------------------------------
 */

/**
 * Interface for UI notification types
 */
export type NotificationType = 'success' | 'error' | 'info' | 'warning';

/**
 * Interface for application UI state
 */
export interface UiState {
  isLoading: boolean;
  notification: {
    open: boolean;
    message: string;
    type: NotificationType;
    duration?: number;
    anchorOrigin?: {
      vertical: 'top' | 'bottom';
      horizontal: 'left' | 'center' | 'right';
    };
  };
  theme: 'light' | 'dark';
  sidebarOpen: boolean;
  modalState: {
    [key: string]: {
      open: boolean;
      data?: any;
    };
  };
  currentView: string;
  breadcrumbs: Array<{
    text: string;
    path?: string;
  }>;
  filters: Record<string, any>;
  pagination: {
    page: number;
    pageSize: number;
    totalItems: number;
    totalPages: number;
  };
  sorting: {
    field: string;
    direction: 'asc' | 'desc';
  };
}
