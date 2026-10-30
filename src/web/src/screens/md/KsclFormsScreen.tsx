import { useState } from 'react';
import { CatalogTable, type CatalogTableColumn } from '../../components/ui/CatalogTable';
import { ControlPanel } from '../../components/ui/ControlPanel';
import { StateNotice } from '../../components/ui/StateNotice';
import { StatusChip } from '../../components/ui/StatusChip';
import { useCatalogList } from '../../hooks/useCatalogList';
import type { ScreenProps } from '../../app/screenProps';
import { describeApiError } from '../../services/apiError';
import { activateVersion, cloneVersion } from '../../services/md/catalogApi';
import type { CatalogRecord } from '../../services/md/types';
import { readDate, readNumber, readText } from '../../lib/record';
import { statusDisplay } from '../../lib/status';

const RESOURCE = 'qc-form-templates';
const SEARCH_LABEL = 'Tìm theo mã hoặc tên biểu mẫu';
const MISSING_DATE_MESSAGE = 'Chọn ngày hiệu lực.';
const CLONED_MESSAGE = 'Đã nhân bản thành bản nháp mới.';
const ACTIVATED_MESSAGE = 'Đã kích hoạt phiên bản.';

// Loại biểu mẫu: mã backend (INLINE, QA) và nhãn hiển thị. Nhãn QA cần PXVB xác nhận.
const CATEGORY_LABELS: Record<string, string> = { INLINE: 'Trong ca', QA: 'Kiểm tra ĐBCL' };

const COLUMNS: CatalogTableColumn<CatalogRecord>[] = [
  { key: 'code', label: 'Mã', render: (row) => readText(row, 'code') },
  { key: 'name', label: 'Tên', render: (row) => readText(row, 'name') },
  { key: 'formGroup', label: 'Nhóm', render: (row) => readText(row, 'formGroup') },
  { key: 'category', label: 'Loại', render: (row) => CATEGORY_LABELS[readText(row, 'category')] ?? readText(row, 'category') },
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

function isDraft(row: CatalogRecord): boolean {
  return readText(row, 'status') === 'DRAFT';
}

// Các phiên bản cùng mã, mới nhất trước. Lấy từ danh sách đã tải, không gọi thêm API.
function versionsOf(rows: CatalogRecord[], code: string): CatalogRecord[] {
  return rows
    .filter((row) => readText(row, 'code') === code)
    .sort((left, right) => readNumber(right, 'versionNo') - readNumber(left, 'versionNo'));
}

// U-2: danh sách biểu mẫu KSCL và phiên bản. Bản đang dùng không sửa trực tiếp: nhân bản để sửa.
export function KsclFormsScreen({ onNavigate }: ScreenProps) {
  const [query, setQuery] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [effectiveFrom, setEffectiveFrom] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const list = useCatalogList(RESOURCE, query);
  const selected = list.rows.find((row) => rowId(row) === selectedId) ?? null;

  async function runAction(action: () => Promise<unknown>, successMessage: string) {
    setMessage(null);
    setActionError(null);
    try {
      await action();
      setMessage(successMessage);
      list.reload();
    } catch (error: unknown) {
      setActionError(describeApiError(error));
    }
  }

  function handleClone(row: CatalogRecord) {
    void runAction(() => cloneVersion(RESOURCE, rowId(row)), CLONED_MESSAGE);
  }

  function handleActivate(row: CatalogRecord) {
    if (effectiveFrom === '') {
      setMessage(null);
      setActionError(MISSING_DATE_MESSAGE);
      return;
    }
    void runAction(() => activateVersion(RESOURCE, rowId(row), effectiveFrom), ACTIVATED_MESSAGE);
  }

  return (
    <section className="screen" aria-labelledby="ksc-forms-title">
      <header className="screen-header">
        <p className="screen-crumb">Danh mục / Biểu mẫu KSCL</p>
        <h1 id="ksc-forms-title">Biểu mẫu KSCL/ĐBCL</h1>
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
              onSelect={(row) => {
                setSelectedId(rowId(row));
                setActionError(null);
                setMessage(null);
              }}
            />
          )}
        </div>

        {selected && (
          <aside className="screen-side">
            <ControlPanel
              owner={readText(selected, 'owner')}
              updatedAt={readDate(selected, 'updatedAt')}
              version={readNumber(selected, 'versionNo')}
              status={readText(selected, 'status')}
            />

            <div className="card side-card">
              <h2 className="side-title">Lịch sử phiên bản</h2>
              <ul aria-label="Lịch sử phiên bản" className="version-list">
                {versionsOf(list.rows, readText(selected, 'code')).map((row) => (
                  <li key={rowId(row)}>
                    v{readNumber(row, 'versionNo')} · {statusDisplay(readText(row, 'status')).label}
                  </li>
                ))}
              </ul>

              <div className="side-actions">
                {isDraft(selected) && (
                  <button type="button" className="btn-secondary" onClick={() => onNavigate('ksclDesigner', rowId(selected))}>
                    Thiết kế
                  </button>
                )}
                <button type="button" className="btn-secondary" onClick={() => handleClone(selected)}>
                  Nhân bản
                </button>
              </div>

              {isDraft(selected) && (
                <div className="activate-block">
                  <label>
                    Hiệu lực từ
                    <input type="date" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} />
                  </label>
                  <button type="button" className="btn-primary" onClick={() => handleActivate(selected)}>
                    Kích hoạt phiên bản
                  </button>
                </div>
              )}

              {message && <StateNotice kind="success" message={message} />}
              {actionError && <StateNotice kind="error" message={actionError} />}
            </div>
          </aside>
        )}
      </div>
    </section>
  );
}
