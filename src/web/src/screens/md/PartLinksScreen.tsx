import { useState } from 'react';
import { CatalogTable, type CatalogTableColumn } from '../../components/ui/CatalogTable';
import { StateNotice } from '../../components/ui/StateNotice';
import { useAsyncRows } from '../../hooks/useAsyncRows';
import { useCatalogList } from '../../hooks/useCatalogList';
import { readText } from '../../lib/record';
import { machinesOfPart, partsOfMachine } from '../../services/md/partLinkApi';
import type { CatalogRecord, MachineOfPart, PartOfMachine } from '../../services/md/types';

type LinkMode = 'machine' | 'part';

const MODE_LABELS: Record<LinkMode, string> = { machine: 'Theo máy', part: 'Theo phụ tùng' };
const SCOPE_LABELS: Record<string, string> = { MACHINE: 'Trực tiếp', MODEL: 'Theo dòng máy' };
const NO_SELECTION_MESSAGE = 'Chọn một mục ở bên trái để xem liên kết.';

const MACHINE_COLUMNS: CatalogTableColumn<CatalogRecord>[] = [
  { key: 'code', label: 'Mã máy', render: (row) => readText(row, 'code') },
  { key: 'name', label: 'Tên', render: (row) => readText(row, 'name') },
];
const PART_COLUMNS: CatalogTableColumn<CatalogRecord>[] = [
  { key: 'partNo', label: 'Mã hàng', render: (row) => readText(row, 'partNo') },
  { key: 'name', label: 'Tên', render: (row) => readText(row, 'name') },
];

function rowId(row: CatalogRecord): string {
  return readText(row, 'id');
}

function scopeText(scope: string): string {
  return SCOPE_LABELS[scope] ?? scope;
}

function PartsOfMachineTable({ machineId, templateCodes }: { machineId: string; templateCodes: Map<string, string> }) {
  const links = useAsyncRows(partsOfMachine, machineId);
  if (links.status === 'loading') {
    return <StateNotice kind="loading" />;
  }
  if (links.status === 'error') {
    return <StateNotice kind="error" message={links.message} />;
  }
  return (
    <table className="catalog-table" aria-label="Phụ tùng của máy">
      <thead>
        <tr>
          <th scope="col">Cụm</th>
          <th scope="col">Mã hàng</th>
          <th scope="col">Phạm vi</th>
          <th scope="col">Công việc bảo trì</th>
          <th scope="col">SL thường dùng</th>
        </tr>
      </thead>
      <tbody>
        {links.rows.map((link: PartOfMachine) => (
          <tr key={link.linkId}>
            <td>{link.componentName ?? '—'}</td>
            <td>{link.partNo}</td>
            <td>{scopeText(link.scope)}</td>
            <td>{(link.taskTemplateId && templateCodes.get(link.taskTemplateId)) ?? '—'}</td>
            <td>{link.typicalQty ?? '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function MachinesOfPartTable({ sparePartId }: { sparePartId: string }) {
  const links = useAsyncRows(machinesOfPart, sparePartId);
  if (links.status === 'loading') {
    return <StateNotice kind="loading" />;
  }
  if (links.status === 'error') {
    return <StateNotice kind="error" message={links.message} />;
  }
  return (
    <table className="catalog-table" aria-label="Máy dùng phụ tùng">
      <thead>
        <tr>
          <th scope="col">Mã máy</th>
          <th scope="col">Tên máy</th>
          <th scope="col">Phạm vi</th>
        </tr>
      </thead>
      <tbody>
        {links.rows.map((link: MachineOfPart) => (
          <tr key={link.machineId}>
            <td>{link.machineCode}</td>
            <td>{link.machineName}</td>
            <td>{scopeText(link.scope)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

// U-5: liên kết thiết bị–phụ tùng, đọc hai chiều (FR-MD-019). Chuyển chế độ bằng nút.
export function PartLinksScreen() {
  const [mode, setMode] = useState<LinkMode>('machine');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const machines = useCatalogList('machines', '');
  const spareParts = useCatalogList('spare-parts', '');
  const templates = useCatalogList('maint-task-templates', '');
  const templateCodes = new Map(templates.rows.map((row) => [rowId(row), readText(row, 'code')]));
  const isMachineMode = mode === 'machine';
  const leftList = isMachineMode ? machines : spareParts;

  function chooseMode(next: LinkMode) {
    setMode(next);
    setSelectedId(null);
  }

  return (
    <section className="screen" aria-labelledby="part-links-title">
      <header className="screen-header">
        <p className="screen-crumb">Danh mục / Liên kết</p>
        <h1 id="part-links-title">Liên kết thiết bị–phụ tùng</h1>
      </header>

      <div className="screen-toolbar" role="group" aria-label="Chế độ xem">
        {(Object.keys(MODE_LABELS) as LinkMode[]).map((item) => (
          <button
            key={item}
            type="button"
            className={item === mode ? 'btn-primary' : 'btn-secondary'}
            aria-pressed={item === mode}
            onClick={() => chooseMode(item)}
          >
            {MODE_LABELS[item]}
          </button>
        ))}
      </div>

      <div className="screen-split">
        <div className="card screen-list">
          {leftList.status === 'loading' && <StateNotice kind="loading" />}
          {leftList.status === 'error' && <StateNotice kind="error" message={leftList.errorMessage ?? undefined} />}
          {leftList.status === 'ready' && leftList.rows.length === 0 && <StateNotice kind="empty" />}
          {leftList.status === 'ready' && leftList.rows.length > 0 && (
            <CatalogTable
              columns={isMachineMode ? MACHINE_COLUMNS : PART_COLUMNS}
              rows={leftList.rows}
              rowKey={rowId}
              selectedId={selectedId}
              onSelect={(row) => setSelectedId(rowId(row))}
            />
          )}
        </div>

        <div className="card screen-side screen-side--wide side-card">
          {selectedId === null && <StateNotice kind="empty" message={NO_SELECTION_MESSAGE} />}
          {selectedId !== null && isMachineMode && (
            <PartsOfMachineTable machineId={selectedId} templateCodes={templateCodes} />
          )}
          {selectedId !== null && !isMachineMode && <MachinesOfPartTable sparePartId={selectedId} />}
        </div>
      </div>
    </section>
  );
}
