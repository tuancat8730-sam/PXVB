import { renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listRecords } from '../services/md/catalogApi';
import { useCatalogList } from './useCatalogList';

vi.mock('../services/md/catalogApi', () => ({
  listRecords: vi.fn(),
}));

const mockedList = vi.mocked(listRecords);

describe('useCatalogList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads the rows of the catalog and reports ready', async () => {
    // Arrange
    mockedList.mockResolvedValue({ content: [{ partNo: 'DC-01' }], page: 0, pageSize: 20, total: 1 });

    // Act
    const { result } = renderHook(() => useCatalogList('spare-parts', ''));

    // Assert
    expect(result.current.status).toBe('loading');
    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(result.current.rows).toEqual([{ partNo: 'DC-01' }]);
  });

  it('passes the search text to the API', async () => {
    mockedList.mockResolvedValue({ content: [], page: 0, pageSize: 20, total: 0 });

    const { result } = renderHook(() => useCatalogList('spare-parts', 'DB'));

    await waitFor(() => expect(result.current.status).toBe('ready'));
    expect(mockedList).toHaveBeenCalledWith('spare-parts', { q: 'DB' });
  });

  it('reports an error with the message from the API layer', async () => {
    mockedList.mockRejectedValue(new Error('boom'));

    const { result } = renderHook(() => useCatalogList('spare-parts', ''));

    await waitFor(() => expect(result.current.status).toBe('error'));
    expect(result.current.errorMessage).toBe('Không thể thực hiện thao tác. Vui lòng thử lại.');
  });
});
