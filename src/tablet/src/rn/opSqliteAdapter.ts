/**
 * Bộ chuyển `SqlDb` cho React Native bằng `@op-engineering/op-sqlite` (SDD §2.2).
 *
 * LƯU Ý: tệp này CHƯA được build/chạy trên thiết bị trong repo này (môi trường phát triển không có toolchain
 * React Native). Giao diện `SqlDb` đã được kiểm thử bằng `node:sqlite`; adapter chỉ ánh xạ API đồng bộ của
 * op-sqlite. Cần kiểm tra trên thiết bị thật và bật mã hóa nghỉ (SQLCipher) theo SDD §16.3.
 */
import type { SqlDb } from '../db.ts';

// Kiểu tối thiểu của op-sqlite để tệp độc lập với việc cài thư viện.
interface OpSqliteConnection {
  executeSync(sql: string, params?: unknown[]): { rows: Array<Record<string, unknown>> };
}

export function createOpSqliteDb(connection: OpSqliteConnection): SqlDb {
  let depth = 0;
  const db: SqlDb = {
    exec(sql) {
      for (const stmt of sql.split(';')) {
        if (stmt.trim()) connection.executeSync(stmt);
      }
    },
    run(sql, params = []) {
      connection.executeSync(sql, params);
    },
    all<T>(sql: string, params: unknown[] = []): T[] {
      return connection.executeSync(sql, params).rows as T[];
    },
    transaction<T>(fn: () => T): T {
      const outer = depth === 0;
      if (outer) connection.executeSync('BEGIN');
      depth++;
      try {
        const result = fn();
        depth--;
        if (outer) connection.executeSync('COMMIT');
        return result;
      } catch (e) {
        depth--;
        if (outer) connection.executeSync('ROLLBACK');
        throw e;
      }
    },
  };
  return db;
}
