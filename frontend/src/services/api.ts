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
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api';

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
    const message = 
      typeof error.response.data === 'object' && error.response.data !== null
        ? error.response.data.message || error.response.data.error || 'An error occurred'
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

// Campaign API functions
export const campaignApi = {
  // Get all campaigns with optional pagination and filters
  getAll: async (page = 0, size = 10, filters?: { active?: boolean, title?: string }): Promise<PaginatedResponseType<Campaign>> => {
    try {
      const response = await apiClient.get<PaginatedResponseServer<Campaign>>('/campaigns', {
        params: { page, size, ...filters }
      });
      return transformPaginatedResponse(response.data);
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Get a single campaign by ID
  getById: async (id: number): Promise<Campaign> => {
    try {
      const response = await apiClient.get<ApiResponseServer<Campaign>>(`/campaigns/${id}`);
      return response.data.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Create a new campaign
  create: async (campaign: CreateCampaignRequest): Promise<Campaign> => {
    try {
      const response = await apiClient.post<ApiResponseServer<Campaign>>('/campaigns', campaign);
      return response.data.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Update an existing campaign
  update: async (id: number, campaign: UpdateCampaignRequest): Promise<Campaign> => {
    try {
      const response = await apiClient.put<ApiResponseServer<Campaign>>(`/campaigns/${id}`, campaign);
      return response.data.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Delete a campaign
  delete: async (id: number): Promise<boolean> => {
    try {
      await apiClient.delete(`/campaigns/${id}`);
      return true;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Add a question to a campaign
  addQuestion: async (campaignId: number, question: CreateQuestionRequest): Promise<Question> => {
    try {
      const response = await apiClient.post<ApiResponseServer<Question>>(
        `/campaigns/${campaignId}/questions`,
        question
      );
      return response.data.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Update a question in a campaign
  updateQuestion: async (campaignId: number, questionId: number, question: UpdateQuestionRequest): Promise<Question> => {
    try {
      const response = await apiClient.put<ApiResponseServer<Question>>(
        `/campaigns/${campaignId}/questions/${questionId}`,
        question
      );
      return response.data.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Delete a question from a campaign
  deleteQuestion: async (campaignId: number, questionId: number): Promise<boolean> => {
    try {
      await apiClient.delete(`/campaigns/${campaignId}/questions/${questionId}`);
      return true;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Reorder questions in a campaign
  reorderQuestions: async (campaignId: number, questionIds: number[]): Promise<boolean> => {
    try {
      await apiClient.post(`/campaigns/${campaignId}/questions/reorder`, { questionIds });
      return true;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  }
};

// Survey API functions
export const surveyApi = {
  // Submit responses for a survey
  submitResponse: async (response: SurveyResponse): Promise<SurveyResponse> => {
    try {
      const result = await apiClient.post<ApiResponseServer<SurveyResponse>>(
        `/surveys/${response.campaignId}/responses`, 
        response
      );
      return result.data.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Get survey responses for a campaign (admin only)
  getResponses: async (campaignId: number, page = 0, size = 10): Promise<PaginatedResponseType<SurveyResponse>> => {
    try {
      const response = await apiClient.get<PaginatedResponseServer<SurveyResponse>>(
        `/surveys/${campaignId}/responses`,
        { params: { page, size } }
      );
      return transformPaginatedResponse(response.data);
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },

  // Get a single survey response
  getResponseById: async (responseId: number): Promise<SurveyResponse> => {
    try {
      const response = await apiClient.get<ApiResponseServer<SurveyResponse>>(`/surveys/responses/${responseId}`);
      return response.data.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  },
  
  // Get survey analytics for a campaign
  getAnalytics: async (campaignId: number): Promise<CampaignAnalytics> => {
    try {
      const response = await apiClient.get<ApiResponseServer<CampaignAnalytics>>(
        `/surveys/${campaignId}/analytics`
      );
      return response.data.data;
    } catch (error) {
      throw handleApiError(error as AxiosError);
    }
  }
};

export default {
  campaignApi,
  surveyApi
};

