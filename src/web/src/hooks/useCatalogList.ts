import { useEffect, useState } from 'react';
import { describeApiError } from '../services/apiError';
import { listRecords } from '../services/md/catalogApi';
import type { CatalogRecord } from '../services/md/types';

export type ListStatus = 'loading' | 'ready' | 'error';

export interface CatalogListState {
  status: ListStatus;
  rows: CatalogRecord[];
  errorMessage: string | null;
}

const LOADING_STATE: CatalogListState = { status: 'loading', rows: [], errorMessage: null };

// Tải danh sách một danh mục theo ô tìm kiếm. Bỏ kết quả cũ khi người dùng gõ tiếp.
export function useCatalogList(resource: string, query: string): CatalogListState {
  const [state, setState] = useState<CatalogListState>(LOADING_STATE);

  useEffect(() => {
    let isCurrent = true;
    setState(LOADING_STATE);

    listRecords(resource, query === '' ? {} : { q: query })
      .then((page) => {
        if (isCurrent) {
          setState({ status: 'ready', rows: page.content, errorMessage: null });
        }
      })
      .catch((error: unknown) => {
        if (isCurrent) {
          setState({ status: 'error', rows: [], errorMessage: describeApiError(error) });
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [resource, query]);

  return state;
}
