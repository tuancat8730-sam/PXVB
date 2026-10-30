import { describe, expect, it } from 'vitest';
import {
  addField,
  moveField,
  parseFormSchema,
  schemaProblems,
  type FormSchema,
} from './formSchema';

const VALID: FormSchema = {
  schemaVersion: 1,
  sections: [
    {
      title: 'Thông số',
      fields: [
        { code: 'khoi_luong', label: 'Khối lượng', type: 'NUMBER', required: true },
        { code: 'do_cung', label: 'Độ cứng', type: 'NUMBER', required: true },
      ],
    },
    {
      title: 'Cảm quan',
      fields: [{ code: 'cam_quan', label: 'Cảm quan', type: 'CHECK', required: false }],
    },
  ],
};

describe('moveField', () => {
  it('swaps a field with its neighbour inside the same section', () => {
    const moved = moveField(VALID, 0, 0, 1);

    expect(moved.sections[0].fields.map((field) => field.code)).toEqual(['do_cung', 'khoi_luong']);
  });

  it('does not change the original schema', () => {
    moveField(VALID, 0, 0, 1);

    expect(VALID.sections[0].fields[0].code).toBe('khoi_luong');
  });

  it('leaves the schema unchanged when the field is already at the edge', () => {
    const moved = moveField(VALID, 0, 0, -1);

    expect(moved).toEqual(VALID);
  });
});

describe('addField', () => {
  it('appends a field to the end of the chosen section', () => {
    const added = addField(VALID, 1, { code: 'mau', label: 'Màu', type: 'TEXT', required: false });

    expect(added.sections[1].fields.map((field) => field.code)).toEqual(['cam_quan', 'mau']);
    expect(VALID.sections[1].fields).toHaveLength(1);
  });
});

describe('schemaProblems', () => {
  it('reports no problem for a valid schema', () => {
    expect(schemaProblems(VALID)).toEqual([]);
  });

  it('reports a SELECT field that has no options', () => {
    const schema = addField(VALID, 1, { code: 'chon_co', label: 'Chọn cỡ', type: 'SELECT', required: true });

    expect(schemaProblems(schema)).toEqual(['Trường chon_co kiểu SELECT cần danh sách lựa chọn']);
  });

  it('reports a duplicated field code', () => {
    const schema = addField(VALID, 1, { code: 'khoi_luong', label: 'Trùng', type: 'NUMBER', required: false });

    expect(schemaProblems(schema)).toEqual(['Trùng mã trường khoi_luong']);
  });

  it('reports an unsupported type and a missing label', () => {
    const schema = addField(VALID, 1, { code: 'x', label: '', type: 'DATE', required: false });

    expect(schemaProblems(schema)).toEqual([
      'Trường x có kiểu không hỗ trợ: DATE',
      'Trường x thiếu nhãn',
    ]);
  });

  it('reports a schema with no section', () => {
    expect(schemaProblems({ schemaVersion: 1, sections: [] })).toEqual(['Biểu mẫu cần ít nhất một mục']);
  });
});

describe('parseFormSchema', () => {
  it('reads a schema object from the API payload', () => {
    expect(parseFormSchema(VALID)).toEqual(VALID);
  });

  it('returns null for a value that is not an object', () => {
    expect(parseFormSchema('không phải lược đồ')).toBeNull();
    expect(parseFormSchema(null)).toBeNull();
  });
});
