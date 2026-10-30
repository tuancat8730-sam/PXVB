import type { ReactNode } from 'react';

export interface CatalogTableColumn<T> {
  key: string;
  label: string;
  render: (row: T) => ReactNode;
}

interface CatalogTableProps<T> {
  columns: CatalogTableColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  selectedId: string | null;
  onSelect: (row: T) => void;
}

// Bảng danh mục dùng chung. Cột đầu là nút chọn dòng (bàn phím và đọc màn hình đều dùng được).
export function CatalogTable<T>({ columns, rows, rowKey, selectedId, onSelect }: CatalogTableProps<T>) {
  const [keyColumn, ...otherColumns] = columns;

  return (
    <table className="catalog-table">
      <thead>
        <tr>
          {columns.map((column) => (
            <th key={column.key} scope="col">
              {column.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => {
          const id = rowKey(row);
          const isSelected = id === selectedId;
          return (
            <tr key={id} className={isSelected ? 'is-selected' : undefined}>
              <td>
                <button
                  type="button"
                  className="row-select"
                  aria-pressed={isSelected}
                  onClick={() => onSelect(row)}
                >
                  {keyColumn.render(row)}
                </button>
              </td>
              {otherColumns.map((column) => (
                <td key={column.key}>{column.render(row)}</td>
              ))}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
