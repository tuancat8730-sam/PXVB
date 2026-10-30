import type { CatalogRecord } from '../services/md/types';

// Đọc một trường của bản ghi danh mục (kiểu unknown từ API) theo kiểu cụ thể, không ép kiểu mù.

export function readText(record: CatalogRecord, key: string): string {
  const value = record[key];
  return typeof value === 'string' ? value : '';
}

export function readNumber(record: CatalogRecord, key: string): number {
  const value = record[key];
  return typeof value === 'number' ? value : 0;
}

function isRecord(value: unknown): value is CatalogRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

// Đọc mảng JSON (checklist, vật tư). Phần tử không phải đối tượng bị bỏ qua.
export function readList(record: CatalogRecord, key: string): CatalogRecord[] {
  const value = record[key];
  return Array.isArray(value) ? value.filter(isRecord) : [];
}

export function readDate(record: CatalogRecord, key: string): Date | null {
  const value = readText(record, key);
  if (value === '') {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}
