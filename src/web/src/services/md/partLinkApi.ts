import apiClient from '../api';
import type { MachineOfPart, PartOfMachine } from './types';

const MD_BASE_PATH = '/api/v1/md';

// Liên kết hai chiều (FR-MD-019): phụ tùng của một máy, và máy dùng một phụ tùng.
export async function partsOfMachine(machineId: string): Promise<PartOfMachine[]> {
  const response = await apiClient.get<PartOfMachine[]>(`${MD_BASE_PATH}/machines/${machineId}/parts`);
  return response.data;
}

export async function machinesOfPart(sparePartId: string): Promise<MachineOfPart[]> {
  const response = await apiClient.get<MachineOfPart[]>(`${MD_BASE_PATH}/spare-parts/${sparePartId}/machines`);
  return response.data;
}
