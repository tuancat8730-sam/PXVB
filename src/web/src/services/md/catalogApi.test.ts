import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import apiClient from '../api';
import {
  activateVersion,
  createRecord,
  getRecord,
  listRecords,
  updateRecord,
} from './catalogApi';

vi.mock('../api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
}));

interface MockedClient {
  get: Mock;
  post: Mock;
  put: Mock;
}

const mockedClient = apiClient as unknown as MockedClient;
const RECORD_ID = '0195a1b2-0000-7000-8000-000000000001';

describe('catalogApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('lists one page of a catalog with the search and paging parameters', async () => {
    // Arrange
    mockedClient.get.mockResolvedValue({ data: { content: [], page: 0, pageSize: 20, total: 0 } });

    // Act
    const page = await listRecords('spare-parts', { q: 'DC-01', page: 0, pageSize: 20 });

    // Assert
    expect(mockedClient.get).toHaveBeenCalledWith('/api/v1/md/spare-parts', {
      params: { q: 'DC-01', page: 0, pageSize: 20 },
    });
    expect(page.total).toBe(0);
  });

  it('reads one record by id', async () => {
    mockedClient.get.mockResolvedValue({ data: { id: RECORD_ID, partNo: 'DC-01' } });

    const record = await getRecord('spare-parts', RECORD_ID);

    expect(mockedClient.get).toHaveBeenCalledWith(`/api/v1/md/spare-parts/${RECORD_ID}`);
    expect(record.partNo).toBe('DC-01');
  });

  it('creates a record by posting its body to the catalog', async () => {
    mockedClient.post.mockResolvedValue({ data: { id: RECORD_ID } });

    await createRecord('spare-parts', { partNo: 'DC-01', name: 'Dao cắt', uom: 'cái' });

    expect(mockedClient.post).toHaveBeenCalledWith('/api/v1/md/spare-parts', {
      partNo: 'DC-01',
      name: 'Dao cắt',
      uom: 'cái',
    });
  });

  it('updates a record with a put to its own url', async () => {
    mockedClient.put.mockResolvedValue({ data: { id: RECORD_ID } });

    await updateRecord('spare-parts', RECORD_ID, { name: 'Dao cắt mới' });

    expect(mockedClient.put).toHaveBeenCalledWith(`/api/v1/md/spare-parts/${RECORD_ID}`, { name: 'Dao cắt mới' });
  });

  it('activates a version with its effective date', async () => {
    mockedClient.post.mockResolvedValue({ data: { id: RECORD_ID, status: 'ACTIVE' } });

    await activateVersion('qc-form-templates', RECORD_ID, '2026-11-01');

    expect(mockedClient.post).toHaveBeenCalledWith(
      `/api/v1/md/qc-form-templates/${RECORD_ID}/activate-version`,
      { effectiveFrom: '2026-11-01' },
    );
  });
});
