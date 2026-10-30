// Lưới lỗi × cấp A/B/C (FR-MD-012). Chỉ cấp B được kích hoạt; A và C chờ định nghĩa (OPEN-QUESTIONS L75).
import type { CatalogRecord } from '../services/md/types';
import { readNumber, readText } from './record';

export type Grade = 'A' | 'B' | 'C';
export const GRADES: readonly Grade[] = ['A', 'B', 'C'];
export const ACTIVE_GRADE: Grade = 'B';
export const DEFECT_GROUPS: readonly string[] = ['ROD', 'PACK', 'CARTON', 'MATERIAL', 'PACKAGING'];

export interface DefectRule {
  id: string;
  defectCode: string;
  appliesTo: string;
  grade: string;
  metric: string;
  thresholdValue: number;
  sampleSize: number | null;
  effectiveFrom: string;
  effectiveTo: string | null;
}

export type CellKind = 'rule' | 'overlap' | 'empty' | 'inactive';

export interface GradeCell {
  kind: CellKind;
  text: string;
}

const METRIC_LABELS: Record<string, string> = {
  COUNT_IN_SAMPLE: 'Đếm trong mẫu',
  RATE_PCT: 'Tỷ lệ %',
};
const OPEN_END_DATE = '9999-12-31';
const INACTIVE_TEXT = 'Chưa kích hoạt (L75)';

// Đọc một bản ghi luật từ API. Ngày so sánh theo chuỗi yyyy-MM-dd.
export function toDefectRule(record: CatalogRecord): DefectRule {
  const effectiveTo = readText(record, 'effectiveTo');
  const sampleSize = readNumber(record, 'sampleSize');
  return {
    id: readText(record, 'id'),
    defectCode: readText(record, 'defectCode'),
    appliesTo: readText(record, 'appliesTo'),
    grade: readText(record, 'grade'),
    metric: readText(record, 'metric'),
    thresholdValue: readNumber(record, 'thresholdValue'),
    sampleSize: sampleSize === 0 ? null : sampleSize,
    effectiveFrom: readText(record, 'effectiveFrom'),
    effectiveTo: effectiveTo === '' ? null : effectiveTo,
  };
}

function rangesOverlap(first: DefectRule, second: DefectRule): boolean {
  return (
    first.effectiveFrom <= (second.effectiveTo ?? OPEN_END_DATE) &&
    second.effectiveFrom <= (first.effectiveTo ?? OPEN_END_DATE)
  );
}

// Nội dung một ô của lưới: luật, trùng hiệu lực, chưa có luật, hoặc chưa kích hoạt.
export function gradeCell(rules: DefectRule[], defectCode: string, grade: Grade): GradeCell {
  if (grade !== ACTIVE_GRADE) {
    return { kind: 'inactive', text: INACTIVE_TEXT };
  }
  const matching = rules.filter((rule) => rule.defectCode === defectCode && rule.grade === grade);
  if (matching.length === 0) {
    return { kind: 'empty', text: 'Chưa có luật' };
  }
  const hasOverlap = matching.some((first, firstIndex) =>
    matching.some((second, secondIndex) => firstIndex !== secondIndex && rangesOverlap(first, second)),
  );
  if (hasOverlap) {
    return { kind: 'overlap', text: `Trùng hiệu lực (${matching.length} luật)` };
  }
  const [shown] = matching;
  return { kind: 'rule', text: `${METRIC_LABELS[shown.metric] ?? shown.metric} · ≥ ${shown.thresholdValue}` };
}
