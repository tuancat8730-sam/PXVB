import { AxiosError, type AxiosResponse } from 'axios';
import { describe, expect, it } from 'vitest';
import { describeApiError } from './apiError';

function responseError(status: number, data: unknown): AxiosError {
  const response = { status, data, headers: {}, statusText: '', config: {} } as AxiosResponse;
  return new AxiosError('request failed', 'ERR_BAD_REQUEST', undefined, undefined, response);
}

describe('describeApiError', () => {
  it('shows the server detail in Vietnamese together with the error code', () => {
    const error = responseError(400, {
      detail: 'Cụm không thuộc máy đã chọn',
      code: 'PXVB-CC-0400',
    });

    expect(describeApiError(error)).toBe('Cụm không thuộc máy đã chọn (PXVB-CC-0400)');
  });

  it('falls back to a permission message for 403 without a detail', () => {
    expect(describeApiError(responseError(403, {}))).toBe('Bạn không có quyền thực hiện thao tác này.');
  });

  it('reports a connection problem when the server did not answer', () => {
    const error = new AxiosError('Network Error', 'ERR_NETWORK');

    expect(describeApiError(error)).toBe('Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.');
  });

  it('uses a generic message for errors that are not from the server', () => {
    expect(describeApiError(new Error('boom'))).toBe('Không thể thực hiện thao tác. Vui lòng thử lại.');
  });
});
