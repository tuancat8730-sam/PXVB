/**
 * UUID v7 (RFC 9562): 48-bit mili-giây Unix + ngẫu nhiên. Tablet tự sinh khóa nên có thể tạo bản ghi ngoại tuyến
 * và khóa vẫn tăng dần theo thời gian (ít phân mảnh chỉ mục InnoDB phía máy chủ).
 */
export function uuid7(nowMs: number = Date.now()): string {
  const bytes = new Uint8Array(16);
  globalThis.crypto.getRandomValues(bytes);
  // 48 bit thời gian (big-endian)
  let ms = BigInt(nowMs);
  for (let i = 5; i >= 0; i--) {
    bytes[i] = Number(ms & 0xffn);
    ms >>= 8n;
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x70; // version 7
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variant 10
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
