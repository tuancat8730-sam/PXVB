import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import apiClient from '../api';
import { getDataQualityReport } from './qualityApi';
import { machinesOfPart, partsOfMachine } from './partLinkApi';

vi.mock('../api', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn() },
}));

const mockedGet = apiClient.get as unknown as Mock;
const MACHINE_ID = '0195a1b2-0000-7000-8000-00000000000a';
const PART_ID = '0195a1b2-0000-7000-8000-00000000000b';

describe('partLinkApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('reads the parts linked to a machine', async () => {
    mockedGet.mockResolvedValue({ data: [{ partNo: 'DC-01' }] });

    const parts = await partsOfMachine(MACHINE_ID);

    expect(mockedGet).toHaveBeenCalledWith(`/api/v1/md/machines/${MACHINE_ID}/parts`);
    expect(parts).toEqual([{ partNo: 'DC-01' }]);
  });

  it('reads the machines linked to a spare part in both directions', async () => {
    mockedGet.mockResolvedValue({ data: [{ machineCode: 'VM-01' }] });

    const machines = await machinesOfPart(PART_ID);

    expect(mockedGet).toHaveBeenCalledWith(`/api/v1/md/spare-parts/${PART_ID}/machines`);
    expect(machines).toEqual([{ machineCode: 'VM-01' }]);
  });
});

describe('qualityApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('reads the data-quality report', async () => {
    mockedGet.mockResolvedValue({ data: { hasErrors: true, issues: [] } });

    const report = await getDataQualityReport();

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/md/data-quality/report');
    expect(report.hasErrors).toBe(true);
  });
});
