import assert from 'node:assert/strict';
import { beforeEach, describe, it } from 'node:test';
import { CommandQueue } from '../src/queue.ts';
import { AuthRequiredError, NetworkError, SyncClient } from '../src/syncClient.ts';
import { uuid7 } from '../src/uuid7.ts';
import { FakeServer, memoryDb } from './helpers.ts';

function setup(token: string | null = 'tok') {
  const db = memoryDb();
  const queue = new CommandQueue(db);
  queue.migrate();
  const server = new FakeServer();
  let currentToken = token;
  const client = new SyncClient(
    { baseUrl: 'http://pxvb.local', getToken: () => currentToken, appVersion: '1.0.0', fetchFn: server.fetch },
    queue,
    db,
  );
  client.migrate();
  return { db, queue, server, client, setToken: (t: string | null) => (currentToken = t) };
}

describe('uuid7', () => {
  it('tạo UUID v7 hợp lệ và tăng dần theo thời gian', () => {
    const a = uuid7(1_700_000_000_000);
    const b = uuid7(1_700_000_000_001);
    assert.match(a, /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    assert.ok(a < b);
  });
});

describe('CommandQueue', () => {
  it('giữ thứ tự phát sinh và sinh khóa duy nhất', () => {
    const { queue } = setup();
    const ids = [1, 2, 3].map((i) => queue.enqueue({ type: 'PR.RecordOutput', payload: { qty: i } }));
    assert.equal(new Set(ids).size, 3);
    assert.deepEqual(queue.pending(10).map((c) => (c.payload as { qty: number }).qty), [1, 2, 3]);
    assert.equal(queue.unsyncedCount(), 3);
  });

  it('khôi phục lệnh đang SYNCING sau khi tắt đột ngột', () => {
    const { queue } = setup();
    const id = queue.enqueue({ type: 'X', payload: {} });
    queue.markSyncing([id]);
    assert.equal(queue.pending(10).length, 0);
    assert.equal(queue.recoverInterrupted(), 1);
    assert.equal(queue.pending(10).length, 1);
    assert.equal(queue.get(id)?.attempts, 1);
  });
});

describe('SyncClient.push', () => {
  let ctx: ReturnType<typeof setup>;
  beforeEach(() => {
    ctx = setup();
  });

  it('gửi theo lô ≤ 100 đúng thứ tự và đánh dấu SYNCED', async () => {
    for (let i = 0; i < 150; i++) {
      ctx.queue.enqueue({ type: 'PR.RecordOutput', payload: { value: 'ok', i } });
    }
    const summary = await ctx.client.push();
    assert.equal(summary.sent, 150);
    assert.equal(summary.applied, 150);
    assert.deepEqual(ctx.server.pushRequests.map((b) => b.length), [100, 50]);
    const order = ctx.server.pushRequests.flat().map((c) => (c.payload as unknown as { i: number }).i);
    assert.deepEqual(order, [...Array(150).keys()]);
    assert.equal(ctx.queue.unsyncedCount(), 0);
    assert.equal(ctx.queue.byStatus('SYNCED').length, 150);
  });

  it('lỗi mạng giữ nguyên lệnh PENDING và gửi lại được', async () => {
    const id = ctx.queue.enqueue({ type: 'X', payload: { value: 'ok' } });
    ctx.server.online = false;
    await assert.rejects(ctx.client.push(), NetworkError);
    assert.equal(ctx.queue.get(id)?.status, 'PENDING');
    ctx.server.online = true;
    const summary = await ctx.client.push();
    assert.equal(summary.applied, 1);
    assert.equal(ctx.queue.get(id)?.status, 'SYNCED');
  });

  it('gửi lại cùng lệnh nhận DUPLICATE và vẫn coi là đã đồng bộ', async () => {
    const id = ctx.queue.enqueue({ type: 'X', payload: { value: 'ok' } });
    await ctx.client.push();
    ctx.queue.setResult(id, 'PENDING', null, null); // giả lập mất xác nhận phía tablet
    const summary = await ctx.client.push();
    assert.equal(summary.duplicates, 1);
    assert.equal(ctx.queue.get(id)?.status, 'SYNCED');
  });

  it('ghi nhận CONFLICT, REJECTED và giữ lỗi tạm thời ở PENDING', async () => {
    const conflict = ctx.queue.enqueue({ type: 'X', payload: { value: 'CONFLICT' } });
    const invalid = ctx.queue.enqueue({ type: 'X', payload: { value: 'INVALID' } });
    const flaky = ctx.queue.enqueue({ type: 'X', payload: { value: 'ok' } });
    ctx.server.retryOnce.add(flaky);

    const first = await ctx.client.push();
    assert.equal(first.conflicts, 1);
    assert.equal(first.rejected, 1);
    assert.equal(first.retried, 1);
    assert.equal(ctx.queue.get(conflict)?.status, 'CONFLICT');
    assert.equal(ctx.queue.get(conflict)?.conflictId, 'conf-1');
    assert.equal(ctx.queue.get(invalid)?.status, 'REJECTED');
    assert.equal(ctx.queue.get(flaky)?.status, 'PENDING');

    const second = await ctx.client.push();
    assert.equal(second.applied, 1);
    assert.equal(ctx.queue.get(flaky)?.status, 'SYNCED');
  });

  it('401 yêu cầu đăng nhập lại và không làm mất lệnh', async () => {
    const id = ctx.queue.enqueue({ type: 'X', payload: { value: 'ok' } });
    ctx.server.authorized = false;
    await assert.rejects(ctx.client.push(), AuthRequiredError);
    assert.equal(ctx.queue.get(id)?.status, 'PENDING');

    ctx.setToken(null);
    await assert.rejects(ctx.client.push(), AuthRequiredError);
  });
});

describe('SyncClient.pull', () => {
  it('kéo phân trang, lưu bản sao cục bộ và dùng since ở lần sau', async () => {
    const { client, server } = setup();
    server.data['machines'] = Array.from({ length: 5 }, (_, i) => ({ id: `m${i}`, code: `M${i}` }));
    server.data['stop-reasons'] = [{ id: 's1', code: 'A' }];
    const clientSmallPage = new SyncClient(
      { baseUrl: 'http://pxvb.local', getToken: () => 'tok', appVersion: '1', fetchFn: server.fetch, pullPageSize: 2 },
      // dùng lại cùng hàng đợi/DB qua client gốc không cần thiết: tạo độc lập
      new CommandQueue(memoryDb()),
      memoryDb(),
    );
    clientSmallPage.migrate();
    const summary = await clientSmallPage.pull();
    assert.equal(summary.records, 6);
    assert.equal(clientSmallPage.cached('machines').length, 5);
    assert.ok(server.pullCalls.some((q) => q.includes('cursor=')));

    server.pullCalls.length = 0;
    await clientSmallPage.pull();
    assert.ok(server.pullCalls.every((q) => q.includes('since=')));
    void client;
  });

  it('thay thế hoàn toàn tài nguyên luôn-đầy-đủ (không có tombstone)', async () => {
    const { client, server } = setup();
    server.fullResources = ['thresholds'];
    server.data['thresholds'] = [{ id: 't1' }, { id: 't2' }];
    await client.pull();
    assert.equal(client.cached('thresholds').length, 2);

    server.data['thresholds'] = [{ id: 't2' }];
    await client.pull();
    assert.deepEqual(client.cached<{ id: string }>('thresholds').map((r) => r.id), ['t2']);
  });
});
