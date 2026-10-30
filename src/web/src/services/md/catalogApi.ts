import apiClient from '../api';
import type { CatalogRecord, ListParams, PageResponse } from './types';

const MD_BASE_PATH = '/api/v1/md';

function resourcePath(resource: string): string {
  return `${MD_BASE_PATH}/${resource}`;
}

export async function listRecords(resource: string, params: ListParams = {}): Promise<PageResponse<CatalogRecord>> {
  const response = await apiClient.get<PageResponse<CatalogRecord>>(resourcePath(resource), { params });
  return response.data;
}

export async function getRecord(resource: string, id: string): Promise<CatalogRecord> {
  const response = await apiClient.get<CatalogRecord>(`${resourcePath(resource)}/${id}`);
  return response.data;
}

export async function createRecord(resource: string, body: CatalogRecord): Promise<CatalogRecord> {
  const response = await apiClient.post<CatalogRecord>(resourcePath(resource), body);
  return response.data;
}

export async function updateRecord(resource: string, id: string, body: CatalogRecord): Promise<CatalogRecord> {
  const response = await apiClient.put<CatalogRecord>(`${resourcePath(resource)}/${id}`, body);
  return response.data;
}

export async function cloneVersion(resource: string, id: string): Promise<CatalogRecord> {
  const response = await apiClient.post<CatalogRecord>(`${resourcePath(resource)}/${id}/clone`);
  return response.data;
}

export async function activateVersion(resource: string, id: string, effectiveFrom: string): Promise<CatalogRecord> {
  const response = await apiClient.post<CatalogRecord>(`${resourcePath(resource)}/${id}/activate-version`, {
    effectiveFrom,
  });
  return response.data;
}
