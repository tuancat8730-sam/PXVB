# Lõi ngoại tuyến tablet PXVB (CC-07)

Thư viện TypeScript độc lập nền tảng cho ứng dụng tablet React Native (SDD §4.7, §15): hàng đợi lệnh SQLite,
máy khách đồng bộ, bản sao masterdata cục bộ.

| Thành phần | Mô tả | Kiểm thử |
|---|---|---|
| `src/uuid7.ts` | Khóa UUID v7 sinh phía thiết bị | Có |
| `src/queue.ts` | `CommandQueue`: lưu lệnh trước khi gọi API, giữ thứ tự, phục hồi sau mất điện | Có |
| `src/syncClient.ts` | `SyncClient`: push ≤ 100 lệnh/lô, idempotent, xử lý CONFLICT/REJECTED, pull delta phân trang | Có |
| `src/rn/opSqliteAdapter.ts` | Bộ chuyển `SqlDb` cho `@op-engineering/op-sqlite` | **Chưa chạy trên thiết bị** |

## Chạy kiểm thử

```bash
cd src/tablet
npm test            # node --test, dùng node:sqlite (Node ≥ 22.5)
npm run typecheck   # cần: npm install
```

## Tích hợp vào ứng dụng React Native

1. Mở DB: `open({ name: 'pxvb.db', encryptionKey })` → `createOpSqliteDb(connection)` (bật mã hóa nghỉ, SDD §16.3).
2. `queue = new CommandQueue(db); queue.migrate(); queue.recoverInterrupted();`
3. `client = new SyncClient({ baseUrl, getToken, appVersion }, queue, db); client.migrate();`
4. Mọi thao tác ghi: `queue.enqueue({ type: 'PR.RecordOutput', entityId, payload, baseVersion })` rồi hiển thị ngay
   (optimistic) với biểu tượng "chưa đồng bộ"; nền gọi `client.sync()` khi có mạng.
5. `AuthRequiredError` → chuyển màn hình đăng nhập PIN (hàng đợi giữ nguyên).

Chưa có: màn hình/UI React Native, đăng nhập PIN + kích hoạt thiết bị phía app, hàng đợi ảnh đính kèm, STOMP phía
tablet (nhận cảnh báo) — làm ở các epic giao diện.
