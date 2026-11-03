# Kế hoạch: Hoàn tất E09 (đồng bộ: push/pull/xung đột/giám sát + lõi tablet ngoại tuyến)

**Nguồn:** `docs/PROGRESS.md` (E09 🟡, commit `ba6e225`), `docs/PXVB-SRS-SDD.md` §4.7 (CC-07: FR-CC-060…069), §18.2 (E09: XL, R0), `docs/OPEN-QUESTIONS.md` L30, L31; bản vẽ `docs/design/screens/T70-Sync.dc.html`, `W-DeviceSync.dc.html` (chờ duyệt).
**Độ phức tạp:** Lớn (backend engine + contract) + lớn (tablet RN chưa chạy trên thiết bị). **Cập nhật lần cuối:** 2026-10-31.
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
| FR-CC-068 72 giờ offline, cảnh báo > 500 lệnh | ❓ | Chưa kiểm |
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

## 6. Quyết định cần PXVB/dev lead (chưa chốt)

| # | Câu hỏi | Mặc định nếu không có trả lời |
|---|---|---|
| D1 | Phạm vi E09: chỉ engine và contract, hay gom cả handler QC/TX/PM/SP? | Engine và contract; handler thuộc từng epic (G1) |
| D2 | PIN-hash và người dùng có được đồng bộ xuống tablet không? (bảo mật, FR-CC-062) | Không đồng bộ hash; đăng nhập lần đầu bắt buộc online (FR-CC-069); tablet xác thực qua server |
| D3 | Tombstone: bảng `sync_tombstone` chung hay cờ `deleted_at` trên từng bảng? | Bảng `sync_tombstone` chung, ghi khi hard-delete |
| D4 | Có duyệt T-70 và W-DeviceSync trong E09 không? | Không duyệt; Pha 8 chờ duyệt, các pha khác không phụ thuộc |
| D5 | Pha 7 (RN trên thiết bị) có bắt buộc để E09 ✅ không? | Có (theo L31); nếu PXVB chấp nhận tách thì E09 ✅ sau Pha 6 và Pha 7 thành epic riêng |

## 7. Kế hoạch thực hiện

Quy ước như E08: mỗi việc viết test trước (đỏ), rồi sửa mã (xanh). Việc chưa chạy được vẫn ghi ⬜.

### Pha 0 – Rà soát và chuẩn bị
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 0.1 | Kiểm FR-CC-060…069 theo mã (bảng mục 2): đánh dấu ❓ thành ✅/🟡/⬜ | ⬜ | Mục 2 |
| 0.2 | Baseline: `npm test` (tablet) và `SyncIT` trên 8.0 | 🟡 | Tablet 10/10 xanh (2026-10-31). `SyncIT` xanh trong full verify 2026-10-30 |
| 0.3 | Kiểm giới hạn 100 lệnh/lô và 500 lệnh cảnh báo (FR-CC-068) bằng test | ⬜ | Test đỏ trước |
| 0.4 | Chốt D1–D5 | ⬜ | Mục 6 |

### Pha 1 – Contract lệnh (SRS §4.7, S3)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 1.1 | Tạo `docs/sync-commands/` với JSON schema cho mỗi loại lệnh đang có handler (PR.*, MT.*, TestSync) | ⬜ | Đối chiếu `ProductionSyncHandlers`, `MaterialSyncHandlers` |
| 1.2 | Kiểm payload theo schema khi `push` (lỗi REJECTED với lý do tiếng Việt) | ⬜ | Test đỏ: payload thiếu trường → REJECTED |
| 1.3 | Trường bắt buộc `baseVersion`, `shiftSessionId`, `machineId` (FR-CC-060) | ⬜ | |

### Pha 2 – Xung đột và idempotent (FR-CC-061, 063, 064)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 2.1 | IT: `baseVersion` lệch → CONFLICT, không ghi đè | ⬜ | Test đỏ trước |
| 2.2 | IT: ca đã khóa → CONFLICT hoặc REJECTED (theo quy tắc FR-CC-063) | ⬜ | |
| 2.3 | IT: lô trùng `clientCommandId` → DUPLICATE, không ghi hai lần | ⬜ | |
| 2.4 | Thứ tự theo `occurredAt` trong một lô | ⬜ | |

### Pha 3 – Pull đủ và tombstone (FR-CC-062, L30, S1, S2)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 3.1 | Thiết kế bảng `sync_tombstone` và ghi khi hard-delete (theo D3) | ⬜ | Migration Flyway theo module |
| 3.2 | `pull` trả tombstone theo `since` | ⬜ | IT đỏ trước |
| 3.3 | `SyncPullProvider` cho ca đang chạy theo thiết bị (máy gắn với thiết bị) | ⬜ | Dùng `PlanningQueries`/`ProductionQueries` đã có |
| 3.4 | Nguồn người dùng/PIN-hash: theo D2 (mặc định: không đồng bộ hash) | ⏸ | Chờ D2 |
| 3.5 | Lần đầu tải đầy đủ có phân trang | ⬜ | |

### Pha 4 – Đồng hồ và giới hạn (FR-CC-067, 068) — P1
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 4.1 | Cảnh báo lệch đồng hồ > 2' (ngưỡng cấu hình, G2) | ⬜ | Test đỏ trước |
| 4.2 | Cảnh báo hàng đợi > 500 lệnh (từ `pendingCount`) | ⬜ | |
| 4.3 | Dữ liệu offline 72 giờ: đo kích thước lưu trữ tablet | ⬜ | Đo trên lõi, không trên thiết bị |

### Pha 5 – Thao tác bắt buộc online (FR-CC-069)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 5.1 | Từ chối lệnh offline cho: duyệt/chốt ca, duyệt điều chỉnh, phát hành phiếu mới | ⬜ | Test đỏ: lệnh loại này từ tablet → REJECTED |
| 5.2 | Đăng nhập lần đầu của thiết bị phải online | ⬜ | Phụ thuộc D2 |

### Pha 6 – Lõi tablet: phục hồi và hoàn thiện (FR-CC-060, 064, 065)
| ID | Việc | Trạng thái | Ghi chú |
|---|---|---|---|
| 6.1 | Test phục hồi hàng đợi sau tắt đột ngột (giao dịch SQLite) | ⬜ | FR-CC-065 |
| 6.2 | Form nháp tự lưu theo thay đổi trường (lõi, không UI) | ⬜ | |
| 6.3 | Trạng thái `PENDING/SYNCING/SYNCED/CONFLICT/REJECTED` đủ trong lõi | ⬜ | |

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
| 9.1 | IT `SyncIT` và IT mới xanh trên MySQL 8.0 và 8.4 | ⬜ | |
| 9.2 | Full `verify` 8.0 và 8.4: không có lỗi mới ngoài baseline 13 | ⬜ | Baseline ProductionIT 11, MaterialIT 2 |
| 9.3 | `npm test` và `npm run typecheck` ở `src/tablet` xanh | 🟡 | Tablet test 10/10 đã xanh; typecheck chưa chạy |
| 9.4 | OPEN-QUESTIONS: đóng L30, L31 hoặc ghi phần còn lại | ⬜ | |
| 9.5 | PROGRESS cập nhật E09 | ⬜ | |

## 8. Trạng thái theo yêu cầu (đích cần đạt)

| Yêu cầu | Hiện tại | Đích E09 | Pha |
|---|---|---|---|
| FR-CC-060 | 🟡 | ✅ | 1.3, 6.2 |
| FR-CC-061 | ✅ | ✅ | 0.3, 1.2 |
| FR-CC-062 | 🟡 | ✅ (PIN-hash theo D2) | 3.1–3.5 |
| FR-CC-063 | 🟡 | ✅ | 2.1, 2.2 |
| FR-CC-064 | 🟡 | ✅ | 2.4, 6.3 |
| FR-CC-065 | ⬜ | ✅ (trên lõi; thiết bị theo D5) | 6.1 |
| FR-CC-066 | 🟡 | ✅ (theo D4) | 8.1, 8.2 |
| FR-CC-067 | ❓ | ✅ (P1) | 4.1 |
| FR-CC-068 | ❓ | ✅ (P1) | 4.2, 4.3 |
| FR-CC-069 | ❓ | ✅ | 5.1, 5.2 |

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
| 2026-10-31 | Lập kế hoạch E09 theo cấu trúc E08. Rà soát tĩnh `modules/sync` (8 file chính) và `src/tablet`. Baseline tablet 10/10 xanh. Chưa sửa mã |
