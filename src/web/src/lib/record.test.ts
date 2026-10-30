import { describe, expect, it } from 'vitest';
import { readDate, readNumber, readText } from './record';

describe('record readers', () => {
  const record = {
    name: 'Dao cắt',
    version: 3,
    updatedAt: '2026-10-18T15:20:00',
    defaultSupplierId: null,
    weird: { nested: true },
  };

  it('reads a text field and returns an empty string when it is missing or not text', () => {
    expect(readText(record, 'name')).toBe('Dao cắt');
    expect(readText(record, 'defaultSupplierId')).toBe('');
    expect(readText(record, 'weird')).toBe('');
  });

  it('reads a number field and returns zero when it is not a number', () => {
    expect(readNumber(record, 'version')).toBe(3);
    expect(readNumber(record, 'name')).toBe(0);
  });

  it('reads an ISO date-time as a Date, or null when absent', () => {
    expect(readDate(record, 'updatedAt')).toEqual(new Date(2026, 9, 18, 15, 20));
    expect(readDate(record, 'defaultSupplierId')).toBeNull();
    expect(readDate(record, 'name')).toBeNull();
  });
});
