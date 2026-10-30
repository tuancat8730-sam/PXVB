import { useEffect, useState } from 'react';
import { describeApiError } from '../services/apiError';

export type AsyncRows<T> =
  | { status: 'loading' }
  | { status: 'ready'; rows: T[] }
  | { status: 'error'; message: string };

const LOADING: AsyncRows<never> = { status: 'loading' };

// Tải danh sách theo một khóa (ví dụ id máy). Bỏ kết quả cũ khi khóa đổi.
export function useAsyncRows<T>(load: (id: string) => Promise<T[]>, id: string): AsyncRows<T> {
  const [state, setState] = useState<AsyncRows<T>>(LOADING);

  useEffect(() => {
    let isCurrent = true;
    setState(LOADING);

    load(id)
      .then((rows) => {
        if (isCurrent) {
          setState({ status: 'ready', rows });
        }
      })
      .catch((error: unknown) => {
        if (isCurrent) {
          setState({ status: 'error', message: describeApiError(error) });
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [load, id]);

  return state;
}
