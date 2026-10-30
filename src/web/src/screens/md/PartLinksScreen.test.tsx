import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { listRecords } from '../../services/md/catalogApi';
import { machinesOfPart, partsOfMachine } from '../../services/md/partLinkApi';
import type { CatalogRecord } from '../../services/md/types';
import { PartLinksScreen } from './PartLinksScreen';

vi.mock('../../services/md/catalogApi', () => ({
  listRecords: vi.fn(),
}));
vi.mock('../../services/md/partLinkApi', () => ({
  partsOfMachine: vi.fn(),
  machinesOfPart: vi.fn(),
}));

const mockedList = vi.mocked(listRecords);
const mockedParts = vi.mocked(partsOfMachine);
const mockedMachines = vi.mocked(machinesOfPart);

const MACHINES: CatalogRecord[] = [
  { id: 'mm1', code: 'VM-01', name: 'Vấn máy 01' },
  { id: 'mm2', code: 'VM-02', name: 'Vấn máy 02' },
];
const SPARE_PARTS: CatalogRecord[] = [
  { id: 'sp1', partNo: 'DC-01', name: 'Dao cắt' },
  { id: 'sp2', partNo: 'BT-12', name: 'Băng tải' },
];
const TEMPLATES: CatalogRecord[] = [{ id: 'mt1', code: 'BT-VS-DC', name: 'Vệ sinh dao cắt' }];

function page(content: CatalogRecord[]) {
  return { content, page: 0, pageSize: 200, total: content.length };
}

describe('PartLinksScreen (U-5)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedList.mockImplementation((resource: string) => {
      if (resource === 'machines') return Promise.resolve(page(MACHINES));
      if (resource === 'spare-parts') return Promise.resolve(page(SPARE_PARTS));
      return Promise.resolve(page(TEMPLATES));
    });
    mockedParts.mockResolvedValue([
      {
        linkId: 'l1',
        sparePartId: 'sp1',
        partNo: 'DC-01',
        partName: 'Dao cắt',
        componentId: 'c1',
        componentCode: 'CC',
        componentName: 'Cụm cấp giấy',
        taskTemplateId: 'mt1',
        typicalQty: 1,
        scope: 'MACHINE',
      },
      {
        linkId: 'l2',
        sparePartId: 'sp2',
        partNo: 'BT-12',
        partName: 'Băng tải',
        componentId: null,
        componentCode: null,
        componentName: null,
        taskTemplateId: null,
        typicalQty: null,
        scope: 'MODEL',
      },
    ]);
    mockedMachines.mockResolvedValue([
      { machineId: 'mm1', machineCode: 'VM-01', machineName: 'Vấn máy 01', scope: 'MACHINE' },
      { machineId: 'mm2', machineCode: 'VM-02', machineName: 'Vấn máy 02', scope: 'MODEL' },
    ]);
  });

  it('opens in the by-machine mode and shows the parts of the chosen machine', async () => {
    render(<PartLinksScreen />);

    expect(screen.getByRole('button', { name: 'Theo máy' })).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(await screen.findByRole('button', { name: 'VM-01' }));

    const table = await screen.findByRole('table', { name: 'Phụ tùng của máy' });
    expect(mockedParts).toHaveBeenCalledWith('mm1');
    expect(within(table).getByRole('cell', { name: 'DC-01' })).toBeInTheDocument();
    expect(within(table).getByRole('cell', { name: 'BT-VS-DC' })).toBeInTheDocument();
    expect(within(table).getByRole('cell', { name: 'Trực tiếp' })).toBeInTheDocument();
    expect(within(table).getByRole('cell', { name: 'Theo dòng máy' })).toBeInTheDocument();
  });

  it('switches to the by-part mode and shows every machine a part is linked to, in both directions', async () => {
    render(<PartLinksScreen />);

    await userEvent.click(screen.getByRole('button', { name: 'Theo phụ tùng' }));
    await userEvent.click(await screen.findByRole('button', { name: 'DC-01' }));

    const table = await screen.findByRole('table', { name: 'Máy dùng phụ tùng' });
    expect(mockedMachines).toHaveBeenCalledWith('sp1');
    expect(within(table).getByRole('cell', { name: 'VM-01' })).toBeInTheDocument();
    expect(within(table).getByRole('cell', { name: 'VM-02' })).toBeInTheDocument();
  });

  it('shows the server message when the links cannot be loaded', async () => {
    mockedParts.mockRejectedValue(new Error('boom'));
    render(<PartLinksScreen />);

    await userEvent.click(await screen.findByRole('button', { name: 'VM-01' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể thực hiện thao tác. Vui lòng thử lại.');
    await waitFor(() => expect(mockedParts).toHaveBeenCalledTimes(1));
  });
});
