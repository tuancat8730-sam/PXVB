import type { SqlDb } from './db.ts';
import type { CommandQueue, CommandStatus, QueuedCommand } from './queue.ts';

export class AuthRequiredError extends Error {
  constructor() {
    super('Cần đăng nhập lại để đồng bộ');
    this.name = 'AuthRequiredError';
  }
}

export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('Không kết nối được máy chủ');
    this.name = 'NetworkError';
    this.cause = cause;
  }
}

export interface SyncClientOptions {
  baseUrl: string;
  /** Access token hiện tại (đăng nhập PIN trên thiết bị); `null` nếu chưa đăng nhập. */
  getToken: () => string | null;
  appVersion: string;
  fetchFn?: typeof fetch;
  now?: () => Date;
  /** Cỡ lô tối đa (máy chủ cho phép 100). */
  batchSize?: number;
  pullPageSize?: number;
}

export interface PushSummary {
  sent: number;
  applied: number;
  duplicates: number;
  rejected: number;
  conflicts: number;
  retried: number;
  clockSkewWarning: boolean;
}

export interface PullSummary {
  resources: number;
  records: number;
}

interface PushResult {
  clientCommandId: string;
  status: 'APPLIED' | 'DUPLICATE' | 'REJECTED' | 'CONFLICT';
  message?: string | null;
  conflictId?: string | null;
  retryable?: boolean;
}

interface PushResponse {
  results: PushResult[];
  clockSkewWarning?: boolean;
}

interface Manifest {
  serverTime: string;
  resources: string[];
  fullResources?: string[];
}

interface PullPage {
  resource: string;
  records: Array<Record<string, unknown> & { id: string }>;
  nextCursor: string | null;
  serverTime: string;
}

/**
 * Máy khách đồng bộ (FR-CC-061/062): đẩy hàng đợi theo thứ tự (≤ 100 lệnh/lô) và kéo delta masterdata về bản sao
 * cục bộ. Không bao giờ bỏ lệnh khi lỗi mạng; 401 giữ nguyên hàng đợi và yêu cầu đăng nhập lại.
 */
export class SyncClient {
  private readonly opts: SyncClientOptions;
  private readonly queue: CommandQueue;
  private readonly db: SqlDb;
  private readonly fetchFn: typeof fetch;
  private readonly now: () => Date;
  private readonly batchSize: number;
  private readonly pullPageSize: number;

  constructor(opts: SyncClientOptions, queue: CommandQueue, db: SqlDb) {
    this.opts = opts;
    this.queue = queue;
    this.db = db;
    this.fetchFn = opts.fetchFn ?? fetch;
    this.now = opts.now ?? (() => new Date());
    this.batchSize = Math.min(opts.batchSize ?? 100, 100);
    this.pullPageSize = opts.pullPageSize ?? 500;
  }

  migrate(): void {
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS cache_record (
        resource TEXT NOT NULL,
        id TEXT NOT NULL,
        json TEXT NOT NULL,
        PRIMARY KEY (resource, id)
      );
      CREATE TABLE IF NOT EXISTS sync_state (
        resource TEXT PRIMARY KEY,
        since TEXT
      );
    `);
  }

  /** Đẩy toàn bộ hàng đợi; dừng khi hết lệnh hoặc có lỗi mạng/xác thực. */
  async push(): Promise<PushSummary> {
    const summary: PushSummary = {
      sent: 0, applied: 0, duplicates: 0, rejected: 0, conflicts: 0, retried: 0, clockSkewWarning: false,
    };
    for (;;) {
      const batch = this.queue.pending(this.batchSize);
      if (batch.length === 0) {
        return summary;
      }
      const ids = batch.map((c) => c.clientCommandId);
      this.queue.markSyncing(ids);
      let response: PushResponse;
      try {
        response = await this.post<PushResponse>('/api/v1/sync/push', {
          deviceTime: this.now().toISOString(),
          appVersion: this.opts.appVersion,
          pendingCount: this.queue.unsyncedCount(),
          commands: batch.map(toWire),
        });
      } catch (e) {
        this.queue.release(ids);
        throw e;
      }
      summary.sent += batch.length;
      summary.clockSkewWarning ||= response.clockSkewWarning === true;
      const handled = new Set<string>();
      for (const r of response.results) {
        handled.add(r.clientCommandId);
        this.applyResult(r, summary);
      }
      const missing = ids.filter((id) => !handled.has(id));
      if (missing.length > 0) {
        this.queue.release(missing);
        summary.retried += missing.length;
        return summary; // máy chủ không trả đủ kết quả: dừng để tránh vòng lặp vô hạn
      }
      if (response.results.some((r) => r.status === 'REJECTED' && r.retryable)) {
        return summary; // lỗi tạm thời: để lần đồng bộ sau
      }
    }
  }

  /** Kéo delta cho mọi tài nguyên trong manifest. */
  async pull(): Promise<PullSummary> {
    const manifest = await this.get<Manifest>('/api/v1/sync/pull/manifest');
    const full = new Set(manifest.fullResources ?? []);
    let records = 0;
    for (const resource of manifest.resources) {
      records += await this.pullResource(resource, full.has(resource));
    }
    return { resources: manifest.resources.length, records };
  }

  async sync(): Promise<{ push: PushSummary; pull: PullSummary }> {
    const push = await this.push();
    const pull = await this.pull();
    return { push, pull };
  }

  /** Đọc bản sao cục bộ của một tài nguyên (ví dụ danh sách máy, mã dừng) khi ngoại tuyến. */
  cached<T = Record<string, unknown>>(resource: string): T[] {
    return this.db
      .all<{ json: string }>(`SELECT json FROM cache_record WHERE resource = ? ORDER BY id`, [resource])
      .map((r) => JSON.parse(r.json) as T);
  }

  private async pullResource(resource: string, replaceAll: boolean): Promise<number> {
    const state = this.db.all<{ since: string | null }>(`SELECT since FROM sync_state WHERE resource = ?`, [resource]);
    const since = replaceAll ? null : state[0]?.since ?? null;
    let cursor: string | null = null;
    let firstServerTime: string | null = null;
    let count = 0;
    const seen = new Set<string>();
    do {
      const params = new URLSearchParams({ resource, limit: String(this.pullPageSize) });
      if (since) params.set('since', since);
      if (cursor) params.set('cursor', cursor);
      const page = await this.get<PullPage>(`/api/v1/sync/pull?${params.toString()}`);
      firstServerTime ??= page.serverTime;
      this.db.transaction(() => {
        for (const rec of page.records) {
          seen.add(rec.id);
          this.db.run(
            `INSERT INTO cache_record(resource, id, json) VALUES (?,?,?)
             ON CONFLICT(resource, id) DO UPDATE SET json = excluded.json`,
            [resource, rec.id, JSON.stringify(rec)],
          );
        }
      });
      count += page.records.length;
      cursor = page.nextCursor;
    } while (cursor);
    this.db.transaction(() => {
      if (replaceAll) {
        // Tài nguyên không có tombstone: xóa mọi bản ghi cục bộ không còn trên máy chủ
        for (const row of this.db.all<{ id: string }>(`SELECT id FROM cache_record WHERE resource = ?`, [resource])) {
          if (!seen.has(row.id)) {
            this.db.run(`DELETE FROM cache_record WHERE resource = ? AND id = ?`, [resource, row.id]);
          }
        }
      }
      this.db.run(
        `INSERT INTO sync_state(resource, since) VALUES (?,?)
         ON CONFLICT(resource) DO UPDATE SET since = excluded.since`,
        [resource, firstServerTime],
      );
    });
    return count;
  }

  private applyResult(r: PushResult, summary: PushSummary): void {
    switch (r.status) {
      case 'APPLIED':
        this.queue.setResult(r.clientCommandId, 'SYNCED', null, null);
        summary.applied++;
        break;
      case 'DUPLICATE':
        this.queue.setResult(r.clientCommandId, 'SYNCED', r.message ?? null, r.conflictId ?? null);
        summary.duplicates++;
        break;
      case 'CONFLICT':
        this.queue.setResult(r.clientCommandId, 'CONFLICT', r.message ?? null, r.conflictId ?? null);
        summary.conflicts++;
        break;
      case 'REJECTED':
        if (r.retryable) {
          this.queue.release([r.clientCommandId]);
          summary.retried++;
        } else {
          this.queue.setResult(r.clientCommandId, 'REJECTED' as CommandStatus, r.message ?? null, null);
          summary.rejected++;
        }
        break;
    }
  }

  private async get<T>(path: string): Promise<T> {
    return this.request<T>(path, { method: 'GET' });
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    return this.request<T>(path, {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    });
  }

  private async request<T>(path: string, init: RequestInit): Promise<T> {
    const token = this.opts.getToken();
    if (!token) {
      throw new AuthRequiredError();
    }
    let res: Response;
    try {
      res = await this.fetchFn(`${this.opts.baseUrl}${path}`, {
        ...init,
        headers: { ...(init.headers as Record<string, string> | undefined), Authorization: `Bearer ${token}` },
      });
    } catch (e) {
      throw new NetworkError(e);
    }
    if (res.status === 401) {
      throw new AuthRequiredError();
    }
    if (!res.ok) {
      throw new Error(`Máy chủ trả lỗi ${res.status}`);
    }
    return (await res.json()) as T;
  }
}

function toWire(c: QueuedCommand): Record<string, unknown> {
  return {
    clientCommandId: c.clientCommandId,
    type: c.type,
    entityId: c.entityId,
    payload: c.payload,
    occurredAt: c.occurredAt,
    enteredAt: c.enteredAt,
    baseVersion: c.baseVersion,
    shiftSessionId: c.shiftSessionId,
    machineId: c.machineId,
  };
}
