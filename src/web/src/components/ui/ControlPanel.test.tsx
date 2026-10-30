import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ControlPanel } from './ControlPanel';

describe('ControlPanel (Kiểm soát)', () => {
  it('shows owner, update time, version and status with Vietnamese labels', () => {
    render(
      <ControlPanel
        owner="Phó giám đốc"
        updatedAt={new Date(2026, 9, 18, 15, 20)}
        version={3}
        status="ACTIVE"
      />,
    );

    const panel = screen.getByRole('region', { name: 'Kiểm soát' });
    expect(within(panel).getByText('Người phụ trách')).toBeInTheDocument();
    expect(within(panel).getByText('Phó giám đốc')).toBeInTheDocument();
    expect(within(panel).getByText('18/10/2026 15:20')).toBeInTheDocument();
    expect(within(panel).getByText('3')).toBeInTheDocument();
    expect(within(panel).getByText('Đang dùng')).toBeInTheDocument();
  });

  it('says the fields are read-only', () => {
    render(<ControlPanel owner="Phó giám đốc" updatedAt={null} version={1} status="DRAFT" />);

    expect(screen.getByText(/chỉ đọc/i)).toBeInTheDocument();
  });

  it('shows a dash for a missing update time', () => {
    render(<ControlPanel owner="Phó giám đốc" updatedAt={null} version={1} status="DRAFT" />);

    expect(screen.getByText('—')).toBeInTheDocument();
  });
});
