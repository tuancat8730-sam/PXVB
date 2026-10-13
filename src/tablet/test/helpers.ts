import { DatabaseSync } from 'node:sqlite';
import type { SqlDb } from '../src/db.ts';

/** `SqlDb` trên `node:sqlite` (bộ nhớ) cho kiểm thử. */
export function memoryDb(): SqlDb {
  const sqlite = new DatabaseSync(':memory:');
  let depth = 0;
  return {
    exec: (sql) => sqlite.exec(sql),
    run: (sql, params = []) => {
      sqlite.prepare(sql).run(...(params as never[]));
    },
    all: <T,>(sql: string, params: unknown[] = []) => sqlite.prepare(sql).all(...(params as never[])) as T[],
    transaction<T>(fn: () => T): T {
      const outer = depth === 0;
      if (outer) sqlite.exec('BEGIN');
      depth++;
      try {
        const result = fn();
        depth--;
        if (outer) sqlite.exec('COMMIT');
        return result;
      } catch (e) {
        depth--;
        if (outer) sqlite.exec('ROLLBACK');
        throw e;
      }
    },
  };
}

interface WireCommand {
  clientCommandId: string;
  type: string;
  payload: { value?: string } | null;
}

/** Máy chủ giả: idempotent theo clientCommandId, trạng thái theo `payload.value`. */
export class FakeServer {
  readonly pushRequests: WireCommand[][] = [];
  readonly processed = new Map<string, string>();
  online = true;
  authorized = true;
  retryOnce = new Set<string>();
  fullResources: string[] = [];
  data: Record<string, Array<{ id: string; [k: string]: unknown }>> = {};
  pullCalls: string[] = [];

  fetch: typeof fetch = async (input, init) => {
    if (!this.online) throw new TypeError('network down');
    const url = new URL(String(input));
    if (!this.authorized) return json({}, 401);
    if (url.pathname === '/api/v1/sync/push') {
      const body = JSON.parse(String(init?.body)) as { commands: WireCommand[] };
      this.pushRequests.push(body.commands);
      const results = body.commands.map((c) => this.process(c));
      return json({ results, serverTime: new Date().toISOString(), clockSkewWarning: false });
    }
    if (url.pathname === '/api/v1/sync/pull/manifest') {
      return json({
        serverTime: '2026-10-13T00:00:00Z',
        resources: Object.keys(this.data),
        fullResources: this.fullResources,
      });
    }
    if (url.pathname === '/api/v1/sync/pull') {
      this.pullCalls.push(url.search);
      const resource = url.searchParams.get('resource') ?? '';
      const limit = Number(url.searchParams.get('limit') ?? 500);
      const cursor = url.searchParams.get('cursor');
      const all = this.data[resource] ?? [];
      const start = cursor ? all.findIndex((r) => r.id === cursor) + 1 : 0;
      const slice = all.slice(start, start + limit);
      const next = start + limit < all.length ? slice[slice.length - 1].id : null;
      return json({ resource, records: slice, nextCursor: next, serverTime: `2026-10-13T00:00:0${this.pullCalls.length}Z` });
    }
    return json({}, 404);
  };

  private process(c: WireCommand) {
    if (this.processed.has(c.clientCommandId)) {
      return { clientCommandId: c.clientCommandId, status: 'DUPLICATE', message: 'Đã xử lý trước đó' };
    }
    const value = c.payload?.value;
    if (this.retryOnce.has(c.clientCommandId)) {
      this.retryOnce.delete(c.clientCommandId);
      return { clientCommandId: c.clientCommandId, status: 'REJECTED', message: 'Lỗi tạm thời', retryable: true };
    }
    if (value === 'CONFLICT') {
      this.processed.set(c.clientCommandId, 'CONFLICT');
      return { clientCommandId: c.clientCommandId, status: 'CONFLICT', message: 'Phiên bản đã đổi', conflictId: 'conf-1' };
    }
    if (value === 'INVALID') {
      this.processed.set(c.clientCommandId, 'REJECTED');
      return { clientCommandId: c.clientCommandId, status: 'REJECTED', message: 'Không hợp lệ' };
    }
    this.processed.set(c.clientCommandId, 'APPLIED');
    return { clientCommandId: c.clientCommandId, status: 'APPLIED' };
  }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}
