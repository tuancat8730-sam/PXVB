import apiClient from '../api';
import type { DataQualityReport } from './types';

// Báo cáo chất lượng dữ liệu masterdata (FR-MD-021), chỉ đọc.
export async function getDataQualityReport(): Promise<DataQualityReport> {
  const response = await apiClient.get<DataQualityReport>('/api/v1/md/data-quality/report');
  return response.data;
}
