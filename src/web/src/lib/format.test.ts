import { describe, expect, it } from 'vitest';
import { formatDateTime } from './format';

describe('formatDateTime', () => {
  it('formats a date and time as dd/MM/yyyy HH:mm', () => {
    // Arrange
    const value = new Date(2026, 9, 18, 15, 20);

    // Act
    const text = formatDateTime(value);

    // Assert
    expect(text).toBe('18/10/2026 15:20');
  });

  it('pads single-digit days, months and hours', () => {
    expect(formatDateTime(new Date(2026, 0, 5, 7, 3))).toBe('05/01/2026 07:03');
  });

  it('returns a dash when there is no value', () => {
    expect(formatDateTime(null)).toBe('—');
  });
});
