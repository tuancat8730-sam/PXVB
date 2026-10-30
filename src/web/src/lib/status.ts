import type { ChipTone } from '../components/ui/StatusChip';

export interface StatusDisplay {
  label: string;
  tone: ChipTone;
}

// Mã trạng thái của backend (UPPER_SNAKE) → nhãn tiếng Việt và tông màu.
const STATUS_DISPLAYS: Record<string, StatusDisplay> = {
  ACTIVE: { label: 'Đang dùng', tone: 'ok' },
  INACTIVE: { label: 'Ngừng dùng', tone: 'off' },
  DRAFT: { label: 'Nháp', tone: 'warn' },
  RETIRED: { label: 'Đã ngừng', tone: 'off' },
};

const UNKNOWN_STATUS: StatusDisplay = { label: 'Không rõ', tone: 'info' };

export function statusDisplay(status: string): StatusDisplay {
  return STATUS_DISPLAYS[status] ?? UNKNOWN_STATUS;
}
