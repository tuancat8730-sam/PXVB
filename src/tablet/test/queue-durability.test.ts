import { strict as assert } from 'node:assert';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { CommandQueue } from '../src/queue.ts';
import { fileDb, memoryDb } from './helpers.ts';

function tempFile(name: string): string {
  return join(mkdtempSync(join(tmpdir(), 'pxvb-tablet-')), name);
}

describe('CommandQueue – thứ tự theo thời điểm phát sinh (FR-CC-064)', () => {
  it('trả lệnh theo occurredAt trước, rồi theo thứ tự nhập', () => {
    // Arrange
    const queue = new CommandQueue(memoryDb());
    queue.migrate();
    queue.enqueue({ type: 'X', payload: { n: 1 }, occurredAt: '2026-11-03T08:10:00.000Z' });
    queue.enqueue({ type: 'X', payload: { n: 2 }, occurredAt: '2026-11-03T08:05:00.000Z' });

    // Act
    const order = queue.pending(10).map((c) => (c.payload as { n: number }).n);

    // Assert
    assert.deepEqual(order, [2, 1]);
  });
});

describe('CommandQueue – phục hồi sau mất điện (FR-CC-065)', () => {
  it('giữ nguyên lệnh chờ và lệnh đang gửi dở khi mở lại file', () => {
    // Arrange
    const path = tempFile('queue.db');
    const before = new CommandQueue(fileDb(path));
    before.migrate();
    const sending = before.enqueue({ type: 'X', payload: { n: 1 } });
    const waiting = before.enqueue({ type: 'X', payload: { n: 2 } });
    before.markSyncing([sending]);

    // Act: mở lại file như sau khi khởi động lại máy
    const after = new CommandQueue(fileDb(path));
    after.migrate();
    const recovered = after.recoverInterrupted();

    // Assert
    assert.equal(recovered, 1);
    assert.deepEqual(after.pending(10).map((c) => c.clientCommandId), [sending, waiting]);
  });

  it('không ghi lệnh dở giữa giao dịch khi tắt đột ngột', () => {
    // Arrange
    const path = tempFile('queue.db');
    const db = fileDb(path);
    const queue = new CommandQueue(db);
    queue.migrate();
    queue.enqueue({ type: 'X', payload: { n: 1 } });

    // Act: mất điện giữa giao dịch
    assert.throws(() =>
      db.transaction(() => {
        queue.enqueue({ type: 'X', payload: { n: 2 } });
        throw new Error('mất điện');
      }),
    );

    // Assert
    const reopened = new CommandQueue(fileDb(path));
    reopened.migrate();
    assert.equal(reopened.unsyncedCount(), 1);
  });
});
