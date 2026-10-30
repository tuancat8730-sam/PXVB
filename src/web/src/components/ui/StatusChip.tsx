import type { ReactNode } from 'react';

export type ChipTone = 'ok' | 'bad' | 'warn' | 'off' | 'info';

interface StatusChipProps {
  tone: ChipTone;
  children: ReactNode;
}

// Hình dạng (tròn, tam giác, vòng) đi kèm màu, để trạng thái không chỉ dựa vào màu.
export function StatusChip({ tone, children }: StatusChipProps) {
  return <span className={`chip chip--${tone}`}>{children}</span>;
}
