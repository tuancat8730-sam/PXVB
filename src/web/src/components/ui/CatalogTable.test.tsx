import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CatalogTable, type CatalogTableColumn } from './CatalogTable';

interface PartRow {
  id: string;
  partNo: string;
  name: string;
}

const COLUMNS: CatalogTableColumn<PartRow>[] = [
  { key: 'partNo', label: 'Mã hàng', render: (row) => row.partNo },
  { key: 'name', label: 'Tên', render: (row) => row.name },
];

const ROWS: PartRow[] = [
  { id: 'p1', partNo: 'DC-01', name: 'Dao cắt' },
  { id: 'p2', partNo: 'DB-03', name: 'Dầu bôi trơn' },
];

describe('CatalogTable', () => {
  it('renders the column headers and one row per record', () => {
    render(<CatalogTable columns={COLUMNS} rows={ROWS} rowKey={(row) => row.id} selectedId={null} onSelect={vi.fn()} />);

    expect(screen.getByRole('columnheader', { name: 'Mã hàng' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Dầu bôi trơn' })).toBeInTheDocument();
  });

  it('reports the row the user chose', async () => {
    const onSelect = vi.fn();
    render(<CatalogTable columns={COLUMNS} rows={ROWS} rowKey={(row) => row.id} selectedId={null} onSelect={onSelect} />);

    await userEvent.click(screen.getByRole('button', { name: 'DB-03' }));

    expect(onSelect).toHaveBeenCalledWith(ROWS[1]);
  });

  it('marks the selected row as pressed for assistive technology', () => {
    render(<CatalogTable columns={COLUMNS} rows={ROWS} rowKey={(row) => row.id} selectedId="p1" onSelect={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'DC-01' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'DB-03' })).toHaveAttribute('aria-pressed', 'false');
  });
});
