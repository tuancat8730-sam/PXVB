import type { FormSchema } from '../../lib/kscl/formSchema';

interface KsclFieldTableProps {
  schema: FormSchema;
  onMove: (sectionIndex: number, fieldIndex: number, delta: -1 | 1) => void;
}

interface FlatField {
  sectionIndex: number;
  fieldIndex: number;
  sectionTitle: string;
  code: string;
  label: string;
  type: string;
  required: boolean;
  isFirstInSection: boolean;
  isLastInSection: boolean;
}

function flattenFields(schema: FormSchema): FlatField[] {
  return schema.sections.flatMap((section, sectionIndex) =>
    section.fields.map((field, fieldIndex) => ({
      sectionIndex,
      fieldIndex,
      sectionTitle: section.title,
      code: field.code,
      label: field.label,
      type: field.type,
      required: field.required,
      isFirstInSection: fieldIndex === 0,
      isLastInSection: fieldIndex === section.fields.length - 1,
    })),
  );
}

// Bảng trường theo mục. Đổi thứ tự bằng nút Lên/Xuống (UQ1): dùng được bằng bàn phím.
export function KsclFieldTable({ schema, onMove }: KsclFieldTableProps) {
  const rows = flattenFields(schema);

  return (
    <table className="catalog-table">
      <thead>
        <tr>
          <th scope="col">STT</th>
          <th scope="col">Mục</th>
          <th scope="col">Mã trường</th>
          <th scope="col">Kiểu</th>
          <th scope="col">Bắt buộc</th>
          <th scope="col">Sắp xếp</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr key={`${row.sectionIndex}-${row.fieldIndex}-${row.code}`}>
            <td>{index + 1}</td>
            <td>{row.sectionTitle}</td>
            <td className="mono">{row.code}</td>
            <td>{row.type}</td>
            <td>{row.required ? 'Có' : 'Không'}</td>
            <td className="sort-buttons">
              <button
                type="button"
                aria-label={`Lên trường ${row.code}`}
                disabled={row.isFirstInSection}
                onClick={() => onMove(row.sectionIndex, row.fieldIndex, -1)}
              >
                Lên
              </button>
              <button
                type="button"
                aria-label={`Xuống trường ${row.code}`}
                disabled={row.isLastInSection}
                onClick={() => onMove(row.sectionIndex, row.fieldIndex, 1)}
              >
                Xuống
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
