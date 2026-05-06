import axios, { AxiosError, AxiosInstance, AxiosResponse } from 'axios';
import { 
  Campaign, 
  Question, 
  SurveyResponse, 
  QuestionAnswer,
  CreateCampaignRequest,
  UpdateCampaignRequest,
  CreateQuestionRequest,
  UpdateQuestionRequest,
  ApiResponse as ApiResponseType, 
  PaginatedResponse as PaginatedResponseType,
  CampaignAnalytics
} from '../types';
// Base API configuration
// Default to a relative URL so nginx (k8s/docker-compose) can proxy /api/ to
// the backend service on the same origin. For `npm start` dev mode, the
// `proxy` field in package.json forwards to the local backend on 18007.
// Override REACT_APP_API_URL at build time if you need to point elsewhere.
const API_BASE_URL = process.env.REACT_APP_API_URL || '/api';

// Create axios instance with default configuration
const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000, // 10 seconds
});

// Request interceptor
apiClient.interceptors.request.use(
  (config) => {
    // You can add auth tokens here if needed
    // const token = localStorage.getItem('token');
    // if (token) {
    //   config.headers.Authorization = `Bearer ${token}`;
    // }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor
apiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  (error: AxiosError) => {
    // Handle common error scenarios
    if (error.response) {
      // The request was made and the server responded with a status code
      // that falls out of the range of 2xx
      console.error('API Error Response:', error.response.data);
      
      // Handle specific status codes
      switch (error.response.status) {
        case 401:
          // Unauthorized - redirect to login or refresh token
          console.log('Unauthorized access, please login again');
          // Optional: redirect to login page
          break;
        case 403:
          // Forbidden - user doesn't have permission
          console.log('You do not have permission to access this resource');
          break;
        case 404:
          // Not found
          console.log('Resource not found');
          break;
        case 500:
          // Server error
          console.log('Server error occurred');
          break;
      }
    } else if (error.request) {
      // The request was made but no response was received
      console.error('No response received:', error.request);
    } else {
      // Something happened in setting up the request that triggered an Error
      console.error('Error setting up request:', error.message);
    }
    
    return Promise.reject(error);
  }
);

// Type definitions for API responses
// Using types defined in types/index.ts but with server-specific property names
interface ApiResponseServer<T> {
  data: T;
  message?: string;
  success: boolean;
}

interface PaginatedResponseServer<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

// Transform server response to client-expected format
const transformPaginatedResponse = <T>(response: PaginatedResponseServer<T>): PaginatedResponseType<T> => {
  return {
    items: response.content,
    total: response.totalElements,
    page: response.number,
    pageSize: response.size,
    totalPages: response.totalPages
  };
};

// Error handling utility
export class ApiError extends Error {
  status: number;
  data?: any;

  constructor(message: string, status: number, data?: any) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

// Convert Axios error to ApiError
export const handleApiError = (error: AxiosError): ApiError => {
  if (error.response) {
    // Try to extract message from different possible response formats
    const data = error.response.data as { message?: string; error?: string } | null | undefined;
    const message =
      typeof data === 'object' && data !== null
        ? data.message || data.error || 'An error occurred'
        : 'An error occurred';
        
    return new ApiError(
      message,
      error.response.status,
      error.response.data
    );
  }
  
  if (error.request) {
    return new ApiError('No response received from server. Please check your connection.', 0);
  }
  
  return new ApiError(error.message || 'Network error', 0);
};

// The backend (Spring Boot CampaignController, QuestionController,
// UserResponseController) returns DTOs / arrays directly — there is no
// {data, success, message} envelope and no Spring Pageable wrapper for
// the campaign/question routes. So we just hand back response.data.

// Campaign API functions
export const campaignApi = {
  // Get all campaigns
  getAll: async (): Promise<Campaign[]> => {
    try {
      const response = await apiClient.get<Campaign[]>('/campaigns');
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Get a single campaign by ID (questions come back inline on CampaignDTO)
  getById: async (id: number): Promise<Campaign> => {
    try {
      const response = await apiClient.get<Campaign>(`/campaigns/${id}`);
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  create: async (campaign: CreateCampaignRequest): Promise<Campaign> => {
    try {
      const response = await apiClient.post<Campaign>('/campaigns', campaign);
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  update: async (id: number, campaign: UpdateCampaignRequest): Promise<Campaign> => {
    try {
      const response = await apiClient.put<Campaign>(`/campaigns/${id}`, campaign);
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  delete: async (id: number): Promise<boolean> => {
    try {
      await apiClient.delete(`/campaigns/${id}`);
      return true;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Question CRUD lives at /api/questions, not nested under /campaigns
  addQuestion: async (campaignId: number, question: CreateQuestionRequest): Promise<Question> => {
    try {
      const response = await apiClient.post<Question>('/questions', { ...question, campaignId });
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  updateQuestion: async (campaignId: number, questionId: number, question: UpdateQuestionRequest): Promise<Question> => {
    try {
      const response = await apiClient.put<Question>(`/questions/${questionId}`, { ...question, campaignId });
      return response.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  deleteQuestion: async (_campaignId: number, questionId: number): Promise<boolean> => {
    try {
      await apiClient.delete(`/questions/${questionId}`);
      return true;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },
};

// Survey API functions
//
// The backend's UserResponseController stores ONE response per question
// (POST /api/responses with {questionId, userId, textResponse,
// selectedOptionId}). The frontend submits one SurveyResponse per survey
// with an answers[] array, so we fan it out — one POST per answer.
export const surveyApi = {
  submitResponse: async (response: SurveyResponse): Promise<SurveyResponse> => {
    try {
      const userId = response.userId ?? response.respondentId ?? 'anonymous';
      await Promise.all(
        response.answers.map((answer) =>
          apiClient.post('/responses', {
            questionId: answer.questionId,
            userId,
            textResponse: Array.isArray(answer.value)
              ? answer.value.join(', ')
              : String(answer.value),
          })
        )
      );
      return response;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // No /api/responses-by-campaign endpoint exists yet on the backend,
  // so we hand back an empty page rather than 404. ViewCampaign's
  // analytics renders gracefully with zero responses.
  getResponses: async (
    _campaignId: number,
    page = 0,
    size = 10
  ): Promise<PaginatedResponseType<SurveyResponse>> => {
    return { items: [], total: 0, page, pageSize: size, totalPages: 0 };
  },
};

export default {
  campaignApi,
  surveyApi
};

// Named-function aliases so pages can `import { createCampaign }` etc.
export const createCampaign = campaignApi.create;
export const getCampaign = campaignApi.getById;
export const getCampaignById = campaignApi.getById;
export const updateCampaign = campaignApi.update;
export const deleteCampaign = campaignApi.delete;
export const getCampaigns = campaignApi.getAll;
export const submitSurveyResponse = surveyApi.submitResponse;
export const getCampaignResponses = surveyApi.getResponses;

