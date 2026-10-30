import { useEffect, useState } from 'react';
import { CatalogTable, type CatalogTableColumn } from '../../components/ui/CatalogTable';
import { StateNotice } from '../../components/ui/StateNotice';
import { StatusChip, type ChipTone } from '../../components/ui/StatusChip';
import { describeApiError } from '../../services/apiError';
import { getDataQualityReport } from '../../services/md/qualityApi';
import type { DataQualityIssue, DataQualityReport } from '../../services/md/types';

const SEVERITY_DISPLAY: Record<string, { label: string; tone: ChipTone }> = {
  ERROR: { label: 'Nghiêm trọng', tone: 'bad' },
  WARN: { label: 'Cần chú ý', tone: 'warn' },
  INFO: { label: 'Thông tin', tone: 'info' },
};
const NO_ISSUE_MESSAGE = 'Không phát hiện vấn đề dữ liệu.';

type ReportState = { status: 'loading' } | { status: 'ready'; report: DataQualityReport } | { status: 'error'; message: string };

const COLUMNS: CatalogTableColumn<DataQualityIssue>[] = [
  { key: 'message', label: 'Vấn đề', render: (issue) => issue.message },
  { key: 'code', label: 'Mã nhóm', render: (issue) => <span className="mono">{issue.code}</span> },
  { key: 'count', label: 'Số bản ghi', render: (issue) => issue.count },
  {
    key: 'severity',
    label: 'Mức',
    render: (issue) => {
      const display = SEVERITY_DISPLAY[issue.severity] ?? { label: issue.severity, tone: 'info' as const };
      return <StatusChip tone={display.tone}>{display.label}</StatusChip>;
    },
  },
];

// U-7: chất lượng dữ liệu masterdata (FR-MD-021). Chỉ đọc; "Quét lại" tải báo cáo mới.
export function DataQualityScreen() {
  const [reloadToken, setReloadToken] = useState(0);
  const [state, setState] = useState<ReportState>({ status: 'loading' });
  const [selectedCode, setSelectedCode] = useState<string | null>(null);

  useEffect(() => {
    let isCurrent = true;
    setState({ status: 'loading' });

    getDataQualityReport()
      .then((report) => {
        if (isCurrent) {
          setState({ status: 'ready', report });
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
  }, [reloadToken]);

  const issues = state.status === 'ready' ? state.report.issues : [];
  const selected = issues.find((issue) => issue.code === selectedCode) ?? null;

  return (
    <section className="screen" aria-labelledby="data-quality-title">
      <header className="screen-header">
        <p className="screen-crumb">Danh mục / Chất lượng dữ liệu</p>
        <h1 id="data-quality-title">Chất lượng dữ liệu danh mục</h1>
      </header>

      <div className="screen-toolbar">
        <button type="button" className="btn-secondary" onClick={() => setReloadToken((token) => token + 1)}>
          Quét lại
        </button>
      </div>

      <div className="screen-split">
        <div className="card screen-list">
          {state.status === 'loading' && <StateNotice kind="loading" />}
          {state.status === 'error' && <StateNotice kind="error" message={state.message} />}
          {state.status === 'ready' && issues.length === 0 && <StateNotice kind="success" message={NO_ISSUE_MESSAGE} />}
          {state.status === 'ready' && issues.length > 0 && (
            <CatalogTable
              columns={COLUMNS}
              rows={issues}
              rowKey={(issue) => issue.code}
              selectedId={selectedCode}
              onSelect={(issue) => setSelectedCode(issue.code)}
            />
          )}
        </div>

        {selected && (
          <aside className="card screen-side side-card">
            <h2 className="side-title">Ví dụ bản ghi</h2>
            <ul aria-label="Ví dụ bản ghi" className="version-list">
              {selected.samples.map((sample) => (
                <li key={sample}>{sample}</li>
              ))}
            </ul>
          </aside>
        )}
      </div>
    </section>
  );
}
