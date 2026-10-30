import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusChip } from './StatusChip';

describe('StatusChip', () => {
  it('shows its text so the state is not conveyed by colour alone', () => {
    render(<StatusChip tone="ok">Đang dùng</StatusChip>);

    expect(screen.getByText('Đang dùng')).toBeInTheDocument();
  });

  it('applies the class for its tone', () => {
    render(<StatusChip tone="warn">Nháp</StatusChip>);

    expect(screen.getByText('Nháp')).toHaveClass('chip', 'chip--warn');
  });
});
