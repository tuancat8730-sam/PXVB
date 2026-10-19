import apiClient from './api';

export const getDashboardData = async (sessionId: string) => {
  const response = await apiClient.get(`/api/v1/pr/sessions/${sessionId}/dashboard`);
  return response.data;
};