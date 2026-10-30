import { formatDateTime } from '../../lib/format';
import { StatusChip, type ChipTone } from './StatusChip';

interface ControlPanelProps {
  owner: string;
  updatedAt: Date | null;
  version: number;
  status: string;
}

const STATUS_LABELS: Record<string, { label: string; tone: ChipTone }> = {
  ACTIVE: { label: 'Đang dùng', tone: 'ok' },
  DRAFT: { label: 'Nháp', tone: 'warn' },
  RETIRED: { label: 'Đã ngừng', tone: 'off' },
};

function statusDisplay(status: string): { label: string; tone: ChipTone } {
  return STATUS_LABELS[status] ?? { label: status, tone: 'info' };
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
