import type { SqlDb } from './db.ts';
import { uuid7 } from './uuid7.ts';

export type CommandStatus = 'PENDING' | 'SYNCING' | 'SYNCED' | 'CONFLICT' | 'REJECTED';

export interface NewCommand {
  /** Loại lệnh, vd `PR.RecordOutput`. */
  type: string;
  entityId?: string;
  payload: unknown;
  baseVersion?: number | null;
  shiftSessionId?: string | null;
  machineId?: string | null;
  /** Thời điểm phát sinh thực tế; mặc định là lúc ghi vào hàng đợi. */
  occurredAt?: string;
}

export interface QueuedCommand {
  seq: number;
  clientCommandId: string;
  type: string;
  entityId: string | null;
  payload: unknown;
  occurredAt: string;
  enteredAt: string;
  baseVersion: number | null;
  shiftSessionId: string | null;
  machineId: string | null;
  status: CommandStatus;
  message: string | null;
  conflictId: string | null;
  attempts: number;
}

interface Row {
  seq: number;
  client_command_id: string;
  type: string;
  entity_id: string | null;
  payload: string;
  occurred_at: string;
  entered_at: string;
  base_version: number | null;
  shift_session_id: string | null;
  machine_id: string | null;
  status: CommandStatus;
  message: string | null;
  conflict_id: string | null;
  attempts: number;
}

/**
 * Hàng đợi lệnh ngoại tuyến (FR-CC-060): mọi thao tác ghi được lưu vào SQLite trước khi gọi API; thứ tự theo `seq`.
 * Khi mở lại app sau mất điện, lệnh đang `SYNCING` được trả về `PENDING` ({@link recoverInterrupted}) — máy chủ
 * idempotent theo clientCommandId nên gửi lại là an toàn.
 */
export class CommandQueue {
  private readonly db: SqlDb;
  private readonly now: () => Date;

  constructor(db: SqlDb, now: () => Date = () => new Date()) {
    this.db = db;
    this.now = now;
  }

  migrate(): void {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS cmd_queue (
        seq INTEGER PRIMARY KEY AUTOINCREMENT,
        client_command_id TEXT NOT NULL UNIQUE,
        type TEXT NOT NULL,
        entity_id TEXT,
        payload TEXT NOT NULL,
        occurred_at TEXT NOT NULL,
        entered_at TEXT NOT NULL,
        base_version INTEGER,
        shift_session_id TEXT,
        machine_id TEXT,
        status TEXT NOT NULL DEFAULT 'PENDING',
        message TEXT,
        conflict_id TEXT,
        attempts INTEGER NOT NULL DEFAULT 0
      );
      CREATE INDEX IF NOT EXISTS ix_cmd_queue_status ON cmd_queue(status, seq);
    `);
  }

  enqueue(cmd: NewCommand): string {
    const id = uuid7(this.now().getTime());
    const entered = this.now().toISOString();
    this.db.run(
      `INSERT INTO cmd_queue(client_command_id, type, entity_id, payload, occurred_at, entered_at, base_version,
                             shift_session_id, machine_id) VALUES (?,?,?,?,?,?,?,?,?)`,
      [
        id,
        cmd.type,
        cmd.entityId ?? null,
        JSON.stringify(cmd.payload ?? null),
        cmd.occurredAt ?? entered,
        entered,
        cmd.baseVersion ?? null,
        cmd.shiftSessionId ?? null,
        cmd.machineId ?? null,
      ],
    );
    return id;
  }

  /** Các lệnh chờ gửi theo đúng thứ tự phát sinh. */
  pending(limit: number): QueuedCommand[] {
    return this.db
      .all<Row>(`SELECT * FROM cmd_queue WHERE status = 'PENDING' ORDER BY seq LIMIT ?`, [limit])
      .map(toCommand);
  }

  markSyncing(ids: string[]): void {
    this.db.transaction(() => {
      for (const id of ids) {
        this.db.run(
          `UPDATE cmd_queue SET status = 'SYNCING', attempts = attempts + 1 WHERE client_command_id = ?`,
          [id],
        );
      }
    });
  }

  setResult(id: string, status: CommandStatus, message: string | null, conflictId: string | null): void {
    this.db.run(
      `UPDATE cmd_queue SET status = ?, message = ?, conflict_id = ? WHERE client_command_id = ?`,
      [status, message, conflictId, id],
    );
  }

  /** Đưa lệnh về PENDING (lỗi mạng/lỗi tạm thời). */
  release(ids: string[]): void {
    this.db.transaction(() => {
      for (const id of ids) {
        this.db.run(`UPDATE cmd_queue SET status = 'PENDING' WHERE client_command_id = ? AND status = 'SYNCING'`, [id]);
      }
    });
  }

  recoverInterrupted(): number {
    const rows = this.db.all<{ n: number }>(`SELECT COUNT(*) AS n FROM cmd_queue WHERE status = 'SYNCING'`);
    this.db.run(`UPDATE cmd_queue SET status = 'PENDING' WHERE status = 'SYNCING'`);
    return rows[0]?.n ?? 0;
  }

  /** Số lệnh chưa đồng bộ xong (PENDING + SYNCING) — gửi kèm mỗi lần push cho giám sát. */
  unsyncedCount(): number {
    const rows = this.db.all<{ n: number }>(
      `SELECT COUNT(*) AS n FROM cmd_queue WHERE status IN ('PENDING','SYNCING')`,
    );
    return rows[0]?.n ?? 0;
  }

  get(id: string): QueuedCommand | undefined {
    const rows = this.db.all<Row>(`SELECT * FROM cmd_queue WHERE client_command_id = ?`, [id]);
    return rows[0] ? toCommand(rows[0]) : undefined;
  }

  byStatus(status: CommandStatus): QueuedCommand[] {
    return this.db.all<Row>(`SELECT * FROM cmd_queue WHERE status = ? ORDER BY seq`, [status]).map(toCommand);
  }

  /** Xóa lệnh đã đồng bộ cũ hơn `olderThan` để giữ dung lượng; không đụng lệnh còn chờ/xung đột. */
  purgeSynced(olderThan: Date): number {
    const before = this.db.all<{ n: number }>(`SELECT COUNT(*) AS n FROM cmd_queue WHERE status = 'SYNCED' AND entered_at < ?`, [
      olderThan.toISOString(),
    ])[0]?.n ?? 0;
    this.db.run(`DELETE FROM cmd_queue WHERE status = 'SYNCED' AND entered_at < ?`, [olderThan.toISOString()]);
    return before;
  }
}

function toCommand(r: Row): QueuedCommand {
  return {
    seq: r.seq,
    clientCommandId: r.client_command_id,
    type: r.type,
    entityId: r.entity_id,
    payload: JSON.parse(r.payload),
    occurredAt: r.occurred_at,
    enteredAt: r.entered_at,
    baseVersion: r.base_version,
    shiftSessionId: r.shift_session_id,
    machineId: r.machine_id,
    status: r.status,
    message: r.message,
    conflictId: r.conflict_id,
    attempts: r.attempts,
  };
}
