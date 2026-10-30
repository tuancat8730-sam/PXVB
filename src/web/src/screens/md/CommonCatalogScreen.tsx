import { useState } from 'react';
import { CatalogTable, type CatalogTableColumn } from '../../components/ui/CatalogTable';
import { ControlPanel } from '../../components/ui/ControlPanel';
import { StateNotice } from '../../components/ui/StateNotice';
import { StatusChip } from '../../components/ui/StatusChip';
import { useCatalogList } from '../../hooks/useCatalogList';
import { readDate, readNumber, readText } from '../../lib/record';
import { statusDisplay } from '../../lib/status';
import type { CatalogRecord } from '../../services/md/types';

const RESOURCE = 'spare-parts';
const SEARCH_LABEL = 'Tìm theo mã hàng, tên hoặc thẻ kho';

const COLUMNS: CatalogTableColumn<CatalogRecord>[] = [
  { key: 'partNo', label: 'Mã hàng', render: (row) => readText(row, 'partNo') },
  { key: 'name', label: 'Tên', render: (row) => readText(row, 'name') },
  { key: 'stockCardNo', label: 'Thẻ kho', render: (row) => readText(row, 'stockCardNo') },
  { key: 'uom', label: 'ĐVT', render: (row) => readText(row, 'uom') },
  { key: 'defaultSupplierId', label: 'NCC mặc định', render: (row) => readText(row, 'defaultSupplierId') || '—' },
  {
    key: 'status',
    label: 'Trạng thái',
    render: (row) => {
      const display = statusDisplay(readText(row, 'status'));
      return <StatusChip tone={display.tone}>{display.label}</StatusChip>;
    },
  },
];

function rowId(row: CatalogRecord): string {
  return readText(row, 'id');
}

// U-6: danh mục phụ tùng (dùng khung danh mục chung). Khối Kiểm soát chỉ đọc.
export function CommonCatalogScreen() {
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const list = useCatalogList(RESOURCE, query);
  const selected = list.rows.find((row) => rowId(row) === selectedId) ?? null;

  return (
    <section className="screen" aria-labelledby="common-catalog-title">
      <header className="screen-header">
        <p className="screen-crumb">Danh mục / Phụ tùng</p>
        <h1 id="common-catalog-title">Phụ tùng</h1>
      </header>

      <div className="screen-toolbar">
        <input
          type="search"
          className="search-input"
          aria-label={SEARCH_LABEL}
          placeholder={SEARCH_LABEL}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>

      <div className="screen-split">
        <div className="card screen-list">
          {list.status === 'loading' && <StateNotice kind="loading" />}
          {list.status === 'error' && <StateNotice kind="error" message={list.errorMessage ?? undefined} />}
          {list.status === 'ready' && list.rows.length === 0 && <StateNotice kind="empty" />}
          {list.status === 'ready' && list.rows.length > 0 && (
            <CatalogTable
              columns={COLUMNS}
              rows={list.rows}
              rowKey={rowId}
              selectedId={selectedId}
              onSelect={(row) => setSelectedId(rowId(row))}
            />
          )}
        </div>

        {selected && (
          <ControlPanel
            owner={readText(selected, 'owner')}
            updatedAt={readDate(selected, 'updatedAt')}
            version={readNumber(selected, 'version')}
            status={readText(selected, 'status')}
          />
        )}
      </div>
    </section>
  );
}
