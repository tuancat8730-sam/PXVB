import { strict as assert } from 'node:assert';
import { mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { DraftStore } from '../src/draft.ts';
import { fileDb } from './helpers.ts';

function tempFile(name: string): string {
  return join(mkdtempSync(join(tmpdir(), 'pxvb-tablet-')), name);
}

describe('DraftStore – nháp biểu mẫu tự lưu (FR-CC-065)', () => {
  it('lưu từng thay đổi trường và khôi phục sau khi mở lại file', () => {
    // Arrange
    const path = tempFile('draft.db');
    const store = new DraftStore(fileDb(path));
    store.migrate();

    // Act
    store.setField('form-1', 'qty', 12);
    store.setField('form-1', 'note', 'kiểm tra');
    const reopened = new DraftStore(fileDb(path));
    reopened.migrate();

    // Assert
    assert.deepEqual(reopened.load('form-1'), { qty: 12, note: 'kiểm tra' });
  });

  it('trả null khi chưa có nháp và xóa được nháp', () => {
    // Arrange
    const store = new DraftStore(fileDb(tempFile('draft.db')));
    store.migrate();
    store.setField('form-1', 'qty', 3);

    // Act
    store.clear('form-1');

    // Assert
    assert.equal(store.load('form-1'), null);
    assert.equal(store.load('form-2'), null);
  });
});
