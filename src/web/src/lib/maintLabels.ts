// Nhãn tiếng Việt cho mã của công việc bảo trì chuẩn (MaintTaskTemplate).

export const MAINT_TYPE_LABELS: Record<string, string> = {
  CLEANING: 'Vệ sinh',
  LUBRICATION: 'Bôi trơn',
  INSPECTION: 'Kiểm tra',
  PERIODIC: 'Định kỳ',
  OTHER: 'Khác',
};

export const CYCLE_UNIT_LABELS: Record<string, string> = {
  DAY: 'ngày',
  WEEK: 'tuần',
  MONTH: 'tháng',
  OP_HOURS: 'giờ vận hành',
};

export const RESULT_TYPE_LABELS: Record<string, string> = {
  OK_NG: 'OK / NG',
  NUMBER: 'Số',
  TEXT: 'Chữ',
};

export const SKILL_LABELS: Record<string, string> = {
  MECHANICAL: 'Cơ khí',
  ELECTRICAL: 'Điện',
  ANY: 'Bất kỳ',
};

// Mã không có trong bảng nhãn thì hiện nguyên mã, không làm mất thông tin.
export function labelFor(labels: Record<string, string>, code: string): string {
  return labels[code] ?? code;
}
