/**
 * Cổng truy cập SQLite tối thiểu. Hiện thực thật cho React Native nằm ở `rn/opSqliteAdapter.ts`;
 * kiểm thử dùng `node:sqlite` (xem test/helpers.ts). Đồng bộ (sync) để giao dịch ngắn, đơn giản.
 */
export interface SqlDb {
  exec(sql: string): void;
  run(sql: string, params?: unknown[]): void;
  all<T = Record<string, unknown>>(sql: string, params?: unknown[]): T[];
  /** Chạy `fn` trong một giao dịch; ném lỗi thì rollback (FR-CC-065: không mất dữ liệu khi tắt đột ngột). */
  transaction<T>(fn: () => T): T;
}
