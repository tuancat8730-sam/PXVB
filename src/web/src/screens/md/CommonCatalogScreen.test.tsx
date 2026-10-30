import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listRecords } from '../../services/md/catalogApi';
import { CommonCatalogScreen } from './CommonCatalogScreen';

vi.mock('../../services/md/catalogApi', () => ({
  listRecords: vi.fn(),
}));

const mockedList = vi.mocked(listRecords);

const PARTS = [
  {
    id: 'p1',
    partNo: 'DC-01',
    name: 'Dao cắt',
    stockCardNo: 'TK-0412',
    uom: 'cái',
    defaultSupplierId: 'Thép Bắc',
    owner: 'Phó giám đốc',
    updatedAt: '2026-10-18T15:20:00',
    version: 3,
    status: 'ACTIVE',
  },
  {
    id: 'p2',
    partNo: 'DB-03',
    name: 'Dầu bôi trơn',
    stockCardNo: 'TK-0530',
    uom: 'lít',
    defaultSupplierId: null,
    owner: 'Phó giám đốc',
    updatedAt: null,
    version: 1,
    status: 'INACTIVE',
  },
];

describe('CommonCatalogScreen (U-6)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedList.mockResolvedValue({ content: PARTS, page: 0, pageSize: 20, total: 2 });
  });

  it('shows spare parts with Vietnamese column headers', async () => {
    render(<CommonCatalogScreen />);

    expect(await screen.findByRole('columnheader', { name: 'Mã hàng' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Thẻ kho' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Dầu bôi trơn' })).toBeInTheDocument();
  });

  it('sends the search text to the spare-part catalog', async () => {
    render(<CommonCatalogScreen />);
    await screen.findByRole('cell', { name: 'Dao cắt' });

    await userEvent.type(screen.getByRole('searchbox', { name: 'Tìm theo mã hàng, tên hoặc thẻ kho' }), 'TK-0412');

    await waitFor(() => expect(mockedList).toHaveBeenLastCalledWith('spare-parts', { q: 'TK-0412' }));
  });

  it('shows the Kiểm soát panel of the chosen part', async () => {
    render(<CommonCatalogScreen />);
    await screen.findByRole('cell', { name: 'Dao cắt' });

    await userEvent.click(screen.getByRole('button', { name: 'DC-01' }));

    const panel = screen.getByRole('region', { name: 'Kiểm soát' });
    expect(within(panel).getByText('Phó giám đốc')).toBeInTheDocument();
    expect(within(panel).getByText('18/10/2026 15:20')).toBeInTheDocument();
    expect(within(panel).getByText('Đang dùng')).toBeInTheDocument();
  });

  it('shows the empty state when no part matches', async () => {
    mockedList.mockResolvedValue({ content: [], page: 0, pageSize: 20, total: 0 });

    render(<CommonCatalogScreen />);

    expect(await screen.findByText('Chưa có dữ liệu.')).toBeInTheDocument();
  });

  it('shows the error message when the list cannot be loaded', async () => {
    mockedList.mockRejectedValue(new Error('boom'));

    render(<CommonCatalogScreen />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể thực hiện thao tác. Vui lòng thử lại.');
  });
});
