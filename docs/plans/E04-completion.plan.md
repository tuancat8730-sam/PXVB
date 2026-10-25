# Kế hoạch: Hoàn tất E04 (Alert engine + Notification + WebSocket/STOMP, CC-06)

**Nguồn:** `docs/PROGRESS.md` (E04 🟡), `docs/PXVB-SRS-SDD.md` §4.6 (FR-CC-050..055, danh mục cảnh báo), §4.9 (job nền), NFR-004; `docs/OPEN-QUESTIONS.md` L11, L12, L16–L18, L26, L27, L63; bản vẽ `docs/design/` (`T12-Alerts.dc.html`, `W-AlertCenter.dc.html`, `DESIGN-SYSTEM.md` §3–§8).
**Độ phức tạp:** Trung bình (backend) + trung bình (UI). **Cập nhật lần cuối:** 2026-10-25 (bổ sung thiết kế UI).
**Phương pháp:** rà soát tĩnh mã nguồn `modules/alerting`, các điểm gọi `AlertRaiser.raise`, test `AlertingIT`/`AlertRealtimeIT`, cấu hình `application.yml`; đối chiếu với bản vẽ tablet và web. **Chưa chạy test, chưa sửa mã, chưa sửa bản vẽ.** Các mục đánh dấu "giả thuyết" phải kiểm chứng ở Pha 0.
**Chú giải:** ✅ xong · 🟡 đang làm / xong một phần · ⬜ chưa làm · ⏸ hoãn (có quyết định) · ❌ bị chặn

## 1. Phạm vi và điều kiện "E04 hoàn tất"

E04 có nhiều phần phụ thuộc khác nhau. Chỉ phần backend thuộc E04 mới được tính vào tiến độ E04.

| Nhóm | Nội dung | Thuộc E04? |
|---|---|---|
| Backend cảnh báo | Rule, vòng đời, khử trùng, leo thang, thông báo trong ứng dụng, email CRITICAL, STOMP server, API trung tâm cảnh báo, xuất Excel | ✅ Có (Pha 0–5, 7) |
| Nguồn phát cảnh báo của từng phân hệ | 22 mã rule chưa có nơi phát (mục 4) | ❌ Thuộc E08/E11/E14/E17–E23; E04 chỉ đảm bảo API `raise` |
| Giao diện tablet cảnh báo | Màn T-12, banner realtime, chuông, bù khi online lại (FR-CC-053) | ✅ Thuộc E04 (Pha 6). Bản vẽ có một phần (mục 2b); build chờ UI tablet RN (`src/tablet` hiện chỉ có lõi TS) |
| Giao diện web cảnh báo | Trung tâm cảnh báo, quy tắc cảnh báo, chuông thông báo (FR-CC-050, 054) | ✅ Thuộc E04 (Pha 6). Bản vẽ có một phần (mục 2b); build chờ khung `src/web` |
| Báo cáo định kỳ qua email | Báo cáo ngày/tuần/tháng | ❌ Chờ RP (E15/E24) + SMTP (L18/L26) |
| Worker, relay RabbitMQ | L11, L12 | ⏸ Đề xuất chuyển E25 (cùng L08), chờ L70 |
| Snooze | FR-CC-055 (P2) | ⏸ Đã ghi "để sau" ở L17, chờ L69 |

**E04 được đánh ✅ khi:** Pha 0–5, Pha 6 (cả thiết kế D1–D6 lẫn build 6.1–6.5) và Pha 7 xong; mục ⏸ có ghi nhận quyết định; mục phụ thuộc epic khác (nguồn phát rule, ShiftCloseWatcher) ghi rõ epic đích.

Các UI của E04 được làm theo bản vẽ trong `docs/design/`. Bản vẽ đang ở trạng thái **chờ duyệt**, nên việc build UI chỉ bắt đầu sau khi PXVB duyệt (`DESIGN-SYSTEM.md` §9).

## 2. Đánh giá hiện trạng theo yêu cầu

| Yêu cầu | Trạng thái | Bằng chứng |
|---|---|---|
| FR-CC-050 Rule cấu hình được | 🟡 | `AlertRuleController` PUT, `AlertRule.configure`; audit tự động. **Thiếu** ngày hiệu lực/lịch sử phiên bản (F9) |
| FR-CC-051 Vòng đời OPEN→ACK→IN_PROGRESS→RESOLVED→CLOSED | 🟡 | `Alert.LIFECYCLE`, `AlertService.move`, `AlertHistory`. Cause bắt buộc, lấy từ `ALERT_CAUSE`. **Thiếu** ghi chú bắt buộc khi RESOLVED (F3) |
| FR-CC-052 Leo thang khi chưa ACK | 🟡 | `AlertService.escalateDue` + `AlertEscalationService` (bật mặc định). Test một cấp (`unackedAlertEscalatesToSuperiorOnce`). Chưa test đa cấp; có vấn đề hiệu năng (F5) |
| FR-CC-053 Tablet nhận qua STOMP | 🟡 | Server có: `WebSocketConfig` (xác thực CONNECT, chặn SEND, chỉ `/user/queue/**`), `AlertRealtimeIT`. Tablet: **không có mã xử lý cảnh báo** (`src/tablet` không tham chiếu alert); sync pull không đưa cảnh báo mở (F13) |
| FR-CC-054 Trung tâm cảnh báo | 🟡 | API lọc theo module/mức/trạng thái/máy, phân trang, xuất Excel ✅. **Thiếu** lọc theo ca (F4), thống kê tần suất (F4), UI (❌) |
| FR-CC-055 Snooze | ⏸ | Chưa làm, chờ L69 |
| Email CRITICAL (L18/L26) | ✅ | `EmailService.sendAfterCommit`, tắt mặc định, `AlertingIT.criticalAlertIsEmailedButWarningIsNot` |
| Thông báo trong ứng dụng | ✅ | `NotificationService`, `NotificationController`, `AlertingIT.notificationsCanBeMarkedRead` (🔴 flaky, F1) |
| Danh mục rule seed | ✅ | `AlertRuleCatalog`: 30 rule, khớp đủ danh mục SRS §4.6 (cộng thêm `SP_STOCK_BELOW_MIN`, `CC_*`) |
| NFR-004 cảnh báo ≤ 5 phút | ⬜ | Không có metric/timer đo `raise → push` (F14) |
| L11/L12 worker + relay | ⏸ | Scanner chạy trong `pxvb-api`; STOMP trong tiến trình |

## 2b. Đối chiếu với thiết kế (`docs/design/`)

Bản vẽ đề xuất, chờ duyệt. Mọi màn UI của E04 phải qua cổng kiểm tra §8 của `DESIGN-SYSTEM.md` và có đủ sáu trạng thái §5.

| Màn | File | Đã có trong bản vẽ | Thiếu so với FR/backend | Việc |
|---|---|---|---|---|
| T-12 Cảnh báo (tablet, mẫu T-A) | `T12-Alerts.dc.html` | Danh sách theo Cấp A/B/C; lọc "Chưa xác nhận / Tất cả"; nút "Xác nhận đã biết"; trạng thái đồng bộ ở đầu trang | Luồng xử lý (IN_PROGRESS, RESOLVED với nguyên nhân và ghi chú bắt buộc); banner realtime theo mức (FR-CC-053); âm/rung cấu hình được; trạng thái "nhận bù khi online lại" | D1, 6.1 |
| Chuông và banner (tablet) | `DESIGN-SYSTEM.md` §4 (thanh trạng thái có "chuông") | Thành phần Banner và chuông trên thanh trạng thái | Màn danh sách thông báo tablet | D4, 6.1 |
| Trung tâm cảnh báo (web, mẫu W1) | `W-AlertCenter.dc.html` | Bảng, lọc theo Cấp và Trạng thái; chi tiết có "Gửi tới", "Xem lượt KSCL", "Xác nhận"; có tab "Quy tắc cảnh báo" | Lọc theo ca, máy, khoảng thời gian (FR-CC-054); **nút xuất Excel**; luồng xử lý đủ vòng đời và lịch sử (FR-CC-051); hiển thị cấp leo thang; thống kê tần suất (FR-CC-054) | D2, 6.2 |
| Quy tắc cảnh báo (web, mẫu W2) | Chưa có màn riêng (chỉ là tab trong `W-AlertCenter`) | – | Người nhận, leo thang, cửa sổ dedup, mức, bật/tắt, ngày hiệu lực, lịch sử (FR-CC-050) | D3, 6.3 |
| Chuông và danh sách thông báo (web) | Chưa có | – | Chuông, danh sách, đánh dấu đã đọc, liên kết đến cảnh báo hoặc phiếu duyệt | D4, 6.4 |
| Snooze (FR-CC-055) | Chưa có | – | Chưa làm (⏸, L69) | D7 (không vẽ) |

## 3. Phát hiện từ rà soát (kèm mức độ)

| ID | Mức | Phát hiện | Bằng chứng |
|---|---|---|---|
| F1 | CAO | `notificationsCanBeMarkedRead` giả định `unread == 0` toàn cục sau "đọc hết". Scanner nền (Production/Material/Sync, `AlertEscalationService`) bật mặc định trong test và có thể tạo thông báo giữa hai bước. **Đây là giả thuyết, chưa tái hiện** (L63) | `AlertingIT` dòng cuối; `application.yml` (`*.scanner.enabled` = true) |
| F2 | CAO | Tái diễn không nâng mức. Nhánh `existing` chỉ gọi `recordRecurrence`, bỏ qua `c.severity()`. `PR_STOP_LONG` (WARN→CRIT trong SRS) không bao giờ thành CRITICAL trên alert đang mở, nên không gửi email CRITICAL | `AlertService.raise` |
| F3 | CAO | `resolve` chỉ kiểm tra `cause`, không yêu cầu ghi chú. FR-CC-051 yêu cầu ghi chú khi RESOLVED | `AlertService.resolve` |
| F4 | TB | Trung tâm cảnh báo không lọc theo ca (`shiftSessionId`), không có thống kê tần suất. Excel cũng không có bộ lọc ca | `AlertService.search`, `AlertController` |
| F5 | TB | `escalateDue` tải mọi alert OPEN mỗi phút, gọi `rules.findByCode` cho từng alert (N+1), không bỏ qua rule đã tắt. Chỉ có test một cấp | `AlertService.escalateDue` |
| F6 | TB | Xuất Excel cắt im lặng ở 50.000 dòng (`PageRequest.of(0, 50_000)`), vi phạm nguyên tắc không nuốt lỗi | `AlertService.export` |
| F7 | TB | Severity sai (chuỗi không thuộc enum) → `Alert.Severity.valueOf` ném `IllegalArgumentException` (500), không phải VALIDATION | `AlertService.raise` |
| F8 | TB | Không có unit test nào trong `modules/alerting/src/test`. Toàn bộ là IT. Logic dedup, leo thang, vòng đời chưa có test thuần | Thư mục `modules/alerting/src/test` không tồn tại |
| F9 | TB | FR-CC-050 yêu cầu "ngày hiệu lực". `configure` ghi đè tại chỗ, chỉ dựa vào audit | `AlertRule.configure` |
| F10 | TB | Ack/xử lý chỉ kiểm quyền `alerting.alert.handle`, không kiểm tra người có thuộc người nhận hay phạm vi. SUPERVISOR của dây chuyền A có thể xử lý cảnh báo dây chuyền B | `AlertController` ack/start/resolve/close; `AlertingPermissions` |
| F11 | THẤP | Tái diễn trong cửa sổ dedup không gửi lại thông báo. Sau cửa sổ thì tạo alert mới và thông báo lại. Cần chốt hành vi | `AlertService.raise` |
| F12 | TB | 22 mã rule đã seed chưa có nơi phát (mục 4) | So `AlertRuleCatalog` với các lệnh `raise` |
| F13 | TB | Tablet không xử lý cảnh báo. Sync pull không bù cảnh báo mở khi thiết bị online lại | `src/tablet`, `SyncService` |
| F14 | TB | Không có metric đo độ trễ `raise → push` để kiểm NFR-004 | Không có `MeterRegistry`/`Timer` trong backend |
| F15 | THẤP | Cảnh báo phát từ worker không tới được client (STOMP trong tiến trình) | L12 |
| F16 | CAO (thiết kế) | Bản vẽ T-12 và W-AlertCenter không có luồng xử lý (IN_PROGRESS, RESOLVED). Backend bắt buộc chọn nguyên nhân và ghi chú (F3, FR-CC-051). Hai màn chỉ có nút "Xác nhận" / "Xác nhận đã biết" | `T12-Alerts.dc.html`, `W-AlertCenter.dc.html` |
| F17 | TB (thiết kế) | Thiếu bản vẽ cho: quy tắc cảnh báo (FR-CC-050), chuông và danh sách thông báo web, lọc theo ca/máy/thời gian, xuất Excel, thống kê tần suất | `docs/design/screens/` (không có tệp tương ứng) |
| F18 | TB (thiết kế) | Từ vựng không khớp. Bản vẽ dùng "Cấp A/B/C" và "Đã biết" cho mọi cảnh báo; backend dùng INFO/WARN/CRITICAL và ACK/IN_PROGRESS. "Cấp A" của QC (lỗi chất lượng) dễ bị nhầm với mức CRITICAL | `DESIGN-SYSTEM.md` §3; `T12-Alerts.dc.html`; `Alert.Severity` |
| F19 | TB (thiết kế) | Các màn cảnh báo chưa qua cổng §8 (bàn phím, tương phản, 1366×768, tablet 10″). Các tệp trạng thái `S-TA-*`, `S-W1-*` là khung chung, không có nội dung cảnh báo nào | `DESIGN-SYSTEM.md` §5, §8, §10 (mục A) |

## 4. Rule đã seed nhưng chưa có nguồn phát

Các mã này đã có trong `AlertRuleCatalog`, nhưng không có lệnh `raise` nào trong mã sản xuất. Trạng thái "đã seed" không có nghĩa là đã hoạt động.

| Epic đích | Mã rule | Ghi chú |
|---|---|---|
| E11 (PR) | `PR_BUFFER_HIGH`, `PR_BUFFER_LOW` | Chỉ có trong test. Chờ FR-PR-007 tồn điếu (⬜) |
| E17–E19 (QC) | `QC_DEFECT_A`, `QC_DEFECT_B`, `QC_DEFECT_C`, `QC_CHECK_LATE`, `QC_CHECK_MISSED`, `QC_PHYSICAL_OOS` | Chờ form engine và lịch kiểm tra inline |
| E20 (TX) | `TX_WASTE_HIGH`, `TX_MISMATCH`, `TX_BLOCK_LOW` | Chờ module TX |
| E21 (PM) | `PM_DUE_SOON`, `PM_OVERDUE`, `PM_WEEK_AT_RISK`, `PM_REPAIR_PENDING`, `PM_MTTR_MTBF_BREACH` | Chờ module PM |
| E22 (SP) | `SP_STOCK_BELOW_MIN` | Chờ module SP |
| E23 (OH) | `OH_DELAYED`, `OH_YEAR_AT_RISK`, `OH_PART_REPEAT`, `OH_EXTRA_PART_PENDING` | Chờ module OH |
| E14 (CC-11) | `CC_SHIFT_NOT_CLOSED` | Cần `ShiftCloseWatcher` (SDD §4.9), chưa làm |

Đã có nguồn phát: `PR_OUTPUT_BEHIND`, `PR_OEE_LOW`, `PR_STOP_LONG` (PR); `MT_ISSUE_OVER_SLIP`, `MT_WASTE_HIGH`, `MT_SLOT_LATE` (MT); `CC_DEVICE_UNSYNCED`, `CC_SYNC_CONFLICT` (CC).

## 5. Giả định đang áp dụng (chưa được PXVB/PO xác nhận; đổi được)

| # | Giả định | Nếu đổi |
|---|---|---|
| G1 | Leo thang chỉ áp dụng cho alert đang OPEN (chưa ACK). ACK hoặc IN_PROGRESS thì dừng leo thang | Đổi điều kiện trong `escalateDue` |
| G2 | Dedup mặc định 30 phút; CRITICAL 10 phút (`AlertRuleCatalog`) | Đổi trong catalog hoặc qua API rule |
| G3 | Thời gian leo thang: `PR_OUTPUT_BEHIND` 30', `PR_STOP_LONG` 15', `CC_SHIFT_NOT_CLOSED` 15' là giả định ⚠. SRS chỉ nêu `QC_CHECK_LATE` 10' và `PM_REPAIR_PENDING` 15' | Chỉnh qua API rule, không cần sửa mã |
| G4 | Email chỉ gửi cho CRITICAL (L18/L26). Báo cáo định kỳ ngoài phạm vi | Thêm khi RP sẵn sàng |
| G5 | Scanner và bộ đếm chạy trong tiến trình `pxvb-api` (L11) | Chuyển worker + Quartz ở E25 |

## 6. Câu hỏi cần PXVB / PO chốt (L64–L72 đã ghi vào `OPEN-QUESTIONS.md`)

**Trạng thái (2026-10-25):** L64–L70 đã chốt theo đề xuất của dev-team, PO duyệt. L71–L72 chốt trước đó. Chi tiết và điều kiện nằm trong `OPEN-QUESTIONS.md` mục E04.

| Mã | Câu hỏi | Đề xuất mặc định (nếu chưa có trả lời) |
|---|---|---|
| L64 | Ghi chú khi RESOLVED: bắt buộc không? | Bắt buộc, không được rỗng (FR-CC-051) |
| L65 | Ai được tiếp nhận/xử lý cảnh báo? Chỉ người nhận, hay mọi người có quyền trong phạm vi? | Giữ quyền hiện tại, kiểm phạm vi khi có L07 (F10) |
| L66 | Khi tái diễn mà mức tăng (WARN→CRIT): có nâng mức, gửi lại thông báo và email không? Tái diễn trong cửa sổ dedup có gửi lại không? | Nâng mức + gửi lại khi mức tăng; không gửi lại khi tái diễn cùng mức trong cửa sổ |
| L67 | Ngày hiệu lực của rule (FR-CC-050): cần phiên bản rule, hay chỉ cần audit? | Chỉ audit + ghi lý do thay đổi; phiên bản làm khi có yêu cầu (YAGNI) |
| L68 | "Thống kê tần suất" (FR-CC-054) đếm theo gì? | Theo rule và ngày, trong khoảng thời gian chọn |
| L69 | Snooze (FR-CC-055, P2) có vào R0 không? | Hoãn sang sau R0 (đã ghi ở L17) |
| L70 | Chuyển worker + relay RabbitMQ (L11/L12) sang E25? | Có, cùng L08 |
| L71 (**đã chốt 2026-10-25**) | Mức hiển thị cảnh báo chung: CRITICAL/WARN/INFO có hiển thị theo "Cấp A/B/C" như bản vẽ không? "Cấp A/B/C" có được tách hẳn khỏi cấp lỗi QC không? | Hiển thị CRITICAL = đỏ "Khẩn", WARN = vàng, INFO = xanh mực. Chữ "Cấp A/B/C" chỉ dùng cho cấp lỗi QC. Cập nhật `DESIGN-SYSTEM.md` §3 và bản vẽ T-12, W-AlertCenter (F18) |

**Quyết định L64–L70 (2026-10-25, theo đề xuất dev-team, PO duyệt):**
- **L64:** ghi chú bắt buộc khi RESOLVED, không rỗng sau khi cắt khoảng trắng, kiểm ở server (RFC7807 tiếng Việt). Backend cần sửa (F3).
- **L65:** giữ quyền `alerting.alert.handle`, không giới hạn theo người nhận. Kiểm phạm vi làm khi có L07. Rủi ro chấp nhận: đến lúc đó chưa chặn được xử lý chéo dây chuyền, nên không đưa nhiều dây chuyền ra production trước L07 (F10).
- **L66:** tái diễn mức tăng thì nâng mức và gửi lại thông báo; lên CRITICAL thì gửi email. Tái diễn cùng mức trong cửa sổ dedup chỉ tăng bộ đếm. Sau cửa sổ thì tạo alert mới (F2, F11).
- **L67:** không làm bảng phiên bản rule. Audit trước/sau + lý do bắt buộc; mỗi alert lưu snapshot ngưỡng và mã phiên bản rule lúc raise. **Điều kiện:** kiểm CLAUDE.md (ngưỡng phải dùng masterdata có phiên bản). Nếu rule cảnh báo là ngưỡng, phải chuyển sang phiên bản (cỡ L).
- **L68:** đếm theo rule và BusinessDate trong khoảng chọn, tối đa 90 ngày. Index `(rule_code, raised_at)`.
- **L69:** snooze không vào R0, hoãn sang P2. Khi làm, CRITICAL không được snooze.
- **L70:** chuyển worker và relay sang E25 **có điều kiện**: chỉ sau khi xác nhận không có đường R0 nào (kể cả email CRITICAL và outbox như `pl.order.confirmed`) phụ thuộc relay. Đến lúc đó, scanner chạy trên một instance duy nhất.
| L72 (**đã chốt 2026-10-25**) | Nhãn trạng thái: dùng "Chưa xác nhận / Đã biết / Đang xử lý / Đã xử lý / Đã đóng" tương ứng OPEN / ACK / IN_PROGRESS / RESOLVED / CLOSED? | Dùng đúng bảng ánh xạ này, PXVB xác nhận nhãn (F18) |

## 7. Kế hoạch thực hiện

Quy ước như E02: mỗi việc viết test trước (đỏ), rồi sửa mã (xanh). Việc có ⏸ hoặc ❌ không tính là thiếu của E04 nếu đã ghi quyết định.

### Pha 0 – Xác minh trước khi sửa
| ID | Việc | Trạng thái | Kết quả |
|---|---|---|---|
| 0.1 | Chạy `AlertingIT` + `AlertRealtimeIT` 5 lần liên tiếp trên MySQL 8.4 (`-Dpxvb.test.mysql=8.4`) để tái hiện hoặc bác bỏ F1 | ✅ 5/5 xanh (mỗi lượt 8 + 2 test). Lượt đầu lỗi hạ tầng Ryuk/Colima, đã xử lý bằng `TESTCONTAINERS_RYUK_DISABLED=true` (không đổi mã) | Ghi số lần đỏ/xanh vào nhật ký |
| 0.2 | Với F1: xác nhận nguyên nhân bằng log (scanner nào tạo thông báo trong khoảng test) | 🟡 F1 không tái hiện: 5 lượt riêng và 1 lượt full suite đều xanh. Giữ F1 ở mức rủi ro, chưa sửa test | Nếu không phải scanner, mở rủi ro mới |
| 0.3 | Baseline `./mvnw verify` trên 8.0 và 8.4 | 🟡 8.4 xong: 93 test, 13 lỗi (ProductionIT 11, MaterialIT 2), đúng baseline E02, không có lỗi E04. 8.0 chưa chạy | Số test xanh/đỏ trước khi sửa. Lưu ý: 13 lỗi sẵn có ở `ProductionIT`/`MaterialIT` (lệch thời gian ca) không thuộc E04 |
| 0.4 | Chốt L64–L70 với PXVB/PO | ✅ 2026-10-25 theo đề xuất dev-team (L67 và L70 có điều kiện, xem mục 6) | Pha 2 và 3 đã có quyết định L64–L66 và L68 để viết test |

### Pha 1 – Ổn định nền (không đổi nghiệp vụ)
| ID | Việc | Trạng thái | Mức |
|---|---|---|---|
| 1.1 | Sửa F1: test không giả định `unread == 0` toàn cục. Cách sửa tùy kết quả 0.2 (tắt scanner trong test, hoặc đếm theo phần tử vừa tạo). Test đỏ trước khi sửa nếu tái hiện được | ✅ Chọn cách đếm theo phần tử vừa tạo: `notificationsCanBeMarkedRead` chỉ kiểm thông báo của cảnh báo vừa raise (không phụ thuộc số thông báo toàn cục). F1 chưa tái hiện, nên không có test đỏ | CAO |
| 1.2 | F7: severity không hợp lệ → `BusinessException(VALIDATION)`, test đỏ trước | ✅ `AlertService.parseSeverity` (kiểm tra ngay đầu `raise`); test đơn vị `AlertInputRulesTest` đỏ rồi xanh | TB |
| 1.3 | F6: vượt 50.000 dòng → lỗi VALIDATION rõ ràng, không cắt im lặng; test | ✅ `requireWithinExportLimit` + lấy `MAX+1` dòng để đếm; test đơn vị ở `AlertInputRulesTest` (IT với 50.001 dòng không thực tế) | TB |
| 1.4 | F5 (một phần): bỏ qua rule không active, tải rule một lần (map theo code) thay vì N+1; test | 🟡 Bỏ qua rule không active ✅ (`escalateDue`, test `disabledRuleDoesNotEscalateOpenAlerts`). Còn lại: tải rule một lần thay vì N+1 | TB |

### Pha 2 – Vòng đời và leo thang
| ID | Việc | Trạng thái | Phụ thuộc |
|---|---|---|---|
| 2.1 | F3: ghi chú bắt buộc khi RESOLVED; test đỏ trước | ✅ Unit `AlertInputRulesTest` 8/8 xanh; IT `resolveWithoutNoteIsRejectedAndKeepsStatus` xanh (chạy lại 2026-10-25 sau khi sửa Docker) | L64 (đã chốt) |
| 2.2 | F2: nâng mức khi tái diễn (chỉ tăng, không hạ); khi nâng thì gửi lại thông báo và email nếu CRITICAL. Nhánh alert đã tồn tại hiện bỏ qua mức yêu cầu. IT `higherSeverityRecurrenceEscalatesAndEmailsOnceWhileSameLevelOnlyCounts` đã viết | ✅ Code (`AlertService.recur`, `Alert.raiseSeverity`, `renotify`); IT `higherSeverityRecurrenceEscalatesAndEmailsOnceWhileSameLevelOnlyCounts` xanh. Email đi một lần cho mỗi người nhận hiện tại (không gửi lặp khi tái diễn cùng mức) | L66 (đã chốt) |
| 2.3 | F11: tái diễn cùng mức trong cửa sổ dedup chỉ tăng bộ đếm; sau cửa sổ tạo alert mới | ✅ Cùng mã với 2.2 (IT ở 2.2). IT `recurrenceAfterDedupWindowCreatesNewAlert` (backdate `last_occurred_at` qua JDBC) xanh | L66 (đã chốt) |
| 2.4 | F10: kiểm tra phạm vi dây chuyền/máy khi xử lý. Chỉ làm sau L07; đến lúc đó giữ permission-only (xem L65). Đề xuất L07 đã ghi trong `OPEN-QUESTIONS.md`: alerting là consumer đầu tiên, lọc theo máy qua `MasterdataQueries` | ✅ Theo L07 (đã chốt 2026-10-25): `MachineInfo.lineCode`; cột `sys_alert.line_code` snapshot khi raise (V20261025_31); list và export lọc trong SQL; detail, ack, start, resolve, close trả 403 ngoài phạm vi; UNIT chưa có dữ liệu nên không khớp (fail-closed). IT `scopedUserCannotActOnAlertOfAnotherLine`, `listAndExportShowOnlyAlertsOfTheScopedLine`, `alertWithoutLineIsVisibleOnlyToAllScope`, `unitScopeSeesNothingUntilUnitsAreMappedToLines` đỏ trước, xanh sau | L65 (đã chốt), L07 (đã chốt) |
| 2.5 | Test leo thang đa cấp: không leo lại cấp đã leo; ACK hoặc IN_PROGRESS dừng leo thang; rule tắt thì không leo | ✅ 4 IT: `escalationAdvancesOneLevelAtATimeAndNeverRepeatsALevel`, `acknowledgedAlertStopsEscalation`, `inProgressAlertStopsEscalation`, `disabledRuleDoesNotEscalateOpenAlerts`. Lỗi thật đã sửa: `escalateDue` không kiểm tra rule tắt (test đỏ trước) | G1 |

### Pha 3 – Trung tâm cảnh báo (API)
| ID | Việc | Trạng thái | Phụ thuộc |
|---|---|---|---|
| 3.1 | F4a: lọc theo `shiftSessionId` ở `GET /alerts` và `GET /alerts/export`; IT | ✅ Thêm tham số `shiftSessionId` ở controller và `AlertService.search`/`export`. IT `alertListAndExportCanBeFilteredByShiftSession` đỏ trước, xanh sau (danh sách và số dòng xuất Excel) | Không |
| 3.2 | F4b: `GET /alerts/stats` đếm theo rule và BusinessDate trong khoảng tối đa 90 ngày; index `(rule_code, raised_at)`; IT kiểm ranh giới ngày theo BusinessDate, không theo UTC | ⬜ | L68 (đã chốt) |
| 3.3 | F9: audit trước/sau và lý do thay đổi bắt buộc khi sửa rule; alert lưu snapshot ngưỡng và mã phiên bản rule lúc raise; IT kiểm snapshot không đổi khi rule đổi sau đó | ⬜ | L67 (đã chốt, có điều kiện CLAUDE.md) |

### Pha 4 – Đo lường và vận hành
| ID | Việc | Trạng thái | Phụ thuộc |
|---|---|---|---|
| 4.1 | F14: Micrometer `Timer` đo `raise → push sau commit`, gắn tag theo mức; IT kiểm timer được ghi | ⬜ | Không |
| 4.2 | Ngưỡng NFR-004 (5 phút) đưa vào cấu hình, không hard-code | ⬜ | Không |
| 4.3 | L11/L12: worker + relay RabbitMQ. Chỉ chuyển khi đã kiểm tra không có đường R0 nào phụ thuộc relay | ⏸ | L70 (có điều kiện) → E25 |

### Pha 5 – Unit test và kiến trúc
| ID | Việc | Trạng thái |
|---|---|---|
| 5.1 | F8: unit test thuần cho `Alert` (vòng đời, `recordRecurrence`), `AlertRuleCatalog` (không trùng mã, JSON hợp lệ), parse chính sách. Mục tiêu ≥ 80% dòng của `alerting.domain` và `alerting.application` | ⬜ |
| 5.2 | Kiểm tra `ArchitectureTest` vẫn xanh; alerting chỉ dùng `identity.contract`, `masterdata.contract`, `dataio.application` | ⬜ |

### Pha 6 – Giao diện (UI) cho E04

Điều kiện chung: bản vẽ được PXVB duyệt (`DESIGN-SYSTEM.md` §9); khung tương ứng sẵn sàng (`src/web` React chưa có; RN tablet UI chưa có). Mọi màn đạt cổng §8 và đủ sáu trạng thái §5, với nội dung cảnh báo thật (không chỉ khung chung).

**6a. Thiết kế (D) — làm được ngay, không cần khung code**
| ID | Việc | Trạng thái | Phụ thuộc |
|---|---|---|---|
| D1 | Bổ sung T-12: luồng xử lý (chọn nguyên nhân từ danh mục `ALERT_CAUSE`, bắt buộc; ghi chú, bắt buộc; trạng thái Đang xử lý / Đã xử lý); xem lịch sử xử lý | ✅ `T12-Alerts` (5 bước, nút hoàn tất có điều kiện) | L64, L72 |
| D2 | Bổ sung W-AlertCenter: lọc theo ca, máy, khoảng thời gian; nút xuất Excel; chi tiết có lịch sử xử lý và cấp leo thang; khu vực thống kê tần suất (FR-CC-054) | ✅ `W-AlertCenter` (lọc ca/máy/ngày, Excel, thống kê, cột leo thang, lịch sử) | L68, L72 |
| D3 | Vẽ màn Quy tắc cảnh báo: người nhận, leo thang, cửa sổ dedup, mức, bật/tắt, lịch sử (FR-CC-050) | ✅ `W-AlertRules` (mẫu W2). **Cần sửa theo L67:** đang vẽ "hiệu lực từ" và "lịch sử phiên bản", phải đổi thành audit trước/sau + lý do (xem D8) | L67 |
| D8 | Sửa W-AlertRules theo L67: bỏ "hiệu lực từ" và "lịch sử phiên bản"; thay bằng lịch sử thay đổi (audit) có lý do; ghi chú rằng alert lưu snapshot rule | ⬜ (bản vẽ chưa sửa) | D3, L67 |
| D4 | Vẽ chuông và danh sách thông báo web; vẽ màn danh sách thông báo tablet | ✅ `W-Notifications`, `T13-Notifications`; nút Thông báo trong header web và tablet | – |
| D5 | Cập nhật ánh xạ mức và trạng thái: `DESIGN-SYSTEM.md` §3, T-12, W-AlertCenter (F18) | ✅ Đã áp dụng L71–L72 trong `T12-Alerts`, `T13-Notifications`, `W-AlertCenter` và `DESIGN-SYSTEM.md` §3 | L71, L72 (đã chốt) |
| D6 | Viết nội dung cảnh báo thật cho 6 trạng thái của các màn trên (không dùng khung chung); kiểm cổng §8: bàn phím, tương phản, 1366×768, tablet 10″ (F19) | 🟡 Đã kiểm bố cục headless (2026-10-25): không tràn khung ở 1366×800 và 1280×800, nút tablet ≥ 56 px, ô nhập có nhãn. **Còn:** 6 trạng thái nội dung cảnh báo thật, bàn phím/Tab, đo tương phản, 1366×768, tablet 10″ thật | D1–D5 |
| D7 | Snooze (FR-CC-055) | ⏸ | L69 — không vẽ |

**6b. Build (6.x) — sau khi duyệt bản vẽ và có khung**
| ID | Việc | Trạng thái | Phụ thuộc |
|---|---|---|---|
| 6.1 | Tablet: màn T-12; banner realtime theo mức, rung/âm cấu hình được; nối `/user/queue/alerts` (STOMP, CONNECT bằng Bearer); bù khi online lại qua sync pull (F13) | ⬜ | D1, D4, khung RN, E09 |
| 6.2 | Web: trung tâm cảnh báo (lọc, Excel, chi tiết và xử lý); gọi API 3.1, 3.2 | ⬜ | D2, khung `src/web`, Pha 2, Pha 3 |
| 6.3 | Web: màn quy tắc cảnh báo; `PUT /alerting/rules/{code}`; hiển thị lỗi VALIDATION theo từng ô | ⬜ | D3, khung web, 3.3 |
| 6.4 | Web: chuông và danh sách thông báo (`/notifications/unread-count`, đọc một và đọc hết; STOMP `/user/queue/notifications`) | ⬜ | D4, khung web |
| 6.5 | Kiểm UI: sáu trạng thái, bàn phím; E2E luồng nhận cảnh báo → xử lý → RESOLVED | ⬜ | 6.1–6.4 |

**Việc của epic khác (không tính vào E04, ghi để theo dõi)**
| ID | Việc | Trạng thái | Chờ |
|---|---|---|---|
| X1 | Nguồn phát 22 rule (mục 4) | ⬜ | Từng epic |
| X2 | `ShiftCloseWatcher` → `CC_SHIFT_NOT_CLOSED` | ⬜ | E14 |
| X3 | Báo cáo định kỳ qua email | ⏸ | RP (E15/E24), L18/L26 |
| X4 | Snooze (FR-CC-055) — build | ⏸ | L69 |

### Pha 7 – Nghiệm thu và tài liệu
| ID | Việc | Trạng thái |
|---|---|---|
| 7.1 | `./mvnw verify` xanh trên 8.0 và 8.4, trừ 13 lỗi baseline ngoài E04 (ghi rõ) | ⬜ |
| 7.2 | Review bảo mật: quyền ack/xử lý (F10), WebSocket (SUBSCRIBE/SEND), input (F7) | ⬜ |
| 7.3 | Cập nhật `PROGRESS.md` (E04), `OPEN-QUESTIONS.md` (L64–L72, đóng L63 nếu xử lý xong), `docs/design/README.md` và `DESIGN-SYSTEM.md` §10 khi bản vẽ T-12/W-AlertCenter đổi | ⬜ |

## 8. Trạng thái theo yêu cầu (đích cần đạt)

| Yêu cầu | Hiện tại | Đích E04 | Pha |
|---|---|---|---|
| FR-CC-050 | 🟡 (thiết kế ⬜) | ✅ (audit + lý do + snapshot, theo L67) · UI | 3.3, D3, D8, 6.3 |
| FR-CC-051 | 🟡 (thiết kế ⬜) | ✅ · UI xử lý | 2.1, 2.4, D1, 6.1, 6.2 |
| FR-CC-052 | 🟡 | ✅ | 2.5, 1.4 |
| FR-CC-053 | 🟡 (server; thiết kế một phần) | ✅ server · ✅ tablet | 6.1, D1, D4 |
| FR-CC-054 | 🟡 (thiết kế ⬜ cho ca/Excel/thống kê) | ✅ API · ✅ UI | 3.1, 3.2, D2, 6.2 |
| FR-CC-055 | ⏸ | ⏸ | D7, X4 |
| NFR-004 (cảnh báo) | ⬜ | ✅ có đo | 4.1, 4.2 |
| Email CRITICAL | ✅ | ✅ | giữ nguyên (F2 bổ sung nâng mức) |
| Notification in-app | ✅ server | ✅ + chuông web | 1.1, D4, 6.4 |
| Rule seed | ✅ | ✅ | giữ nguyên |

## 9. Rủi ro

| Mức | Rủi ro | Giảm thiểu |
|---|---|---|
| CAO | F1: CI đỏ ngẫu nhiên làm mất niềm tin vào test | Pha 0 tái hiện trước khi sửa |
| CAO | F2: cảnh báo CRITICAL không gửi email khi tái diễn | Test đỏ ở 2.2 |
| CAO | F10: người không thuộc phạm vi xử lý cảnh báo. PO đã chấp nhận permission-only đến khi có L07 (L65) | Không đưa nhiều dây chuyền ra production trước L07; làm 2.4 sau L07 |
| CAO | L67: CLAUDE.md yêu cầu ngưỡng dùng masterdata có phiên bản; nếu rule cảnh báo là ngưỡng thì quyết định audit-only (L67) bị vượt | Kiểm CLAUDE.md và xác nhận với PO trước khi làm 3.3; nếu cần thì đổi sang phiên bản (cỡ L) |
| TB | L70: chuyển relay sang E25 có thể chặn email CRITICAL hoặc outbox khác nếu còn phụ thuộc | Kiểm điều kiện L70 trước khi chuyển; đến lúc đó scanner chạy đơn nút |
| TB | F5: `escalateDue` chậm khi số alert OPEN tăng | Giảm N+1 ở 1.4; đo ở 4.1 |
| TB | 22 rule chưa có nguồn phát dễ tạo cảm giác "đã có" | Ghi rõ ở mục 4; không tính vào tiến độ E04 |
| TB | Bộ đếm và scanner trong tiến trình (L11) khi nhiều nút | Ghi rõ G5; chuyển E25 |
| THẤP | Đổi chuỗi `ALERT_CAUSE` đã seed giả định (L27) | Chờ PXVB cung cấp danh sách thật |
| CAO | Bản vẽ chưa duyệt, UI bị chặn hoặc phải làm lại (F16–F19) | Làm D1–D6 trước khi khung UI sẵn sàng; chốt L71–L72 trước khi duyệt |
| TB | Nhầm "Cấp A" của QC với mức CRITICAL của cảnh báo chung (F18) | Chốt L71; tách nhãn trong bản vẽ |

## 10. Nhật ký thực hiện
| Ngày | Ghi chú |
|---|---|
| 2026-10-25 | Rà soát tĩnh E04 theo SRS §4.6, §4.9, NFR-004; phát hiện F1–F15; tạo kế hoạch này. **Chưa chạy test, chưa sửa mã.** Chờ xác nhận để bắt đầu Pha 0 |
| 2026-10-25 | Pha 2.1 và 2.2–2.3 (mã): thêm `AlertService.requireResolutionNote` (L64) và nhánh tái diễn nâng mức (L66, `recur` + `renotify`, `Alert.raiseSeverity`). Unit `AlertInputRulesTest` 8/8 xanh; `ArchitectureTest` 16/16 xanh; `AlertingIT` biên dịch được. **IT chưa chạy được:** Testcontainers không nhận Docker của Colima (`/var/run/docker.sock` không có, `DOCKER_HOST` và `docker.host` không được dùng trong JVM test). Đã thử tạm `~/.testcontainers.properties` rồi xóa. Cần xử lý môi trường (ví dụ symlink socket) trước khi xác nhận IT |
| 2026-10-25 | PO duyệt đề xuất dev-team cho L64–L70 (xem mục 6). Ghi vào `OPEN-QUESTIONS.md` (đã chốt). Đánh dấu 0.4 ✅; cập nhật mục 7, 8, 9. Chưa sửa mã, chưa sửa bản vẽ W-AlertRules (D8 ⬜) |
| 2026-10-25 | Pha 0.1 xong (5/5 xanh trên 8.4). Full verify 8.4: 93 test, 13 lỗi baseline E02, không có E04. F1 không tái hiện. Pha 1.2 và 1.3 xong (TDD: test đỏ do thiếu symbol, rồi xanh; `AlertingIT` + `AlertRealtimeIT` vẫn 10/10 xanh). Chưa làm 1.1 (F1, không tái hiện nên chưa sửa), 1.4 (F5), Pha 2+ (chờ L64–L66). |
| 2026-10-25 | Chốt L71–L72 theo đề xuất (ghi `OPEN-QUESTIONS.md` mục E04, đánh dấu đã chốt); D5 xong: nhãn Khẩn / Cảnh báo / Thông tin, trạng thái theo L72, bậc leo thang "Bậc n", áp dụng trong T-12, T-13, W-AlertCenter và `DESIGN-SYSTEM.md` §3. L64–L70 vẫn chờ PXVB. **D6 (kiểm trình duyệt) chưa làm.** |
| 2026-10-25 | D1–D4 xong: sửa `generator/specs.py` và `generator/lib.py`, chạy lại `specs.py` và `states.py` (tái sinh đúng, không đổi màn ngoài phạm vi trừ header). Màn mới/cập nhật: `T12-Alerts`, `T13-Notifications`, `W-AlertCenter`, `W-AlertRules`, `W-Notifications`; README đợt 6 đã ghi. **Chưa kiểm trong trình duyệt (D6), chưa chốt L71–L72 (D5).** |
| 2026-10-25 | Đối chiếu với `docs/design/` (T-12, W-AlertCenter, DESIGN-SYSTEM §3–§8): thêm mục 2b, F16–F19, L71–L72; Pha 6 tách thành thiết kế (D1–D7) và build (6.1–6.5); việc của epic khác chuyển sang X1–X4. **Chưa sửa bản vẽ** (chờ duyệt) |
| 2026-10-25 | Pha 2.4 (L07, alerting là consumer đầu tiên) theo TDD: 4 IT đỏ rồi xanh; `AlertingIT` 20/20, `AlertRealtimeIT` 2/2, `ArchitectureTest` 16/16. Lệch so với quyết định L07: snapshot lưu `line_code` (mã dây chuyền trong `md_machine`), không phải `line_id`, vì masterdata chỉ có mã. UNIT chưa được mở rộng (masterdata chưa có đơn vị) nên UNIT không thấy cảnh báo nào |
