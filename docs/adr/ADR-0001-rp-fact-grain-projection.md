# ADR-0001 – RP: grain của bảng Fact và khung projection

- **Trạng thái:** Đề xuất (chờ Dev lead + PO xác nhận mục §8; câu hỏi O1–O5 = L57–L61 trong `docs/OPEN-QUESTIONS.md`)
- **Ngày:** 2026-10-18
- **Phạm vi:** Epic E15 (RP core). Liên quan: SDD §14.1 (chiến lược dữ liệu RP), FR-RP-002/007/008, AC-RP-001/003/005, L11, L15, L50.
- **Không thuộc ADR này:** bố cục PDF/XLSX, dashboard (E16), chính sách revision báo cáo gửi đi, các fact QC/TX/PM/OH/SP.

## 1. Bối cảnh (đã đối chiếu với mã nguồn)

Hiện trạng làm ràng buộc thiết kế:

1. **Outbox là một cờ `processed_at` dùng chung** (`OutboxDispatcher`). Mỗi bản ghi được chuyển lần lượt cho *mọi* `OutboxSink`;
   nếu một sink ném lỗi thì cả bản ghi bị thử lại và **các sink đã chạy trước đó bị gọi lại**. Không có offset theo consumer, không có dead-letter
   (`attempts` chỉ tăng). `SELECT … FOR UPDATE SKIP LOCKED` nên nhiều dispatcher có thể xử lý lô khác nhau → **không đảm bảo thứ tự**.
2. **Sự kiện hiện có quá thô để dựng fact bằng cộng dồn:** `pr.session.opened/ended`, `pr.stop.started/ended`, `pr.shift.closed`
   (kèm `reportRevision`), `pr.shift.finalized`, `adjustment.applied`, `pl.order.*`. **Chưa có** sự kiện khi ghi sản lượng
   (`pr_output_entry`), khi tính OEE, hay bất kỳ sự kiện nào của MT (`mt_*`). Payload chỉ chứa khóa (`sessionId`, `businessDate`, `shiftCode`).
3. **Dữ liệu nguồn đã có dạng cộng dồn được:** `pr_oee_result` lưu `planned_minutes, run_minutes, total_qty, good_qty, theoretical_qty` theo
   **(phiên ca × máy)**, kèm `formula_id`, `is_provisional`; `OeeCalculator.aggregate` đã gộp bằng "cộng tử – mẫu".
   Sản lượng theo mác nằm ở `pr_output_entry` (`segment_no`, `product_id`); dừng máy ở `pr_stop_event` (theo run, **không có mác**).
4. **`pr_shift_session` có `UNIQUE (business_date, shift_code)`** nên (ngày, ca) định danh duy nhất phiên ca. Trạng thái: `OPEN → CLOSING → CLOSED → FINALIZED`;
   mở lại qua điều chỉnh là `CLOSED → CLOSING` (xóa `closed_at`), chốt lại phát `pr.shift.closed` với `reportRevision` mới.
5. **ArchUnit:** RP chỉ được đọc module khác qua `contract`. `MaterialQueries` chưa tồn tại (module `material` chưa có gói `contract`), `ProductionQueries` chưa có truy vấn fact.
6. **Worker chưa nạp module nghiệp vụ (L11)**; dispatcher và scanner đang chạy trong `pxvb-api`. Host dùng `@Scheduled` + cờ bật/tắt, không phải Quartz.
7. **Quy mô nhỏ:** 77 máy × 3 ca × 365 × 3 năm ≈ **253 nghìn dòng** ở grain máy; ~1–2 triệu dòng với các bảng con. Phân vùng theo tháng (SDD) phục vụ **quản lý vòng đời dữ liệu**, không phải là điều kiện để đạt P95 < 3s.

## 2. Quyết định

### D1. Fact là "sự thật hiện hành", ảnh chụp bất biến nằm ở `ReportSnapshot`

`rp_fact_*` luôn phản ánh trạng thái nguồn mới nhất (ghi đè). Tính bất biến/pháp lý nằm ở `rp_report_snapshot` (đã bất biến theo `Revision`, SDD §14.1).
Không lưu lịch sử phiên bản của fact; khác biệt giữa hai revision (FR-RP-008) được tính bằng so sánh payload của hai snapshot.
Hệ quả: "so với ngày trước" trong một snapshot là con số **tại thời điểm sinh** và được nhúng vào payload.

### D2. Grain: tách thành các bảng theo đúng grain của nguồn (lệch SDD §14.1, cần cập nhật SDD)

SDD mô tả một bảng `(ca × máy × SP)` chứa cả phút chạy/OEE lẫn `StopMinByReason(json)`. Với dữ liệu thật điều này sai về cộng dồn:
phút chạy, phút kế hoạch và OEE là của **(ca × máy)**; khi máy đổi mác thành 2 segment, đặt chúng cùng dòng với mác sẽ **nhân đôi** phút khi `SUM`
(fan-out). Dừng máy không gắn mác; chia theo thời gian chồng lấn là tùy tiện và không đối soát được.

| Bảng | Khóa chính (có `business_date` cho phân vùng) | Đo lường |
|---|---|---|
| `rp_fact_shift_machine` | `(business_date, shift_code, machine_id)` | `session_id`, `unit` (`RODS`/`PACKS`), `plan_qty`, `planned_min`, `run_min`, `planned_stop_min`, `unplanned_stop_min`, `total_qty`, `good_qty`, `theoretical_qty`, `perf_num`, `availability`, `performance`, `quality`, `oee`, `formula_id`, `is_provisional`, `session_status`, `source_hash`, `projected_at` |
| `rp_fact_shift_machine_product` | `(business_date, shift_code, machine_id, segment_no)` | `product_id`, `qty`, `plan_qty` (nullable), `entries` |
| `rp_fact_shift_stop` | `(business_date, shift_code, machine_id, reason_code)` | `minutes`, `events`, `planned` |
| `rp_fact_shift_material` | `(business_date, shift_code, machine_id, material_id)` | `opening, issued, returned, closing, consumed, theoretical, waste_qty, waste_pct, flag, counted` (từ `mt_consumption_summary`) |

- Khóa tự nhiên là khóa upsert → idempotent, không cần khóa thay thế, thỏa quy tắc MySQL "cột phân vùng nằm trong mọi unique key".
- **Không có FK** (InnoDB phân vùng không hỗ trợ). Toàn vẹn do projection + đối soát (D6) bảo đảm.
- `StopMinByReason` chuyển từ JSON sang bảng con để `GROUP BY reason_code` (Pareto A–I, drill-down) và AC-RP-003 kiểm được bằng SQL thuần.
- **Chỉ lưu đại lượng cộng dồn là nguồn sự thật.** Tỷ lệ A/P/Q/OEE lưu ở grain (ca × máy) để đọc nhanh; **mọi gộp (ngày/tuần/tháng/dòng máy) tính lại tỷ lệ từ tổng**, không trung bình cộng phần trăm (SDD §8.3).
- Không cộng chéo `unit` (điếu vs bao): gộp luôn `GROUP BY unit`.
- `perf_num` = tử số hiệu năng **sau khi áp trần** (`min(total, theoretical)` khi `CapPerformanceAt100`), để gộp là phép cộng thuần. Xem câu hỏi O1.
- Các bảng `FactDaily/Weekly/MonthlyKpi` trong SDD **hoãn** sang E24; E15 chỉ cần gộp lúc đọc (dữ liệu nhỏ).
- Phân vùng `RANGE COLUMNS(business_date)` theo tháng + phân vùng `p_future` (`MAXVALUE`) để insert không bao giờ lỗi 1526. Job `PartitionMaintainer` mỗi ngày
  `REORGANIZE PARTITION p_future` để luôn có ≥ 3 tháng phía trước; cùng hàm `ensureRange(from, to)` dùng cho fixture 3 năm của AC-RP-005.

### D3. Mô hình kích hoạt: "thông báo + kéo qua contract", đơn vị tính lại là một phiên ca

Không dựng fact bằng cách cộng dồn từng sự kiện (cần sự kiện cho mọi bản ghi, thứ tự, và điều chỉnh dưới dạng delta – nguồn của double count).
Thay vào đó:

1. Sự kiện chỉ nói "phiên ca `(business_date, shift_code)` đã đổi".
2. Worker **tính lại toàn bộ fact của phiên ca đó** từ nguồn qua `contract`, rồi thay thế (D5).

Do đó thứ tự và trùng lặp sự kiện không ảnh hưởng kết quả (last-writer-wins trên bản tính lại), replay = tính lại, rebuild = tính lại nhiều phiên ca, đối soát = tính lại rồi so sánh.
Chi phí một lần tính lại nhỏ (≤ 77 máy).

Phương án đã loại: (A) delta theo sự kiện – xem trên; (B) RP đọc thẳng bảng `pr_*/mt_*` hoặc view – vi phạm ranh giới module (ArchUnit); (C) chỉ dựa `pr_shift_report` JSON –
là ảnh chụp tại lúc chốt, không có số tạm tính, không có chi tiết NVL/dừng theo máy; dùng làm **đối chiếu** chứ không làm nguồn.

Hợp đồng nguồn cần bổ sung (điều kiện tiên quyết):
- `production.contract.ProductionQueries#shiftFacts(UUID sessionId)` → `ShiftFacts` (các `RunFact` kèm `SegmentFact`, `StopFact`), và `#sessionsBetween(LocalDate, LocalDate)`.
- `material.contract.MaterialQueries#consumption(UUID sessionId)` (tạo gói `contract`).
- Hàm gộp OEE thuần đưa vào `production.contract` (hoặc RP dùng công thức từ `perf_num` – O1) để không nhân đôi công thức.
- Sự kiện mới: `pr.output.recorded` (khóa phiên ca) và `mt.consumption.computed` / `mt.issue.*` (khóa phiên ca). Được hợp nhất (coalesce) ở D4 nên tần suất không đáng lo.

Sự kiện kích hoạt đánh dấu bẩn: `pr.session.opened/ended`, `pr.stop.*`, `pr.output.recorded`, `pr.shift.closed`, `pr.shift.finalized`, `mt.*`, và `adjustment.applied`
với `entityType = ShiftSession` (khóa = `entityId`). `adjustment.applied` cho loại thực thể khác chưa có cách ánh xạ về phiên ca → ghi nhận ở O3, tạm thời do đối soát đêm bắt.

### D4. Hàng đợi riêng của RP, tách khỏi vòng retry chung của outbox

- `RpOutboxSink implements OutboxSink` **chỉ** upsert vào `rp_projection_dirty` và không làm gì khác:
  `INSERT … (business_date, shift_code, reason, dirty_seq=1, marked_at) ON DUPLICATE KEY UPDATE dirty_seq = dirty_seq + 1, marked_at = …`.
  Không có nghiệp vụ nên không thể gây lỗi làm outbox gọi lại các sink khác (ví dụ `WorkflowEventSink` gửi thông báo lặp). Sink bắt buộc idempotent theo hợp đồng `OutboxSink`; upsert đáp ứng.
- `ProjectionWorker` (`@Scheduled`, cờ `pxvb.rp.projection.enabled`) nhận bản ghi bằng `FOR UPDATE SKIP LOCKED`, tính lại, rồi xóa bản ghi bẩn **chỉ khi `dirty_seq` không đổi** từ lúc đọc (CAS);
  nếu sự kiện đến trong lúc đang tính thì `dirty_seq` đã tăng → bản ghi còn lại và được tính lại vòng sau. Nhiều đợt sự kiện dồn dập (ghi sản lượng liên tục) tự gộp thành một lần tính.
- Lỗi: `attempts`, `next_attempt_at` lùi theo cấp số nhân, sau N lần (mặc định 5) đặt `FAILED` và bắn cảnh báo `RP_PROJECTION_FAILED`. Chỉ số độ trễ = `now - min(marked_at)` của hàng chưa xong → dùng cho "dữ liệu cũ > 5 phút" ở E16.
- Vị trí chạy: sink chỉ là upsert nên chạy ở host nào có dispatcher cũng được; `ProjectionWorker` bật ở host đang nạp module `reporting` (hiện là `pxvb-api`, theo L11) và chuyển sang worker khi L11 xong.
  `SKIP LOCKED` làm cho chạy hai nơi vẫn an toàn.

### D5. Ghi fact: thay thế nguyên phiên ca trong một giao dịch nhất quán

Một giao dịch `REPEATABLE_READ` (ghi đè mức mặc định `READ_COMMITTED` của datasource worker) bao trùm: đọc tất cả truy vấn contract của phiên ca → upsert mọi bảng fact → xóa các dòng
của `(business_date, shift_code)` không còn trong tập mới (máy bị gỡ, segment biến mất) → cập nhật `source_hash`. `REPEATABLE_READ` để các truy vấn rời nhau nhìn thấy cùng một ảnh chụp,
tránh fact mâu thuẫn nội bộ (ví dụ sản lượng đã tính nhưng dừng máy vừa kết thúc chưa thấy).

- `source_hash` (băm tập dòng đã tính) bằng giá trị cũ → **không ghi, không phát sự kiện**.
- Khác → ghi và phát outbox `rp.fact.changed {businessDate, shiftCode, sessionStatus}` cho E16 (STOMP) và bộ dựng báo cáo.
- Đặt cờ `is_provisional` và `session_status` ngay trên dòng; báo cáo ca chỉ được dựng khi `session_status ∈ {CLOSED, FINALIZED}` và `is_provisional = false`.

### D6. Đối soát và dựng lại

- **Dựng lại:** `ProjectionRebuilder.mark(from, to)` chèn `rp_projection_dirty` cho mọi phiên ca trong khoảng (từ `sessionsBetween`), worker xử lý có giới hạn tốc độ. Dùng khi đổi công thức, sửa lỗi projection, hoặc nạp lịch sử.
- **Đối soát đêm:** với các phiên ca trong `RECONCILE_DAYS` (mặc định 35) tính lại trong bộ nhớ, so `source_hash`; lệch → ghi `rp_reconciliation_issue`, bắn `RP_FACT_DRIFT`, tự chữa bằng đánh dấu bẩn.
  Job này dùng cùng mã tính nên **không độc lập**; không dùng nó làm bằng chứng cho AC-RP-003.
- **Oracle độc lập cho AC-RP-003:** test tích hợp (Testcontainers MySQL 8.0 và 8.4) sinh tập 30 ngày xác định, tính tổng bằng SQL **trực tiếp trên `pr_*/mt_*`** (không qua contract/RP), so với tổng từ `rp_fact_*`, sai số 0.

### D7. Cấu trúc module

Module mới `modules/reporting`, gói `vn.pxvb.reporting.{domain,application,infrastructure,api,contract}`, tiền tố bảng `rp_`, Flyway `db/migration/reporting`.
Chỉ phụ thuộc `production.contract`, `material.contract`, `masterdata.contract`, `identity.contract`. Thêm luật ArchUnit tương ứng trong `ArchitectureTest`.

## 3. Hệ quả

- ✅ Đúng khi replay/đảo thứ tự/trùng sự kiện; rebuild và đối soát gần như miễn phí vì cùng một đường mã.
- ✅ Lỗi RP không làm outbox gọi lại sink khác; hàng đợi có độ trễ đo được.
- ✅ Cộng dồn đúng ở mọi cấp, không nhân đôi phút khi đổi mác.
- ⚠ Thêm việc ở module nguồn (contract mới, 2–3 sự kiện mới) – nằm trên đường găng của E15.
- ⚠ Lệch SDD §14.1 (nhiều bảng thay vì 1, dừng máy thành bảng con) → cần cập nhật SDD.
- ⚠ Tính lại cả phiên ca mỗi lần: chấp nhận được ở quy mô này; nếu `pr.output.recorded` quá dày thì tăng cửa sổ gộp (`min-interval` theo khóa).
- ⚠ Dữ liệu tablet đồng bộ muộn sau khi ca đã `CLOSED` vẫn đi vào fact (đúng, D1) nhưng sẽ làm báo cáo đã sinh *lỗi thời*; xử lý ở chính sách revision (O2), không ở ADR này.

## 4. Kiểm thử bắt buộc

1. Phát lại: chạy projection hai lần / theo thứ tự xáo trộn → `source_hash` giống nhau (property test).
2. Sự kiện đến giữa lúc đang tính → hàng còn lại, vòng sau tính lại (CAS).
3. Đổi mác trong ca: `SUM(run_min)` ở `rp_fact_shift_machine` không đổi so với `pr_oee_result`.
4. Mở lại ca (`CLOSED → CLOSING → CLOSED`): fact quay về tạm tính rồi về cuối cùng, không mồ côi dòng.
5. Oracle SQL 30 ngày sai số 0; EXPLAIN xác nhận cắt tỉa phân vùng; fixture 3 năm cho AC-RP-005.
6. Sink lỗi/DB lỗi: không gọi lại `WorkflowEventSink`; hàng chuyển `FAILED` sau N lần.
7. ArchUnit: `reporting` chỉ phụ thuộc gói `contract` của module khác.

## 5. Thứ tự thực hiện đề xuất

1. Contract nguồn + sự kiện mới (PR, MT) – kèm test.
2. Migration `reporting` (bảng fact, `rp_projection_dirty`, phân vùng) + `PartitionMaintainer`.
3. `RpOutboxSink` + `ProjectionWorker` + fact PR/OEE/dừng máy, oracle SQL (cắt dọc đầu tiên).
4. Fact NVL, `ProjectionRebuilder`, đối soát đêm.
5. Sau đó mới tới bộ dựng báo cáo (`ReportSnapshot`) và renderer.

## 6. Phương án đã cân nhắc nhưng không chọn

| Phương án | Lý do loại |
|---|---|
| Một bảng `(ca × máy × SP)` đúng SDD | Nhân đôi phút/OEE khi đổi mác; buộc chia dừng máy theo mác |
| Cộng dồn delta theo sự kiện | Thiếu sự kiện; phụ thuộc thứ tự; double count khi replay/điều chỉnh |
| Sink xử lý projection trực tiếp trong `handle()` | Lỗi RP kéo theo gọi lại mọi sink; không có back-off/dead-letter |
| RP đọc bảng nguồn qua view/SQL | Vi phạm ArchUnit và ranh giới module |
| Lấy `pr_shift_report` làm nguồn fact | Chỉ có ảnh chụp lúc chốt, không có số tạm tính, thiếu chi tiết |
| Lưu lịch sử phiên bản của fact | Không cần: bất biến đã nằm ở `ReportSnapshot` |

## 7. Điểm cần cập nhật ở tài liệu khác

- SDD §14.1: thay mô hình bảng fact theo D2; chuyển `StopMinByReason` thành bảng con; ghi `rp_projection_dirty`, `rp_reconciliation_issue`.
- `docs/OPEN-QUESTIONS.md`: thêm O1–O5 bên dưới; cập nhật L15 trỏ ADR này.
- `docs/PROGRESS.md`: ghi các việc tiên quyết ở §5.1 vào E15.

## 8. Câu hỏi mở

- **O1 (Dev lead PR):** `OeeCalculator.aggregate` áp trần hiệu năng ở mức **tổng gộp** (`min(1, Σtotal/Σtheoretical)`), còn `compute` áp trần **từng máy/ca**. Với `CapPerformanceAt100 = true` (mặc định từ L42),
  OEE ngày tính từ tổng gộp có thể khác OEE tính từ các dòng đã bị trần. Cần chốt quy ước gộp trước khi viết AC-RP-003 cho OEE; ADR này giả định gộp theo `perf_num` (tổng các tử số đã trần).
  *(Mới đọc mã, chưa chạy số liệu để xác nhận mức chênh.)*
- **O2 (PO):** Chính sách revision/gửi lại R_SHIFT/R_DAY khi fact đổi sau lúc đã sinh (điều chỉnh, đồng bộ muộn); "ngày trước" của R_DAY dùng số gốc hay số mới nhất; có thông báo revision không.
  ADR này chỉ bảo đảm fact luôn mới nhất và snapshot bất biến (D1).
- **O3 (Dev lead):** Ánh xạ `adjustment.applied` của các `entityType` khác `ShiftSession` về phiên ca (module nào cung cấp `SessionResolver`?). Khi `FINALIZED` được sửa (L15/L50) thì ai kích hoạt tính lại?
- **O4 (PXVB/CNTT):** `RECONCILE_DAYS`, thời hạn giữ `rp_fact_*` online (3 năm, Q18) và chính sách archive phân vùng cũ.
- **O5 (Dev lead):** Thời điểm đo bắt đầu của SLA READY 5': đề xuất tính từ `created_at` của bản ghi outbox `pr.shift.closed` đến `generated_at` của snapshot `READY`, đã gồm trễ dispatcher và projection.
