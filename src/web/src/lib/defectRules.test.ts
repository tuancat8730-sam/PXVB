import { describe, expect, it } from 'vitest';
import { gradeCell, type DefectRule } from './defectRules';

function rule(overrides: Partial<DefectRule>): DefectRule {
  return {
    id: 'r1',
    defectCode: 'SD_DN',
    appliesTo: 'ROD',
    grade: 'B',
    metric: 'COUNT_IN_SAMPLE',
    thresholdValue: 1,
    sampleSize: 200,
    effectiveFrom: '2026-11-01',
    effectiveTo: null,
    ...overrides,
  };
}

describe('gradeCell', () => {
  it('describes a single grade B rule by its metric and threshold', () => {
    const cell = gradeCell([rule({})], 'SD_DN', 'B');

    expect(cell).toEqual({ kind: 'rule', text: 'Đếm trong mẫu · ≥ 1' });
  });

  it('labels a rate rule in percent', () => {
    const cell = gradeCell([rule({ metric: 'RATE_PCT', thresholdValue: 2.5 })], 'SD_DN', 'B');

    expect(cell).toEqual({ kind: 'rule', text: 'Tỷ lệ % · ≥ 2.5' });
  });

  it('reports grade B as empty when no rule exists', () => {
    expect(gradeCell([], 'SD_DN', 'B')).toEqual({ kind: 'empty', text: 'Chưa có luật' });
  });

  it('reports grades A and C as not activated, because they are not defined yet (L75)', () => {
    expect(gradeCell([], 'SD_DN', 'A')).toEqual({ kind: 'inactive', text: 'Chưa kích hoạt (L75)' });
    expect(gradeCell([], 'SD_DN', 'C')).toEqual({ kind: 'inactive', text: 'Chưa kích hoạt (L75)' });
  });

  it('flags two rules whose effective dates overlap', () => {
    const rules = [
      rule({ id: 'r1', effectiveFrom: '2026-11-01', effectiveTo: '2026-12-31' }),
      rule({ id: 'r2', effectiveFrom: '2026-12-01', effectiveTo: null }),
    ];

    expect(gradeCell(rules, 'SD_DN', 'B')).toEqual({ kind: 'overlap', text: 'Trùng hiệu lực (2 luật)' });
  });

  it('does not flag rules that follow one another', () => {
    const rules = [
      rule({ id: 'r1', effectiveFrom: '2026-11-01', effectiveTo: '2026-11-30' }),
      rule({ id: 'r2', effectiveFrom: '2026-12-01', effectiveTo: null }),
    ];

    expect(gradeCell(rules, 'SD_DN', 'B').kind).toBe('rule');
  });

  it('ignores rules of other defects', () => {
    expect(gradeCell([rule({ defectCode: 'DG' })], 'SD_DN', 'B').kind).toBe('empty');
  });
});
