import { useState, type FormEvent } from 'react';
import type { FormField, FormSection } from '../../lib/kscl/formSchema';

interface KsclAddFieldFormProps {
  sections: FormSection[];
  onAdd: (sectionIndex: number, field: FormField) => void;
}

interface Draft {
  sectionIndex: number;
  code: string;
  label: string;
  type: string;
  optionsText: string;
}

const FIELD_TYPE_OPTIONS = ['NUMBER', 'TEXT', 'CHECK', 'SELECT'];
const EMPTY_DRAFT: Draft = { sectionIndex: 0, code: '', label: '', type: 'TEXT', optionsText: '' };

// Chuỗi "A, B, C" thành danh sách lựa chọn; bỏ phần trống.
function parseOptions(text: string): string[] {
  return text
    .split(',')
    .map((option) => option.trim())
    .filter((option) => option !== '');
}

export function KsclAddFieldForm({ sections, onAdd }: KsclAddFieldFormProps) {
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT);
  const isSelect = draft.type === 'SELECT';

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const field: FormField = {
      code: draft.code.trim(),
      label: draft.label.trim(),
      type: draft.type,
      required: false,
      ...(isSelect ? { options: parseOptions(draft.optionsText) } : {}),
    };
    onAdd(draft.sectionIndex, field);
    setDraft({ ...EMPTY_DRAFT, sectionIndex: draft.sectionIndex });
  }

  return (
    <form className="add-field-form" onSubmit={handleSubmit}>
      <label>
        Mục
        <select
          value={draft.sectionIndex}
          onChange={(event) => setDraft({ ...draft, sectionIndex: Number(event.target.value) })}
        >
          {sections.map((section, index) => (
            <option key={index} value={index}>
              {section.title}
            </option>
          ))}
        </select>
      </label>
      <label>
        Mã trường
        <input value={draft.code} onChange={(event) => setDraft({ ...draft, code: event.target.value })} />
      </label>
      <label>
        Nhãn
        <input value={draft.label} onChange={(event) => setDraft({ ...draft, label: event.target.value })} />
      </label>
      <label>
        Kiểu
        <select value={draft.type} onChange={(event) => setDraft({ ...draft, type: event.target.value })}>
          {FIELD_TYPE_OPTIONS.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </label>
      {isSelect && (
        <label>
          Lựa chọn (cách nhau bằng dấu phẩy)
          <input
            value={draft.optionsText}
            onChange={(event) => setDraft({ ...draft, optionsText: event.target.value })}
          />
        </label>
      )}
      <button type="submit" className="btn-secondary">
        Thêm trường
      </button>
    </form>
  );
}
