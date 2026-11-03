import type { SqlDb } from './db.ts';

/** Giá trị một trường của biểu mẫu đang nhập. */
export type DraftValues = Record<string, unknown>;

interface DraftRow {
  data: string;
}

/**
 * Nháp biểu mẫu tự lưu theo từng thay đổi trường (FR-CC-065): mất điện thì mở lại vẫn còn giá trị đã nhập.
 * Mỗi lần ghi là một giao dịch ngắn, nên không để lại nháp dở dang.
 */
export class DraftStore {
  private readonly db: SqlDb;

  constructor(db: SqlDb) {
    this.db = db;
  }

  migrate(): void {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS form_draft (
        form_id TEXT PRIMARY KEY,
        data TEXT NOT NULL,
        updated_at TEXT NOT NULL
      );
    `);
  }

  setField(formId: string, field: string, value: unknown): void {
    this.db.transaction(() => {
      const next: DraftValues = { ...(this.load(formId) ?? {}), [field]: value };
      this.db.run(
        `INSERT INTO form_draft (form_id, data, updated_at) VALUES (?, ?, ?)
         ON CONFLICT(form_id) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at`,
        [formId, JSON.stringify(next), new Date().toISOString()],
      );
    });
  }

  load(formId: string): DraftValues | null {
    const rows = this.db.all<DraftRow>('SELECT data FROM form_draft WHERE form_id = ?', [formId]);
    return rows.length === 0 ? null : (JSON.parse(rows[0].data) as DraftValues);
  }

  clear(formId: string): void {
    this.db.run('DELETE FROM form_draft WHERE form_id = ?', [formId]);
  }
}
