const EMPTY_TEXT = '—';

function twoDigits(value: number): string {
  return value.toString().padStart(2, '0');
}

// Định dạng vi-VN: dd/MM/yyyy HH:mm, theo giờ địa phương của trình duyệt.
export function formatDateTime(value: Date | null): string {
  if (value === null) {
    return EMPTY_TEXT;
  }
  const date = `${twoDigits(value.getDate())}/${twoDigits(value.getMonth() + 1)}/${value.getFullYear()}`;
  const time = `${twoDigits(value.getHours())}:${twoDigits(value.getMinutes())}`;
  return `${date} ${time}`;
}
