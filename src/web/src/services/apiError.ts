import { isAxiosError } from 'axios';

const GENERIC_MESSAGE = 'Không thể thực hiện thao tác. Vui lòng thử lại.';
const FORBIDDEN_MESSAGE = 'Bạn không có quyền thực hiện thao tác này.';
const NETWORK_MESSAGE = 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.';
const FORBIDDEN_STATUS = 403;

interface ProblemBody {
  detail?: string;
  code?: string;
}

function readProblem(data: unknown): ProblemBody {
  if (typeof data !== 'object' || data === null) {
    return {};
  }
  const { detail, code } = data as Record<string, unknown>;
  return {
    detail: typeof detail === 'string' ? detail : undefined,
    code: typeof code === 'string' ? code : undefined,
  };
}

// Đọc lỗi RFC 7807 của backend (detail tiếng Việt, code PXVB-…) thành thông báo cho người dùng.
export function describeApiError(error: unknown): string {
  if (!isAxiosError(error)) {
    return GENERIC_MESSAGE;
  }
  if (!error.response) {
    return NETWORK_MESSAGE;
  }

  const { detail, code } = readProblem(error.response.data);
  if (detail) {
    return code ? `${detail} (${code})` : detail;
  }
  return error.response.status === FORBIDDEN_STATUS ? FORBIDDEN_MESSAGE : GENERIC_MESSAGE;
}
