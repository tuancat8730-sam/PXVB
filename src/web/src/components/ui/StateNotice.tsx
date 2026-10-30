export type NoticeKind = 'loading' | 'empty' | 'error' | 'partial' | 'noperm' | 'success';

interface StateNoticeProps {
  kind: NoticeKind;
  message?: string;
}

interface NoticeDefinition {
  defaultText: string;
  role: 'status' | 'alert';
}

const NOTICES: Record<NoticeKind, NoticeDefinition> = {
  loading: { defaultText: 'Đang tải dữ liệu…', role: 'status' },
  empty: { defaultText: 'Chưa có dữ liệu.', role: 'status' },
  error: { defaultText: 'Không tải được dữ liệu. Vui lòng thử lại.', role: 'alert' },
  partial: { defaultText: 'Dữ liệu chưa đầy đủ.', role: 'status' },
  noperm: { defaultText: 'Bạn không có quyền xem màn hình này.', role: 'alert' },
  success: { defaultText: 'Đã lưu.', role: 'status' },
};

// Sáu trạng thái nội dung theo DESIGN-SYSTEM.md §5. Mỗi trạng thái có vai trò ARIA riêng.
export function StateNotice({ kind, message }: StateNoticeProps) {
  const notice = NOTICES[kind];
  return (
    <div className={`state-notice state-notice--${kind}`} role={notice.role}>
      {message ?? notice.defaultText}
    </div>
  );
}
