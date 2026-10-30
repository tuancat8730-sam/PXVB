import { useMemo, useState } from 'react';
import { StateNotice } from '../../components/ui/StateNotice';
import { StatusChip, type ChipTone } from '../../components/ui/StatusChip';
import { useCatalogList } from '../../hooks/useCatalogList';
import { describeApiError } from '../../services/apiError';
import { createRecord } from '../../services/md/catalogApi';
import { ACTIVE_GRADE, DEFECT_GROUPS, GRADES, gradeCell, toDefectRule, type CellKind, type Grade } from '../../lib/defectRules';
import { readText } from '../../lib/record';
import type { CatalogRecord } from '../../services/md/types';

const DEFECT_RESOURCE = 'defects';
const RULE_RESOURCE = 'defect-grade-rules';
const DEFAULT_SAMPLE_SIZE = '200';
const MISSING_DATE_MESSAGE = 'Chọn ngày hiệu lực.';
const SAVED_MESSAGE = 'Đã lưu luật.';
const CHIP_TONE: Record<CellKind, ChipTone> = { rule: 'ok', overlap: 'warn', empty: 'off', inactive: 'off' };
const NOT_DEFINED_MESSAGE = 'Cấp A và C chưa được định nghĩa (OPEN-QUESTIONS L75). Màn này không lưu luật cấp A và C.';

interface Selection {
  defectCode: string;
  defectName: string;
  grade: Grade;
}

interface RuleDraft {
  sampleSize: string;
  effectiveFrom: string;
}

// U-3: lưới lỗi × cấp A/B/C. Trùng hiệu lực hiện ngay trên lưới. Chỉ lưu luật cấp B.
export function DefectRulesScreen() {
  const [group, setGroup] = useState(DEFECT_GROUPS[0]);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [draft, setDraft] = useState<RuleDraft>({ sampleSize: DEFAULT_SAMPLE_SIZE, effectiveFrom: '' });
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const defects = useCatalogList(DEFECT_RESOURCE, '');
  const rules = useCatalogList(RULE_RESOURCE, '');

  const groupDefects = useMemo(
    () => defects.rows.filter((row) => readText(row, 'appliesTo') === group),
    [defects.rows, group],
  );
  const ruleList = useMemo(() => rules.rows.map(toDefectRule), [rules.rows]);
  const isReady = defects.status === 'ready' && rules.status === 'ready';

  function handleSelect(defect: CatalogRecord, grade: Grade) {
    setSelection({ defectCode: readText(defect, 'code'), defectName: readText(defect, 'name'), grade });
    setSaveMessage(null);
    setSaveError(null);
  }

  async function handleSave() {
    if (selection === null) {
      return;
    }
    if (draft.effectiveFrom === '') {
      setSaveMessage(null);
      setSaveError(MISSING_DATE_MESSAGE);
      return;
    }
    setSaveMessage(null);
    setSaveError(null);
    try {
      await createRecord(RULE_RESOURCE, {
        defectCode: selection.defectCode,
        appliesTo: group,
        grade: ACTIVE_GRADE,
        metric: 'COUNT_IN_SAMPLE',
        thresholdValue: 1,
        sampleSize: Number(draft.sampleSize),
        effectiveFrom: draft.effectiveFrom,
      });
      setSaveMessage(SAVED_MESSAGE);
      rules.reload();
    } catch (error: unknown) {
      setSaveError(describeApiError(error));
    }
  }

  return (
    <section className="screen" aria-labelledby="defect-rules-title">
      <header className="screen-header">
        <p className="screen-crumb">Danh mục / Lỗi và luật</p>
        <h1 id="defect-rules-title">Lỗi và luật phân cấp A/B/C</h1>
      </header>

      <div className="screen-toolbar">
        <label className="inline-field">
          Nhóm áp dụng
          <select value={group} onChange={(event) => setGroup(event.target.value)}>
            {DEFECT_GROUPS.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="screen-split">
        <div className="card screen-list">
          {!isReady && <StateNotice kind="loading" />}
          {isReady && groupDefects.length === 0 && <StateNotice kind="empty" />}
          {isReady && groupDefects.length > 0 && (
            <table className="catalog-table grade-grid">
              <thead>
                <tr>
                  <th scope="col">Lỗi</th>
                  {GRADES.map((grade) => (
                    <th key={grade} scope="col">
                      Cấp {grade}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {groupDefects.map((defect) => {
                  const code = readText(defect, 'code');
                  const name = readText(defect, 'name');
                  return (
                    <tr key={code}>
                      <th scope="row">{name}</th>
                      {GRADES.map((grade) => {
                        const cell = gradeCell(ruleList, code, grade);
                        const isSelected = selection?.defectCode === code && selection.grade === grade;
                        return (
                          <td key={grade}>
                            <button
                              type="button"
                              className="grade-cell"
                              aria-label={`${name}, cấp ${grade}`}
                              aria-pressed={isSelected}
                              onClick={() => handleSelect(defect, grade)}
                            >
                              <StatusChip tone={CHIP_TONE[cell.kind]}>{cell.text}</StatusChip>
                            </button>
                          </td>
                        );
                      })}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {selection && (
          <aside className="card side-card screen-side">
            <h2 className="side-title">
              {selection.defectName} · cấp {selection.grade}
            </h2>
            {selection.grade !== ACTIVE_GRADE && <StateNotice kind="partial" message={NOT_DEFINED_MESSAGE} />}
            {selection.grade === ACTIVE_GRADE && (
              <>
                <p className="side-text">Ngưỡng: ≥ 1 lần ngoài biên trong cỡ mẫu (quy tắc đã chốt, L74).</p>
                <label className="inline-field">
                  Cỡ mẫu
                  <input
                    type="number"
                    min="1"
                    value={draft.sampleSize}
                    onChange={(event) => setDraft({ ...draft, sampleSize: event.target.value })}
                  />
                </label>
                <label className="inline-field">
                  Hiệu lực từ
                  <input
                    type="date"
                    value={draft.effectiveFrom}
                    onChange={(event) => setDraft({ ...draft, effectiveFrom: event.target.value })}
                  />
                </label>
                <button type="button" className="btn-primary" onClick={handleSave}>
                  Lưu luật
                </button>
                {saveMessage && <StateNotice kind="success" message={saveMessage} />}
                {saveError && <StateNotice kind="error" message={saveError} />}
              </>
            )}
          </aside>
        )}
      </div>
    </section>
  );
}
