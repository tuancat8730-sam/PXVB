import { strict as assert } from 'node:assert';
import { mkdtempSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { CommandQueue } from '../src/queue.ts';
import { fileDb } from './helpers.ts';

/** FR-CC-068: tối thiểu lưu được 72 giờ thao tác offline (≈ 3 ca). */
const OFFLINE_HOURS = 72;
/** Giả định đo: một lệnh mỗi 30 giây khi làm việc liên tục (xem plan, mục 7, 4.3). */
const SECONDS_PER_COMMAND = 30;
const COMMANDS = (OFFLINE_HOURS * 3600) / SECONDS_PER_COMMAND;
/** Ngưỡng dung lượng file cho 72 giờ trên tablet (MB). */
const MAX_FILE_MB = 50;
const BYTES_PER_MB = 1_048_576;
const START = Date.parse('2026-11-01T00:00:00Z');

describe('CommandQueue – dung lượng 72 giờ offline (FR-CC-068)', () => {
  it('lưu và đọc lại đủ 72 giờ lệnh trên file SQLite trong ngưỡng dung lượng', (t) => {
    // Arrange
    const path = join(mkdtempSync(join(tmpdir(), 'pxvb-tablet-')), 'queue.db');
    const queue = new CommandQueue(fileDb(path));
    queue.migrate();

    // Act
    for (let i = 0; i < COMMANDS; i++) {
      queue.enqueue({
        type: 'PR.RecordOutput',
        payload: { qty: i, note: 'kiểm tra đầu ca, số liệu sản lượng theo giờ' },
        occurredAt: new Date(START + i * SECONDS_PER_COMMAND * 1000).toISOString(),
      });
    }
    const sizeMb = statSync(path).size / BYTES_PER_MB;
    t.diagnostic(`72 giờ = ${COMMANDS} lệnh, file ${sizeMb.toFixed(2)} MB`);

    // Assert
    assert.equal(queue.unsyncedCount(), COMMANDS);
    assert.equal(queue.pending(COMMANDS).length, COMMANDS);
    assert.ok(sizeMb < MAX_FILE_MB, `file ${sizeMb.toFixed(2)} MB vượt ngưỡng ${MAX_FILE_MB} MB`);
  });
});
