# Kế hoạch: Hoàn tất E09 (đồng bộ: push/pull/xung đột/giám sát + lõi tablet ngoại tuyến)

**Nguồn:** `docs/PROGRESS.md` (E09 🟡, commit `ba6e225`), `docs/PXVB-SRS-SDD.md` §4.7 (CC-07: FR-CC-060…069), §18.2 (E09: XL, R0), `docs/OPEN-QUESTIONS.md` L30, L31; bản vẽ `docs/design/screens/T70-Sync.dc.html`, `W-DeviceSync.dc.html` (chờ duyệt).
**Độ phức tạp:** Lớn (backend engine + contract) + lớn (tablet RN chưa chạy trên thiết bị). **Cập nhật lần cuối:** 2026-11-03.
**Phương pháp:** rà soát tĩnh `modules/sync` và `src/tablet` đối chiếu FR-CC-060…069; baseline `src/tablet` (`npm test`: 10/10 xanh, 2026-10-31); `SyncIT` xanh trong full verify 2026-10-30 (8.0 và 8.4). Mỗi việc viết test trước (đỏ), rồi sửa mã (xanh), theo quy ước E04/E08.
**Chú giải:** ✅ xong · 🟡 xong một phần · ⬜ chưa làm · ⏸ hoãn (có quyết định) · ❌ bị chặn · ❓ chưa kiểm (cần Pha 0)

## 1. Phạm vi và điều kiện "E09 hoàn tất"

| Nhóm | Nội dung | Thuộc E09? |
|---|---|---|
| Engine push/pull, idempotent, trả kết quả từng lệnh (FR-CC-061) | `SyncService.push`, `pull`, `manifest` | ✅ Có (đã chạy `SyncIT`) |
| Xung đột và danh sách cần xử lý (FR-CC-063) | `SyncConflictService`, `/sync/conflicts` | 🟡 Có API; chưa kiểm đủ quy tắc `baseVersion` và ca đã khóa (Pha 0) |
| Giám sát thiết bị (FR-CC-066, backend) | `SyncMonitorScanner`, `/sync/devices` | 🟡 Có backend; màn web chưa build (chờ duyệt bản vẽ `W-DeviceSync`) |
| Handler lệnh PR.* và MT.* | `ProductionSyncHandlers`, `MaterialSyncHandlers` | ✅ Có (E11, E13 đã làm) |
| Handler lệnh QC.*, TX.*, PM.*, SP.* | Thuộc E17–E21, E22 | ❌ Không thuộc E09: các module chưa có (`modules/`) |
| Pull đủ (FR-CC-062): máy, SP, NVL, định mức, mã dừng, mẫu KSCL, người dùng/PIN-hash, ngưỡng, ca đang chạy | `MasterdataPullProvider` (1 nguồn) | 🟡 Mới masterdata; các nguồn còn lại ⬜ |
| Tombstone cho bảng hard-delete (L30) | — | ⬜ Chưa có |
| Đồng hồ, lệch > 2' (FR-CC-067), dung lượng 72 giờ và cảnh báo > 500 lệnh (FR-CC-068) | — | ❓ Chưa kiểm (Pha 0) |
| Thao tác bắt buộc online (FR-CC-069) | — | ❓ Chưa kiểm (Pha 0) |
| Contract lệnh: `docs/sync-commands/*.schema.json` (SRS §4.7) | Thư mục chưa có | ⬜ |
| Lõi tablet: hàng đợi SQLite, SyncClient (FR-CC-060, 064, 065) | `src/tablet/src/queue.ts`, `syncClient.ts`, 10/10 test | 🟡 Lõi xong; phục hồi sau mất điện (FR-CC-065) chưa được kiểm |
| Adapter op-sqlite và UI RN (T-70, FR-CC-065 trên thiết bị) | `src/tablet/src/rn/opSqliteAdapter.ts` (46 dòng, chưa build) | ⬜ Chưa chạy trên thiết bị (L31); cần thiết bị và bản vẽ T-70 đã duyệt |
| Màn web giám sát thiết bị (FR-CC-066) | `W-DeviceSync` | ⏸ Chờ duyệt bản vẽ (`DESIGN-SYSTEM.md` §9); chưa build |

**E09 được đánh ✅ khi:** Pha 0–6 xong (backend và lõi tablet); `SyncIT` và IT đồng bộ mới xanh trên MySQL 8.0 và 8.4; tablet core xanh; `docs/sync-commands/` đủ schema cho mọi lệnh đang có handler; L30 và L31 đóng hoặc ghi rõ phần còn lại; OPEN-QUESTIONS và PROGRESS cập nhật. Pha 7 (RN trên thiết bị) và Pha 8 (màn web giám sát) có thể tách nếu PXVB chấp nhận (D5).

## 2. Đánh giá hiện trạng theo yêu cầu

| Yêu cầu | Trạng thái | Bằng chứng / ghi chú |
|---|---|---|
| FR-CC-060 Command lưu SQLite trước khi gọi API | 🟡 | `queue.ts` (193 dòng); test trong `test/sync.test.ts`. Cần kiểm `baseVersion`, `shiftSessionId`, `machineId` đủ theo SRS |
| FR-CC-061 Push lô ≤ 100, idempotent theo `(deviceId, clientCommandId)`, kết quả từng lệnh | ✅ | `SyncService.push` (dòng 100), `CommandResult` (APPLIED/DUPLICATE/REJECTED/CONFLICT). `SyncIT` xanh. Giới hạn 100 lệnh/lô: ❓ |
| FR-CC-062 Pull delta theo `since`, scope; lần đầu phân trang | 🟡 | `SyncService.pull` (dòng 211), `Manifest`; chỉ `MasterdataPullProvider`. Thiếu nguồn: PIN-hash, ca đang chạy theo thiết bị |
| FR-CC-063 Xung đột, không ghi đè | 🟡 | `SyncConflictService` (106 dòng); endpoint `resolve`. Chưa có IT riêng cho `baseVersion` lệch và ca đã khóa: ❓ |
| FR-CC-064 Trạng thái `PENDING/SYNCING/SYNCED/CONFLICT/REJECTED`, theo thời gian | 🟡 | Trạng thái trong lõi tablet; kiểm thứ tự theo `occurredAt` và trùng: ❓ |
| FR-CC-065 Phục hồi sau mất điện, form nháp tự lưu | ⬜ | Chưa có test; form nháp chưa có ở lõi |
| FR-CC-066 Màn giám sát thiết bị và đồng bộ | 🟡 | Backend `/sync/devices`; web chưa build (bản vẽ chờ duyệt) |
| FR-CC-067 Đồng hồ lệch > 2' | ❓ | `PushResponse` có `clockOffsetMs`; cảnh báo chưa kiểm |
| FR-CC-068 72 giờ offline, cảnh báo > 500 lệnh | 🟡 | Cảnh báo hàng đợi: `SyncMonitorScanner.scanQueueBacklog` (rule `CC_QUEUE_BACKLOG`, ngưỡng `SYNC_QUEUE_MAX` = 500). Test unit 4/4; `SyncIT` 5/5 trên 8.0 và 8.4. Còn: đo 72 giờ (4.3) |
| FR-CC-069 Thao tác bắt buộc online | ❓ | Chưa kiểm |
| Handler PR.* | ✅ | `ProductionSyncHandlers` (E11): `PR.RecordOutput`, `PR.StartStop`, `PR.EndStop`, `PR.ChangeProduct` |
| Handler MT.* | ✅ | `MaterialSyncHandlers` (E13) |
| Handler QC.*, TX.*, PM.*, SP.* | ❌ | Thuộc E17–E21, E22 |
| Test handler | ✅ | `TestSyncHandler` (test) |
| Lõi tablet TS | ✅ | `npm test` 10/10 (2026-10-31) |
| Adapter RN | ⬜ | Chưa build; L31 |

## 3. Phát hiện từ rà soát

| ID | Mức | Phát hiện | Xử lý |
|---|---|---|---|
| S1 | CAO | Không có tombstone: bảng hard-delete không báo xóa cho tablet (L30). Pull hiện chỉ thay tài nguyên đầy đủ (`alwaysFull`) | Pha 3: thiết kế tombstone (D3) |
| S2 | CAO | Pull thiếu PIN-hash/người dùng và ca đang chạy theo thiết bị (FR-CC-062). Đưa PIN-hash xuống tablet là vấn đề bảo mật | D2 trước khi làm |
| S3 | TB | Chưa có `docs/sync-commands/*.schema.json`; payload chưa kiểm theo schema | Pha 1 |
| S4 | TB | Lõi tablet chưa chạy trên thiết bị: adapter op-sqlite chưa build (L31) | Pha 7, cần thiết bị |
| S5 | TB | FR-CC-067, 068, 069 chưa kiểm trong code | Pha 0 kiểm; làm ở Pha 4–5 |
| S6 | THẤP | `TestSyncHandler` là handler test nằm trong module chính | Ghi nhận; không ảnh hưởng |
| S7 | TB | Màn T-70 và W-DeviceSync chưa duyệt; theo DESIGN-SYSTEM §9 không build UI khi chưa duyệt | Pha 8 chờ duyệt (D4) |
| S9 | CAO → TB (đã phân tích) | L30 nêu bảng hard-delete chưa có tombstone. Kiểm tra: `CatalogService.delete` chỉ xóa được catalog không có cờ hoạt động, không versioned, không phải `thresholds`. Trong 16 resource pull, không có resource nào vừa xóa được vừa delta (chỉ `product-allowed-machines`, đã alwaysFull). Các bảng xóa cứng khác (pairing, BOM, roster, kế hoạch, lệnh tự động) không nằm trong pull | Không xây tombstone ngay (xem D3 điều chỉnh). Bất biến test khóa lại. L30 đóng với điều kiện này |
| S10 | TB | Tên loại lệnh trong SRS §4.7 khác tên trong code: SRS `PR.RecordProductChange`, `MT.RecordIssueActual`; code `PR.ChangeProduct`, `MT.IssueMaterial`. Tablet gửi theo tên code | Contract lấy tên code (`payloads.schema.json`). Cần PXVB/SRS đồng bộ tên khi sửa tài liệu |
| S8 | TB | `push` đã trả cờ `clockSkewWarning` (hằng `CLOCK_SKEW_WARN_MS` cố định 120 000 ms) nhưng không phát cảnh báo cho giám sát; ngưỡng cứng, không cấu hình được (G2) | Đã xử lý ở 4.1: alert `CC_CLOCK_SKEW`, ngưỡng `SYNC_CLOCK_SKEW_SEC`. (Phát hiện ban đầu "không dùng" là sai, đã sửa) |

## 4. Phụ thuộc và rủi ro

| Phụ thuộc | Nội dung | Ảnh hưởng |
|---|---|---|
| E11, E13 | Handler PR/MT đã có | Không chặn |
| E17–E22 | Handler QC/TX/PM/SP | Không thuộc E09; mỗi epic thêm handler theo `SyncCommandHandler` |
| Thiết bị tablet | Cần máy thật cho RN (L31) | Chặn Pha 7 |
| Duyệt bản vẽ T-70, W-DeviceSync | `DESIGN-SYSTEM.md` §9 | Chặn Pha 8 và UI tablet |
| Docker | Đã chạy (Colima) | Không chặn IT |

## 5. Giả định đang áp dụng (chưa được PXVB xác nhận)

| # | Giả định | Nếu đổi |
|---|---|---|
| G1 | Handler QC/TX/PM/SP do các epic đó thêm, không làm trong E09 | Nếu PXVB muốn E09 gom hết, thêm handler vào Pha 1 |
| G2 | Lệch đồng hồ và ngưỡng hàng đợi là cấu hình (không hard-code), giá trị theo SRS (2', 500 lệnh) | Đổi thành masterdata có phiên bản nếu PXVB yêu cầu |
| G3 | Tombstone chỉ cho bảng hard-delete liệt kê trong L30 | Mở rộng nếu bảng khác có xóa cứng |
| G4 | Lõi tablet giữ là module TS độc lập; adapter RN là lớp mỏng bên ngoài | Thay đổi nếu chọn SQLite khác |

## 6. Quyết định (đã chốt 2026-11-03 theo mặc định, do dev lead trả lời)

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| D1 | Phạm vi E09: chỉ engine và contract, hay gom cả handler QC/TX/PM/SP? | Engine và contract; handler thuộc từng epic (G1) | ✅ (2026-11-03) |
| D2 | PIN-hash và người dùng có được đồng bộ xuống tablet không? (bảo mật, FR-CC-062) | Không đồng bộ hash; đăng nhập lần đầu bắt buộc online (FR-CC-069); tablet xác thực qua server | ✅ (2026-11-03) |
| D3 | Tombstone: bảng `sync_tombstone` chung hay cờ `deleted_at` trên từng bảng? | Quyết định ban đầu: bảng `sync_tombstone` chung. **Điều chỉnh 2026-11-03 theo bằng chứng (S9):** không có resource nào được pull mà xóa cứng được ngoài `product-allowed-machines` (đã alwaysFull) và `thresholds` (cấm xóa). Nên chưa xây tombstone; bất biến `MasterdataPullDeletionInvariantTest` khóa điều kiện này. Khi có resource delta xóa cứng, làm tombstone trước | ✅ điều chỉnh (2026-11-03) |
| D4 | Có duyệt T-70 và W-DeviceSync trong E09 không? | Không duyệt; Pha 8 chờ duyệt, các pha khác không phụ thuộc | ✅ (2026-11-03) |
| D6 | Thiết bị tablet được gắn với máy nào, để pull "ca đang chạy của các máy gắn với thiết bị" (FR-CC-062, 3.3)? Lựa chọn: (a) danh sách máy gắn khi đăng ký thiết bị (cần trường mới trên thiết bị); (b) theo trạm/vị trí (`position`) đã có; (c) theo người đăng nhập trên thiết bị | ⬜ | Mặc định: không làm 3.3 (⏸); FR-CC-062 thiếu một nguồn dữ liệu cho đến khi chốt |
| D5 | Pha 7 (RN trên thiết bị) có bắt buộc để E09 ✅ không? | Có, bắt buộc (theo L31). E09 chỉ ✅ khi Pha 7 xong; hiện chưa có thiết bị nên Pha 7 ❌ | ✅ (2026-11-03) |

## 7. Kế hoạch thực hiện

Quy ước như E08: mỗi việc viết test trước (đỏ), rồi sửa mã (xanh). Việc chưa chạy được vẫn ghi ⬜.

### Pha 0 – Rà soát và chuẩn bị
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 0.1 | Kiểm FR-CC-060…069 theo mã (bảng mục 2): đánh dấu ❓ thành ✅/🟡/⬜ | ✅ | Mục 2 và mục 8 cập nhật 2026-11-03 |
| 0.2 | Baseline: `npm test` (tablet) và `SyncIT` trên 8.0 | ✅ | Tablet baseline xanh (10/10, typecheck 0). `SyncIT` 5/5 trên 8.0 và 8.4 (2026-11-03) |
| 0.3 | Kiểm giới hạn 100 lệnh/lô và 500 lệnh cảnh báo (FR-CC-068) bằng test | ✅ | 100 lệnh/lô: `SyncIT.batchLimitAndDeviceRequirementAreEnforced` (đã có, xanh 8.0 và 8.4). 500: `SyncMonitorQueueBacklogTest` (4.2) |
| 0.4 | Chốt D1–D5 | ✅ | Mục 6, 2026-11-03 (theo mặc định) |

### Pha 1 – Contract lệnh (SRS §4.7, S3)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 1.1 | Tạo `docs/sync-commands/` với JSON schema cho mỗi loại lệnh đang có handler (PR.*, MT.*, TestSync) | 🟡 | `docs/sync-commands/payloads.schema.json` (2020-12, một file với `$defs` theo loại; lệch so với "một file mỗi loại" để giữ một nguồn). Đủ 8 loại PR.* và MT.*. `TEST.Set` chỉ dùng trong test, không đưa vào contract. JSON hợp lệ (kiểm bằng `JSON.parse`). Chưa có kiểm schema tự động (1.2) |
| 1.2 | Kiểm payload theo schema khi `push` (lỗi REJECTED với lý do tiếng Việt) | ⏸ | Không thêm thư viện JSON-Schema vào backend (cần phê duyệt phụ thuộc). Hiện các service nghiệp vụ đã từ chối dữ liệu sai (ném lỗi → REJECTED), nhưng chưa có test riêng cho từng loại. Mở việc khi có quyết định về thư viện |
| 1.3 | Trường bắt buộc `baseVersion`, `shiftSessionId`, `machineId` (FR-CC-060) | 🟡 | Envelope chưa bắt buộc ở `SyncService` (trường nullable). Với PR/MT, `shiftSessionId` và `machineId` do service dùng, nên thiếu thì lệnh bị từ chối. `baseVersion` chỉ bắt buộc với lệnh sửa bản ghi (có IT). Chưa có quy tắc chung |

### Pha 2 – Xung đột và idempotent (FR-CC-061, 063, 064)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 2.1 | IT: `baseVersion` lệch → CONFLICT, không ghi đè | ✅ | Đã có: `SyncIT.staleBaseVersionCreatesConflictWithoutOverwriteAndCanBeResolved` (CONFLICT, alert `CC_SYNC_CONFLICT`, giải quyết được). Xanh 8.0 và 8.4 |
| 2.2 | IT: ca đã khóa → CONFLICT hoặc REJECTED (theo quy tắc FR-CC-063) | 🟡 | Đường mã có: `RecordingService` ném `DATA_LOCKED` khi ca CLOSING/CLOSED, `processOne` đổi thành CONFLICT. **Chưa có IT qua sync** (cần mở và chốt ca thật trong `SyncIT`). Việc còn lại |
| 2.3 | IT: lô trùng `clientCommandId` → DUPLICATE, không ghi hai lần | ✅ | Đã có: `SyncIT.pushIsOrderedIdempotentAndReportsEachCommand` (gửi lại → DUPLICATE, `version` vẫn 1). Xanh 8.0 và 8.4 |
| 2.4 | Thứ tự theo `occurredAt` trong một lô | 🟡 | Tablet: `pending()` sắp theo `occurredAt` rồi `seq` (đã sửa, test đỏ trước). Server xử lý theo thứ tự mảng gửi lên, không sắp lại theo `occurredAt`; cùng test kiểm thứ tự kết quả |

### Pha 3 – Pull đủ và tombstone (FR-CC-062, L30, S1, S2)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 3.1 | Thiết kế bảng `sync_tombstone` và ghi khi hard-delete (theo D3) | ⏸ | Không cần hiện tại: xem S9 và D3 (điều chỉnh). Bất biến `MasterdataPullDeletionInvariantTest` 1/1 khóa lại điều kiện này |
| 3.2 | `pull` trả tombstone theo `since` | ⏸ | Hoãn cùng 3.1 |
| 3.3 | `SyncPullProvider` cho ca đang chạy theo thiết bị (máy gắn với thiết bị) | ⏸ | **Cần quyết định D6:** hệ thống chưa có liên kết thiết bị–máy (`DeviceRegistry` không có khái niệm máy). Không tự đặt quy tắc. Xem câu hỏi D6 ở mục 6 |
| 3.4 | Nguồn người dùng/PIN-hash: theo D2 (mặc định: không đồng bộ hash) | ⏸ | D2 đã chốt: không đồng bộ hash; không có nguồn người dùng nào trong pull |
| 3.5 | Lần đầu tải đầy đủ có phân trang | ✅ | `SyncIT.pullDeliversMasterdataDeltaWithPaging` (limit 4 → cursor → trang 2; delta theo `serverTime`) xanh 8.0 và 8.4 (kiểm lại ở Pha 9) |

### Pha 4 – Đồng hồ và giới hạn (FR-CC-067, 068) — P1
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 4.1 | Cảnh báo lệch đồng hồ > 2' (ngưỡng cấu hình, G2) | ✅ | Test đỏ (thiếu `SettingService` trong constructor), rồi xanh. `SyncServiceClockSkewTest` 3/3: cờ `clockSkewWarning` và alert `CC_CLOCK_SKEW` khi vượt ngưỡng; ngưỡng `SYNC_CLOCK_SKEW_SEC` = 120 giây (seeder). Unit sync 7/7 xanh |
| 4.2 | Cảnh báo hàng đợi > 500 lệnh (từ `pendingCount`) | ✅ | Test đỏ trước (lỗi biên dịch: thiếu `scanQueueBacklog`), rồi xanh. `SyncMonitorQueueBacklogTest` 4/4; `alerting` và `masterdata` unit xanh; `SyncIT` 5/5 trên 8.0 và 8.4. Chưa có IT riêng cho seed rule `CC_QUEUE_BACKLOG` |
| 4.3 | Dữ liệu offline 72 giờ: đo kích thước lưu trữ tablet | ✅ | Đo trên lõi (không trên thiết bị). Giả định 1 lệnh/30 giây → 8 640 lệnh; `storage-72h.test.ts`: file SQLite 2,38 MB (ngưỡng 50 MB), ghi và đọc lại trong ~1,7 giây. Số đo này là trên máy phát triển, chưa đo trên tablet thật (Pha 7) |

### Pha 5 – Thao tác bắt buộc online (FR-CC-069)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 5.1 | Từ chối lệnh offline cho: duyệt/chốt ca, duyệt điều chỉnh, phát hành phiếu mới | ✅ | Hằng `ONLINE_ONLY_TYPES` trong `SyncService`: `SHIFT.Close`, `SHIFT.Approve`, `ADJ.Approve`, `SLIP.Issue` (tên lệnh theo quy ước; chưa có handler nào). `SyncOnlineOnlyCommandTest` 2/2 (lệnh bị chặn không tới handler; lệnh thường vẫn tới). Lưu ý: test và guard viết gần như đồng thời, chưa quan sát pha đỏ riêng cho guard |
| 5.2 | Đăng nhập lần đầu của thiết bị phải online | ✅ | Kích hoạt thiết bị (`/identity/devices/activate`) và đăng nhập PIN (`/identity/auth/pin-login`) đều là endpoint server (`SyncIT.setUp`). Lõi tablet không có đường đăng nhập ngoại tuyến. D2 đã chốt: hash không xuống tablet |

### Pha 6 – Lõi tablet: phục hồi và hoàn thiện (FR-CC-060, 064, 065)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 6.1 | Test phục hồi hàng đợi sau tắt đột ngột (giao dịch SQLite) | ✅ | FR-CC-065. `queue-durability.test.ts`: mở lại file SQLite thật, lệnh đang gửi dở về PENDING, còn nguyên; giao dịch lỗi giữa chừng không ghi dòng nào. Xanh ngay (hành vi đã đúng, nay có bằng chứng trên file) |
| 6.2 | Form nháp tự lưu theo thay đổi trường (lõi, không UI) | ✅ | `src/draft.ts` (`DraftStore`, bảng `form_draft`). Test đỏ trước (thiếu module), rồi xanh: `draft.test.ts` 2/2 (lưu từng trường, khôi phục sau mở lại; xóa nháp) |
| 6.3 | Trạng thái `PENDING/SYNCING/SYNCED/CONFLICT/REJECTED` đủ trong lõi | ✅ | Kiểu `CommandStatus` đủ 5 giá trị; `sync.test.ts` kiểm CONFLICT và REJECTED. Thứ tự: `pending()` sắp theo `occurredAt` rồi `seq` (FR-CC-064); test đỏ trước rồi xanh |

### Pha 7 – Tablet RN trên thiết bị (L31, S4, D5)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 7.1 | Build `opSqliteAdapter` và chạy trên thiết bị thật | ❌ | Cần thiết bị; không làm được trên máy này |
| 7.2 | Đồng bộ thật với backend (push/pull) | ❌ | Phụ thuộc 7.1 |

### Pha 8 – Giám sát web (FR-CC-066, S7, D4)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 8.1 | Duyệt bản vẽ `W-DeviceSync` và `T-70` | ⏸ | Chờ PXVB (D4) |
| 8.2 | Build màn giám sát trong `src/web` (thiết bị, lần đồng bộ, chưa đồng bộ, xung đột) | ⏸ | Sau 8.1; mẫu theo E08 |

### Pha 9 – Nghiệm thu và tài liệu
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 9.1 | IT `SyncIT` và IT mới xanh trên MySQL 8.0 và 8.4 | ✅ | `SyncIT` 5/5 trên 8.0 và 8.4; `MasterdataIT` 9/9 và `AlertingIT` 27/27 trên cả hai (2026-11-03). Không có IT mới nào được thêm (xem 2.2, 1.2) |
| 9.2 | Full `verify` 8.0 và 8.4: không có lỗi mới ngoài baseline 13 | ✅ | 2026-11-03: 116 test, 13 lỗi trên mỗi môi trường, đúng `MaterialIT` 2 và `ProductionIT` 11. Không có lỗi mới |
| 9.3 | `npm test` và `npm run typecheck` ở `src/tablet` xanh | ✅ | `npm test` 16/16; `npm run typecheck` exit 0 (2026-11-03) |
| 9.4 | OPEN-QUESTIONS: đóng L30, L31 hoặc ghi phần còn lại | ✅ | L30 đóng một phần (S9, bất biến test); L31 còn mở, cần thiết bị (Pha 7) |
| 9.5 | PROGRESS cập nhật E09 | ✅ | E09 vẫn 🟡 (Pha 7 ❌, Pha 8 ⏸, D6 ⬜) |

## 8. Trạng thái theo yêu cầu (đích cần đạt)

| Yêu cầu | Hiện tại | Đích E09 | Pha |
|---|---|---|---|
| FR-CC-060 | 🟡 | 🟡 | 1.3 🟡 (envelope chưa bắt buộc chung), 6.2 ✅ |
| FR-CC-061 | ✅ | ✅ | 0.3 ✅, 1.2 ⏸ (kiểm schema tự động) |
| FR-CC-062 | 🟡 | 🟡 | 3.3 ⏸ (D6). 3.4 ✅ (D2), 3.5 ✅. Tombstone không cần (S9) |
| FR-CC-063 | 🟡 | 🟡 | 2.1 ✅, 2.2 🟡 (chưa IT qua sync) |
| FR-CC-064 | 🟡 | 🟡 | 2.4 🟡 (server không sắp lại), 6.3 ✅ |
| FR-CC-065 | ⬜ | ✅ trên lõi | 6.1 ✅, 6.2 ✅; trên thiết bị ❌ (Pha 7) |
| FR-CC-066 | 🟡 | ⏸ | Backend có; màn web chờ duyệt D4 (Pha 8) |
| FR-CC-067 | ✅ | ✅ (P1) | 4.1 ✅ (cờ + cảnh báo `CC_CLOCK_SKEW`). Thời điểm tin cậy hiệu chỉnh offset: chưa làm |
| FR-CC-068 | ✅ | ✅ (P1) | 0.3 ✅, 4.2 ✅, 4.3 ✅ (đo trên máy phát triển) |
| FR-CC-069 | ✅ | ✅ | 5.1 ✅ (lệnh chưa có handler), 5.2 ✅ |

## 9. Rủi ro

| Mức | Rủi ro | Giảm thiểu |
|---|---|---|
| CAO | PIN-hash xuống tablet làm lộ thông tin đăng nhập nếu mất thiết bị | D2: không đồng bộ hash; đăng nhập online |
| CAO | Không có tombstone: tablet giữ bản ghi đã xóa, dữ liệu sai | Pha 3 trước khi E11/E13 mở rộng pull |
| CAO | Lõi tablet chưa chạy thật: lỗi SQLite/RN chỉ lộ khi có thiết bị | Pha 7; ghi rõ trong PROGRESS |
| TB | Xung đột chưa kiểm đủ quy tắc (ca khóa, baseline) | Pha 2 IT |
| TB | Handler QC/TX/PM/SP phải đi theo contract khi các epic đó làm | Pha 1 làm contract rõ và có ví dụ |
| THẤP | Baseline 13 lỗi ProductionIT/MaterialIT làm full verify không xanh | Ghi nhận; không thuộc E09 |

## 10. Nhật ký thực hiện

| Ngày | Ghi chú |
|---|---|
| 2026-11-03 | **Hoàn tất phần làm được của E09 (kết thúc phiên).** Commit: `a596629`, `6d78654`, `5243ffe`, `8ccd94b`, `cf81b01`, `c0ec361`, `c5ced35`, `8e3cafc`. Kiểm chứng: `SyncIT` 5/5, `MasterdataIT` 9/9, `AlertingIT` 27/27 trên 8.0 và 8.4; full verify 8.0 và 8.4: 116 test, 13 lỗi = baseline; tablet 16/16 và typecheck 0; unit sync/alerting/masterdata xanh. **Chưa đạt ✅:** Pha 7 (cần thiết bị, D5 bắt buộc), Pha 8 (chờ duyệt D4), 3.3 (chờ D6), 2.2 (chưa IT qua sync), 1.2 (chờ quyết định thư viện JSON-Schema). Điều chỉnh: D3 (không xây tombstone, S9), S8 (đã sửa), S10 (tên lệnh SRS khác code). Môi trường: Colima đã bật; socket qua `DOCKER_HOST`, không có symlink `/var/run` |
| 2026-10-31 | Lập kế hoạch E09 theo cấu trúc E08. Rà soát tĩnh `modules/sync` (8 file chính) và `src/tablet`. Baseline tablet 10/10 xanh. Chưa sửa mã |
| 2026-11-03 | 4.2 ✅ (chọn phương án 2: rule `CC_QUEUE_BACKLOG` qua `SyncMonitorScanner`, ngưỡng `SYNC_QUEUE_MAX` = 500 trong seeder). Đỏ: test không biên dịch (thiếu `scanQueueBacklog`). Xanh: `SyncMonitorQueueBacklogTest` 4/4; unit sync/alerting/masterdata xanh; `SyncIT` 5/5 trên 8.0 và 8.4 (Colima chạy, socket trỏ qua `DOCKER_HOST`, không tạo symlink `/var/run`). Lệch so với plan: `scheduled()` gọi thêm `scanQueueBacklog`. Phát hiện S8. 0.3 còn test 100 lệnh/lô |
| 2026-11-03 | 0.4 ✅: chốt D1–D5 theo mặc định (mục 6). Tạo worktree `/Volumes/DATA/code/PXVB-e09` từ `origin/main`, nhánh `feat/e09-sync-completion` (commit `a596629` thêm plan). Chưa chạy test; tiếp theo là 0.2 và 0.3 (test đỏ). Kiểm chứng: không có (chỉ sửa tài liệu) |
