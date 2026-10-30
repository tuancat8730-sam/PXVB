import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createRecord, listRecords } from '../../services/md/catalogApi';
import type { CatalogRecord } from '../../services/md/types';
import { DefectRulesScreen } from './DefectRulesScreen';

vi.mock('../../services/md/catalogApi', () => ({
  listRecords: vi.fn(),
  createRecord: vi.fn(),
}));

const mockedList = vi.mocked(listRecords);
const mockedCreate = vi.mocked(createRecord);

const DEFECTS: CatalogRecord[] = [
  { id: 'd1', code: 'SD_DN', name: 'Sứt đầu điếu', appliesTo: 'ROD' },
  { id: 'd2', code: 'DG', name: 'Điếu bị gãy', appliesTo: 'ROD' },
  { id: 'd3', code: 'LK', name: 'Lỗi keo', appliesTo: 'PACK' },
];

const RULES: CatalogRecord[] = [
  {
    id: 'r1',
    defectCode: 'SD_DN',
    appliesTo: 'ROD',
    grade: 'B',
    metric: 'COUNT_IN_SAMPLE',
    thresholdValue: 1,
    sampleSize: 200,
    effectiveFrom: '2026-11-01',
    effectiveTo: null,
  },
  {
    id: 'r2',
    defectCode: 'DG',
    appliesTo: 'ROD',
    grade: 'B',
    metric: 'COUNT_IN_SAMPLE',
    thresholdValue: 1,
    sampleSize: 200,
    effectiveFrom: '2026-11-01',
    effectiveTo: '2026-12-31',
  },
  {
    id: 'r3',
    defectCode: 'DG',
    appliesTo: 'ROD',
    grade: 'B',
    metric: 'COUNT_IN_SAMPLE',
    thresholdValue: 1,
    sampleSize: 200,
    effectiveFrom: '2026-12-01',
    effectiveTo: null,
  },
];

function page(content: CatalogRecord[]) {
  return { content, page: 0, pageSize: 200, total: content.length };
}

describe('DefectRulesScreen (U-3)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedList.mockImplementation((resource: string) =>
      Promise.resolve(page(resource === 'defects' ? DEFECTS : RULES)),
    );
    mockedCreate.mockResolvedValue({ id: 'r9' });
  });

  it('shows the defects of the chosen group in rows with grades A, B and C as columns', async () => {
    render(<DefectRulesScreen />);

    expect(await screen.findByRole('columnheader', { name: 'Cấp A' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Cấp C' })).toBeInTheDocument();
    expect(screen.getByRole('rowheader', { name: 'Sứt đầu điếu' })).toBeInTheDocument();
    expect(screen.queryByRole('rowheader', { name: 'Lỗi keo' })).not.toBeInTheDocument();
  });

  it('describes the grade B rule in its cell and flags overlapping rules', async () => {
    render(<DefectRulesScreen />);

    expect(await screen.findByText('Đếm trong mẫu · ≥ 1')).toBeInTheDocument();
    expect(screen.getByText('Trùng hiệu lực (2 luật)')).toBeInTheDocument();
  });

  it('changes the group shown when another group is chosen', async () => {
    render(<DefectRulesScreen />);
    await screen.findByRole('rowheader', { name: 'Sứt đầu điếu' });

    await userEvent.selectOptions(screen.getByLabelText('Nhóm áp dụng'), 'PACK');

    expect(await screen.findByRole('rowheader', { name: 'Lỗi keo' })).toBeInTheDocument();
    expect(screen.queryByRole('rowheader', { name: 'Sứt đầu điếu' })).not.toBeInTheDocument();
  });

  it('does not allow saving grade A and explains why', async () => {
    render(<DefectRulesScreen />);
    await screen.findByRole('rowheader', { name: 'Sứt đầu điếu' });

    await userEvent.click(screen.getByRole('button', { name: 'Sứt đầu điếu, cấp A' }));

    expect(screen.getByRole('status')).toHaveTextContent('Cấp A và C chưa được định nghĩa');
    expect(screen.queryByRole('button', { name: 'Lưu luật' })).not.toBeInTheDocument();
  });

  it('saves a grade B rule with the sample size and effective date', async () => {
    render(<DefectRulesScreen />);
    await screen.findByRole('rowheader', { name: 'Điếu bị gãy' });
    await userEvent.click(screen.getByRole('button', { name: 'Điếu bị gãy, cấp B' }));

    await userEvent.clear(screen.getByLabelText('Cỡ mẫu'));
    await userEvent.type(screen.getByLabelText('Cỡ mẫu'), '300');
    await userEvent.type(screen.getByLabelText('Hiệu lực từ'), '2027-01-01');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu luật' }));

    await waitFor(() => expect(mockedCreate).toHaveBeenCalledTimes(1));
    expect(mockedCreate).toHaveBeenCalledWith('defect-grade-rules', {
      defectCode: 'DG',
      appliesTo: 'ROD',
      grade: 'B',
      metric: 'COUNT_IN_SAMPLE',
      thresholdValue: 1,
      sampleSize: 300,
      effectiveFrom: '2027-01-01',
    });
  });

  it('shows the server message when the rule cannot be saved', async () => {
    mockedCreate.mockRejectedValue(new Error('boom'));
    render(<DefectRulesScreen />);
    await screen.findByRole('rowheader', { name: 'Điếu bị gãy' });
    await userEvent.click(screen.getByRole('button', { name: 'Điếu bị gãy, cấp B' }));
    await userEvent.type(screen.getByLabelText('Hiệu lực từ'), '2027-01-01');

    await userEvent.click(screen.getByRole('button', { name: 'Lưu luật' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể thực hiện thao tác. Vui lòng thử lại.');
  });
});
