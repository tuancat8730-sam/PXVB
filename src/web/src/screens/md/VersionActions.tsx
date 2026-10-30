import { useState } from 'react';
import { StateNotice } from '../../components/ui/StateNotice';
import { describeApiError } from '../../services/apiError';
import { activateVersion, cloneVersion } from '../../services/md/catalogApi';
import type { CatalogRecord } from '../../services/md/types';
import { readNumber, readText } from '../../lib/record';
import { statusDisplay } from '../../lib/status';

const MISSING_DATE_MESSAGE = 'Chọn ngày hiệu lực.';
const CLONED_MESSAGE = 'Đã nhân bản thành bản nháp mới.';
const ACTIVATED_MESSAGE = 'Đã kích hoạt phiên bản.';

interface VersionActionsProps {
  resource: string;
  record: CatalogRecord;
  versions: CatalogRecord[];
  onChanged: () => void;
}

// Các phiên bản cùng mã, mới nhất trước. Lấy từ danh sách đã tải, không gọi thêm API.
export function versionsOf(rows: CatalogRecord[], code: string): CatalogRecord[] {
  return rows
    .filter((row) => readText(row, 'code') === code)
    .sort((left, right) => readNumber(right, 'versionNo') - readNumber(left, 'versionNo'));
}

// Lịch sử phiên bản, nhân bản và kích hoạt bản nháp (cần ngày hiệu lực). Bản đang dùng không sửa trực tiếp.
export function VersionActions({ resource, record, versions, onChanged }: VersionActionsProps) {
  const [effectiveFrom, setEffectiveFrom] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const recordId = readText(record, 'id');
  const isDraft = readText(record, 'status') === 'DRAFT';

  async function runAction(action: () => Promise<unknown>, successMessage: string) {
    setMessage(null);
    setActionError(null);
    try {
      await action();
      setMessage(successMessage);
      onChanged();
    } catch (error: unknown) {
      setActionError(describeApiError(error));
    }
  }

  function handleActivate() {
    if (effectiveFrom === '') {
      setMessage(null);
      setActionError(MISSING_DATE_MESSAGE);
      return;
    }
    void runAction(() => activateVersion(resource, recordId, effectiveFrom), ACTIVATED_MESSAGE);
  }

  return (
    <div className="version-actions">
      <h2 className="side-title">Lịch sử phiên bản</h2>
      <ul aria-label="Lịch sử phiên bản" className="version-list">
        {versions.map((row) => (
          <li key={readText(row, 'id')}>
            v{readNumber(row, 'versionNo')} · {statusDisplay(readText(row, 'status')).label}
          </li>
        ))}
      </ul>

      <div className="side-actions">
        <button type="button" className="btn-secondary" onClick={() => void runAction(() => cloneVersion(resource, recordId), CLONED_MESSAGE)}>
          Nhân bản
        </button>
      </div>

      {isDraft && (
        <div className="activate-block">
          <label>
            Hiệu lực từ
            <input type="date" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} />
          </label>
          <button type="button" className="btn-primary" onClick={handleActivate}>
            Kích hoạt phiên bản
          </button>
        </div>
      )}

      {message && <StateNotice kind="success" message={message} />}
      {actionError && <StateNotice kind="error" message={actionError} />}
    </div>
  );
}
