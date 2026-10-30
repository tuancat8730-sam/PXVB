import { useState } from 'react';
import { CatalogTable, type CatalogTableColumn } from '../../components/ui/CatalogTable';
import { ControlPanel } from '../../components/ui/ControlPanel';
import { StateNotice } from '../../components/ui/StateNotice';
import { StatusChip } from '../../components/ui/StatusChip';
import { useCatalogList } from '../../hooks/useCatalogList';
import {
  CYCLE_UNIT_LABELS,
  MAINT_TYPE_LABELS,
  RESULT_TYPE_LABELS,
  SKILL_LABELS,
  labelFor,
} from '../../lib/maintLabels';
import { readDate, readList, readNumber, readText } from '../../lib/record';
import { statusDisplay } from '../../lib/status';
import type { CatalogRecord } from '../../services/md/types';
import { VersionActions, versionsOf } from './VersionActions';

const RESOURCE = 'maint-task-templates';
const SEARCH_LABEL = 'Tìm theo mã hoặc tên công việc';

function cycleText(row: CatalogRecord): string {
  return `${readNumber(row, 'cycleValue')} ${labelFor(CYCLE_UNIT_LABELS, readText(row, 'cycleUnit'))}`;
}

function optionalBound(item: CatalogRecord, key: string): string {
  const value = item[key];
  return typeof value === 'number' ? String(value) : '—';
}

const COLUMNS: CatalogTableColumn<CatalogRecord>[] = [
  { key: 'code', label: 'Mã', render: (row) => readText(row, 'code') },
  { key: 'name', label: 'Tên', render: (row) => readText(row, 'name') },
  { key: 'maintType', label: 'Loại bảo trì', render: (row) => labelFor(MAINT_TYPE_LABELS, readText(row, 'maintType')) },
  { key: 'cycle', label: 'Chu kỳ', render: cycleText },
  { key: 'versionNo', label: 'Phiên bản', render: (row) => readNumber(row, 'versionNo') },
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

// U-4: công việc bảo trì chuẩn. Checklist và vật tư là chỉ đọc ở bản vẽ này; sửa nội dung làm sau.
export function MaintTemplatesScreen() {
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const list = useCatalogList(RESOURCE, query);
  const selected = list.rows.find((row) => rowId(row) === selectedId) ?? null;

  return (
    <section className="screen" aria-labelledby="maint-templates-title">
      <header className="screen-header">
        <p className="screen-crumb">Danh mục / Bảo trì chuẩn</p>
        <h1 id="maint-templates-title">Công việc bảo trì chuẩn</h1>
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
            <CatalogTable columns={COLUMNS} rows={list.rows} rowKey={rowId} selectedId={selectedId} onSelect={(row) => setSelectedId(rowId(row))} />
          )}
        </div>

        {selected && (
          <aside className="screen-side screen-side--wide">
            <ControlPanel
              owner={readText(selected, 'owner')}
              updatedAt={readDate(selected, 'updatedAt')}
              version={readNumber(selected, 'versionNo')}
              status={readText(selected, 'status')}
            />

            <div className="card side-card">
              <p className="side-text">
                Loại: {labelFor(MAINT_TYPE_LABELS, readText(selected, 'maintType'))} · Chu kỳ: {cycleText(selected)} · Kỹ năng:{' '}
                {labelFor(SKILL_LABELS, readText(selected, 'skillRequired'))}
              </p>

              <h2 className="side-title">Checklist</h2>
              <table className="catalog-table" aria-label="Checklist">
                <thead>
                  <tr>
                    <th scope="col">STT</th>
                    <th scope="col">Nội dung</th>
                    <th scope="col">Kiểu kết quả</th>
                    <th scope="col">Min</th>
                    <th scope="col">Max</th>
                  </tr>
                </thead>
                <tbody>
                  {readList(selected, 'checklist').map((item) => (
                    <tr key={readNumber(item, 'seq')}>
                      <td>{readNumber(item, 'seq')}</td>
                      <td>{readText(item, 'text')}</td>
                      <td>{labelFor(RESULT_TYPE_LABELS, readText(item, 'resultType'))}</td>
                      <td>{optionalBound(item, 'min')}</td>
                      <td>{optionalBound(item, 'max')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <h2 className="side-title">Vật tư dự kiến</h2>
              <table className="catalog-table" aria-label="Vật tư dự kiến">
                <thead>
                  <tr>
                    <th scope="col">Phụ tùng</th>
                    <th scope="col">Số lượng</th>
                  </tr>
                </thead>
                <tbody>
                  {readList(selected, 'parts').map((part) => (
                    <tr key={readText(part, 'partNo')}>
                      <td>{readText(part, 'partNo')}</td>
                      <td>{readNumber(part, 'qty')}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <VersionActions
                key={rowId(selected)}
                resource={RESOURCE}
                record={selected}
                versions={versionsOf(list.rows, readText(selected, 'code'))}
                onChanged={list.reload}
              />
            </div>
          </aside>
        )}
      </div>
    </section>
  );
}
