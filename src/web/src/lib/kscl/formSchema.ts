// Lược đồ biểu mẫu KSCL phiên bản 1. Các kiểm tra trong schemaProblems khớp với
// QcFormSchemaRules phía backend, để người thiết kế thấy lỗi trước khi lưu.

export const SUPPORTED_SCHEMA_VERSION = 1;
const FIELD_TYPES: readonly string[] = ['NUMBER', 'TEXT', 'CHECK', 'SELECT'];
const SELECT_TYPE = 'SELECT';

export interface FormField {
  code: string;
  label: string;
  type: string;
  required: boolean;
  options?: string[];
}

export interface FormSection {
  title: string;
  fields: FormField[];
}

export interface FormSchema {
  schemaVersion: number;
  sections: FormSection[];
}

// Đọc lược đồ từ payload API. Không phải đối tượng có mục là không đọc được.
export function parseFormSchema(value: unknown): FormSchema | null {
  if (typeof value !== 'object' || value === null) {
    return null;
  }
  const candidate = value as Partial<FormSchema>;
  if (!Array.isArray(candidate.sections)) {
    return null;
  }
  return candidate as FormSchema;
}

function replaceSection(schema: FormSchema, sectionIndex: number, section: FormSection): FormSchema {
  return {
    ...schema,
    sections: schema.sections.map((current, index) => (index === sectionIndex ? section : current)),
  };
}

// Đổi chỗ một trường với trường liền kề trong cùng mục. Ở mép thì giữ nguyên.
export function moveField(schema: FormSchema, sectionIndex: number, fieldIndex: number, delta: -1 | 1): FormSchema {
  const section = schema.sections[sectionIndex];
  const targetIndex = fieldIndex + delta;
  if (!section || targetIndex < 0 || targetIndex >= section.fields.length) {
    return schema;
  }
  const fields = [...section.fields];
  [fields[fieldIndex], fields[targetIndex]] = [fields[targetIndex], fields[fieldIndex]];
  return replaceSection(schema, sectionIndex, { ...section, fields });
}

export function addField(schema: FormSchema, sectionIndex: number, field: FormField): FormSchema {
  const section = schema.sections[sectionIndex];
  if (!section) {
    return schema;
  }
  return replaceSection(schema, sectionIndex, { ...section, fields: [...section.fields, field] });
}

function fieldProblems(field: FormField, seenCodes: Set<string>): string[] {
  const problems: string[] = [];
  const code = field.code.trim();
  if (code === '') {
    problems.push('Trường thiếu mã');
  } else if (seenCodes.has(code)) {
    problems.push(`Trùng mã trường ${code}`);
  }
  seenCodes.add(code);

  if (!FIELD_TYPES.includes(field.type)) {
    problems.push(`Trường ${code} có kiểu không hỗ trợ: ${field.type}`);
  }
  if (field.label.trim() === '') {
    problems.push(`Trường ${code} thiếu nhãn`);
  }
  if (field.type === SELECT_TYPE && (field.options ?? []).length === 0) {
    problems.push(`Trường ${code} kiểu SELECT cần danh sách lựa chọn`);
  }
  return problems;
}

// Danh sách lỗi bằng tiếng Việt. Rỗng nghĩa là lược đồ có thể lưu.
export function schemaProblems(schema: FormSchema): string[] {
  if (schema.schemaVersion !== SUPPORTED_SCHEMA_VERSION) {
    return [`Phiên bản lược đồ không được hỗ trợ: ${schema.schemaVersion}`];
  }
  if (schema.sections.length === 0) {
    return ['Biểu mẫu cần ít nhất một mục'];
  }
  const seenCodes = new Set<string>();
  return schema.sections.flatMap((section) =>
    section.fields.length === 0
      ? ['Mục không có trường nào']
      : section.fields.flatMap((field) => fieldProblems(field, seenCodes)),
  );
}
