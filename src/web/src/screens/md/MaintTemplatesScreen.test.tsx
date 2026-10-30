import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { activateVersion, cloneVersion, listRecords } from '../../services/md/catalogApi';
import type { CatalogRecord } from '../../services/md/types';
import { MaintTemplatesScreen } from './MaintTemplatesScreen';

vi.mock('../../services/md/catalogApi', () => ({
  listRecords: vi.fn(),
  cloneVersion: vi.fn(),
  activateVersion: vi.fn(),
}));

const mockedList = vi.mocked(listRecords);
const mockedActivate = vi.mocked(activateVersion);
const RESOURCE = 'maint-task-templates';

const TEMPLATES: CatalogRecord[] = [
  {
    id: 'm1',
    code: 'BT-VS-DC',
    name: 'Vệ sinh dao cắt',
    maintType: 'CLEANING',
    cycleValue: 7,
    cycleUnit: 'DAY',
    skillRequired: 'MECHANICAL',
    owner: 'KTCD',
    updatedAt: null,
    versionNo: 1,
    status: 'DRAFT',
    checklist: [
      { seq: 1, text: 'Vệ sinh khu vực dao cắt', resultType: 'OK_NG' },
      { seq: 2, text: 'Đo độ mòn lưỡi', resultType: 'NUMBER', min: 0, max: 0.05 },
    ],
    parts: [{ partNo: 'DC-01', qty: 1 }],
  },
  {
    id: 'm2',
    code: 'BT-BT-01',
    name: 'Bôi trơn băng tải',
    maintType: 'LUBRICATION',
    cycleValue: 2,
    cycleUnit: 'OP_HOURS',
    skillRequired: 'ANY',
    owner: 'KTCD',
    updatedAt: null,
    versionNo: 2,
    status: 'ACTIVE',
    checklist: [],
    parts: [],
  },
];

function page(content: CatalogRecord[]) {
  return { content, page: 0, pageSize: 20, total: content.length };
}

describe('MaintTemplatesScreen (U-4)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedList.mockResolvedValue(page(TEMPLATES));
    mockedActivate.mockResolvedValue({ id: 'm1', status: 'ACTIVE' });
  });

  it('lists templates with type, cycle, version and status', async () => {
    render(<MaintTemplatesScreen />);

    expect(await screen.findByRole('columnheader', { name: 'Chu kỳ' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Vệ sinh' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Bôi trơn' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '7 ngày' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '2 giờ vận hành' })).toBeInTheDocument();
  });

  it('shows the checklist with result types in Vietnamese', async () => {
    render(<MaintTemplatesScreen />);
    await screen.findByRole('cell', { name: 'Vệ sinh' });

    await userEvent.click(screen.getByRole('button', { name: 'BT-VS-DC' }));

    const checklist = screen.getByRole('table', { name: 'Checklist' });
    expect(within(checklist).getByRole('cell', { name: 'Vệ sinh khu vực dao cắt' })).toBeInTheDocument();
    expect(within(checklist).getByRole('cell', { name: 'OK / NG' })).toBeInTheDocument();
    expect(within(checklist).getByRole('cell', { name: 'Số' })).toBeInTheDocument();
  });

  it('shows the planned materials with their quantity', async () => {
    render(<MaintTemplatesScreen />);
    await screen.findByRole('cell', { name: 'Vệ sinh' });

    await userEvent.click(screen.getByRole('button', { name: 'BT-VS-DC' }));

    const materials = screen.getByRole('table', { name: 'Vật tư dự kiến' });
    expect(within(materials).getByRole('cell', { name: 'DC-01' })).toBeInTheDocument();
    expect(within(materials).getByRole('cell', { name: '1' })).toBeInTheDocument();
  });

  it('activates a draft from the chosen effective date and reloads the list', async () => {
    render(<MaintTemplatesScreen />);
    await screen.findByRole('cell', { name: 'Vệ sinh' });
    await userEvent.click(screen.getByRole('button', { name: 'BT-VS-DC' }));

    await userEvent.type(screen.getByLabelText('Hiệu lực từ'), '2026-11-01');
    await userEvent.click(screen.getByRole('button', { name: 'Kích hoạt phiên bản' }));

    await waitFor(() => expect(mockedActivate).toHaveBeenCalledWith(RESOURCE, 'm1', '2026-11-01'));
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
  });

  it('shows the error when the list cannot be loaded', async () => {
    mockedList.mockRejectedValue(new Error('boom'));

    render(<MaintTemplatesScreen />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể thực hiện thao tác. Vui lòng thử lại.');
  });
});
