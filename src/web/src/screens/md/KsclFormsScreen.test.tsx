import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { activateVersion, cloneVersion, listRecords } from '../../services/md/catalogApi';
import type { CatalogRecord } from '../../services/md/types';
import { KsclFormsScreen } from './KsclFormsScreen';

vi.mock('../../services/md/catalogApi', () => ({
  listRecords: vi.fn(),
  cloneVersion: vi.fn(),
  activateVersion: vi.fn(),
}));

const mockedList = vi.mocked(listRecords);
const mockedClone = vi.mocked(cloneVersion);
const mockedActivate = vi.mocked(activateVersion);
const RESOURCE = 'qc-form-templates';

const FORMS = [
  { id: 'f3', code: 'B06.01', name: 'Khối lượng điếu', formGroup: 'B06', category: 'INLINE', versionNo: 3, status: 'ACTIVE', owner: 'QA', updatedAt: null },
  { id: 'f4', code: 'B06.01', name: 'Khối lượng điếu', formGroup: 'B06', category: 'INLINE', versionNo: 4, status: 'DRAFT', owner: 'QA', updatedAt: null },
  { id: 'g1', code: 'B08.01', name: 'Kiểm tra tem', formGroup: 'B08', category: 'INLINE', versionNo: 1, status: 'DRAFT', owner: 'QA', updatedAt: null },
];

function page(content: CatalogRecord[]) {
  return { content, page: 0, pageSize: 20, total: content.length };
}

describe('KsclFormsScreen (U-2)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedList.mockResolvedValue(page(FORMS));
    mockedClone.mockResolvedValue({ id: 'f5' });
    mockedActivate.mockResolvedValue({ id: 'f4', status: 'ACTIVE' });
  });

  it('lists forms with code, group, type, version and status', async () => {
    render(<KsclFormsScreen onNavigate={vi.fn()} recordId={null} />);

    expect(await screen.findByRole('columnheader', { name: 'Phiên bản' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Kiểm tra tem' })).toBeInTheDocument();
    expect(screen.getAllByText('Đang dùng').length).toBeGreaterThan(0);
  });

  it('shows the version history of the chosen form from the loaded list', async () => {
    render(<KsclFormsScreen onNavigate={vi.fn()} recordId={null} />);
    await screen.findByRole('cell', { name: 'Kiểm tra tem' });

    await userEvent.click(screen.getAllByRole('button', { name: 'B06.01' })[0]);

    const history = screen.getByRole('list', { name: 'Lịch sử phiên bản' });
    expect(within(history).getByText('v3 · Đang dùng')).toBeInTheDocument();
    expect(within(history).getByText('v4 · Nháp')).toBeInTheDocument();
  });

  it('opens the designer for a draft version', async () => {
    const onNavigate = vi.fn();
    render(<KsclFormsScreen onNavigate={onNavigate} recordId={null} />);
    await screen.findByRole('cell', { name: 'Kiểm tra tem' });

    await userEvent.click(screen.getByRole('button', { name: 'B08.01' }));
    await userEvent.click(screen.getByRole('button', { name: 'Thiết kế' }));

    expect(onNavigate).toHaveBeenCalledWith('ksclDesigner', 'g1');
  });

  it('clones the selected version and reloads the list', async () => {
    render(<KsclFormsScreen onNavigate={vi.fn()} recordId={null} />);
    await screen.findByRole('cell', { name: 'Kiểm tra tem' });
    await userEvent.click(screen.getByRole('button', { name: 'B08.01' }));

    await userEvent.click(screen.getByRole('button', { name: 'Nhân bản' }));

    await waitFor(() => expect(mockedClone).toHaveBeenCalledWith(RESOURCE, 'g1'));
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2));
  });

  it('activates a draft only with an effective date', async () => {
    render(<KsclFormsScreen onNavigate={vi.fn()} recordId={null} />);
    await screen.findByRole('cell', { name: 'Kiểm tra tem' });
    await userEvent.click(screen.getByRole('button', { name: 'B08.01' }));

    await userEvent.click(screen.getByRole('button', { name: 'Kích hoạt phiên bản' }));

    expect(mockedActivate).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('Chọn ngày hiệu lực.');
  });

  it('activates the draft from the chosen effective date', async () => {
    render(<KsclFormsScreen onNavigate={vi.fn()} recordId={null} />);
    await screen.findByRole('cell', { name: 'Kiểm tra tem' });
    await userEvent.click(screen.getByRole('button', { name: 'B08.01' }));

    await userEvent.type(screen.getByLabelText('Hiệu lực từ'), '2026-11-01');
    await userEvent.click(screen.getByRole('button', { name: 'Kích hoạt phiên bản' }));

    await waitFor(() => expect(mockedActivate).toHaveBeenCalledWith(RESOURCE, 'g1', '2026-11-01'));
  });

  it('shows the server message when cloning fails', async () => {
    mockedClone.mockRejectedValue(new Error('boom'));
    render(<KsclFormsScreen onNavigate={vi.fn()} recordId={null} />);
    await screen.findByRole('cell', { name: 'Kiểm tra tem' });
    await userEvent.click(screen.getByRole('button', { name: 'B08.01' }));

    await userEvent.click(screen.getByRole('button', { name: 'Nhân bản' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể thực hiện thao tác. Vui lòng thử lại.');
  });
});
