import { formatDateTime } from '../../lib/format';
import { statusDisplay } from '../../lib/status';
import { StatusChip } from './StatusChip';

interface ControlPanelProps {
  owner: string;
  updatedAt: Date | null;
  version: number;
  status: string;
}

// Khối "Kiểm soát" (FR-MD-022): chỉ đọc. Thay đổi đi qua luồng duyệt của danh mục (D4).
export function ControlPanel({ owner, updatedAt, version, status }: ControlPanelProps) {
  const statusInfo = statusDisplay(status);
  return (
    <section className="control-panel" aria-label="Kiểm soát">
      <h2 className="control-panel__title">Kiểm soát</h2>
      <dl className="control-panel__list">
        <dt>Người phụ trách</dt>
        <dd>{owner}</dd>
        <dt>Ngày cập nhật</dt>
        <dd>{formatDateTime(updatedAt)}</dd>
        <dt>Phiên bản</dt>
        <dd>{version}</dd>
        <dt>Trạng thái</dt>
        <dd>
          <StatusChip tone={statusInfo.tone}>{statusInfo.label}</StatusChip>
        </dd>
      </dl>
      <p className="control-panel__note">Các trường này chỉ đọc.</p>
    </section>
  );
}
