import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getDataQualityReport } from '../../services/md/qualityApi';
import type { DataQualityReport } from '../../services/md/types';
import { DataQualityScreen } from './DataQualityScreen';

vi.mock('../../services/md/qualityApi', () => ({
  getDataQualityReport: vi.fn(),
}));

const mockedReport = vi.mocked(getDataQualityReport);

const REPORT: DataQualityReport = {
  generatedAt: '2026-10-30T08:00:00',
  hasErrors: true,
  issues: [
    {
      code: 'DEFECT_RULE_OVERLAP',
      severity: 'ERROR',
      message: 'Luật lỗi A/B/C chồng lấn khoảng hiệu lực',
      count: 2,
      samples: ['SD_DN · B', 'DG · B'],
    },
    {
      code: 'SPARE_PART_NO_SUPPLIER',
      severity: 'WARN',
      message: 'Phụ tùng chưa có nhà cung cấp mặc định',
      count: 3,
      samples: ['DB-03', 'BT-12', 'DC-01'],
    },
  ],
};

describe('DataQualityScreen (U-7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedReport.mockResolvedValue(REPORT);
  });

  it('shows each issue with its count and a severity label that is not only colour', async () => {
    render(<DataQualityScreen />);

    expect(await screen.findByRole('cell', { name: 'Nghiêm trọng' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Cần chú ý' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '2' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '3' })).toBeInTheDocument();
  });

  it('lists sample records of the chosen issue', async () => {
    render(<DataQualityScreen />);

    await userEvent.click(await screen.findByRole('button', { name: 'Phụ tùng chưa có nhà cung cấp mặc định' }));

    const samples = screen.getByRole('list', { name: 'Ví dụ bản ghi' });
    expect(within(samples).getByText('DB-03')).toBeInTheDocument();
    expect(within(samples).getByText('DC-01')).toBeInTheDocument();
  });

  it('scans again when the user asks for it', async () => {
    render(<DataQualityScreen />);
    await screen.findByRole('cell', { name: 'Nghiêm trọng' });

    await userEvent.click(screen.getByRole('button', { name: 'Quét lại' }));

    expect(mockedReport).toHaveBeenCalledTimes(2);
  });

  it('confirms when no issue is found', async () => {
    mockedReport.mockResolvedValue({ generatedAt: '2026-10-30T08:00:00', hasErrors: false, issues: [] });

    render(<DataQualityScreen />);

    expect(await screen.findByText('Không phát hiện vấn đề dữ liệu.')).toBeInTheDocument();
  });

  it('shows the error message when the report cannot be loaded', async () => {
    mockedReport.mockRejectedValue(new Error('boom'));

    render(<DataQualityScreen />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể thực hiện thao tác. Vui lòng thử lại.');
  });
});
