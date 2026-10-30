# Điểm cần chốt & giả định phát sinh khi implement

Bổ sung cho SDD §17. Mỗi mục: quyết định đang áp dụng, ảnh hưởng, ai cần xác nhận.

| # | Vấn đề | Đang áp dụng | Ảnh hưởng | Cần ai chốt |
|---|---|---|---|---|
| L01 | Q16 tách React/Angular | **Toàn bộ frontend dùng React** (web quản lý + dashboard + tablet RN), không dùng Angular như SDD §2.2 | `src/web`, §2.4, §15 | PO (đã chốt) |
| L02 | Quyền SUPER cho trigger append-only `sys_audit_log` | Migration tạo trigger chặn UPDATE/DELETE; khi bật binlog cần user migration có `SUPER` hoặc `log_bin_trust_function_creators=1` | Triển khai/DBA | CNTT TLSG |
| L03 | Phân vùng `sys_audit_log` theo tháng (NFR §4.3) | Chưa làm ở E01; cần đổi PK thành (id, occurred_at) khi bật partition | Hiệu năng audit 3 năm | Dev lead + CNTT |
| L04 | Rate limiting (PXVB-CC-0429) | Đã có cho `/auth/login`, `/auth/pin-login`, `/devices/activate`: cửa sổ cố định, khóa theo (đường dẫn, IP kết nối, `X-Device-Id`), mặc định 10 lần / 15 phút (`pxvb.security.rate-limit-*`), bộ đếm trong bộ nhớ một tiến trình (G4); API chung theo người dùng chưa làm (G1) | ATTT §16.3 | Dev lead (chuyển Redis ở E25) |
| L05 | LDAP v3 (Q17) | `AuthSource.LDAP` đã có trong mô hình, chưa cài đặt đăng nhập LDAP | Identity | CNTT |
| L06 | Màn hình tra cứu audit (FR-CC-022, P1) | Đã có API `GET /api/v1/identity/audit-logs` (lọc, phân trang con trỏ, khoảng thời gian bắt buộc: mặc định 24 giờ, tối đa 31 ngày), lịch sử theo bản ghi, xuất Excel tối đa 50.000 dòng (ghi audit `EXPORT`); che giá trị trường nhạy cảm khi trả. Chưa có UI | RP/Admin UI | Dev lead |
| L07 | Phạm vi dữ liệu `ScopeFilter` (FR-CC-006, P1) | Hiện chỉ có `DataScope` + `permits()`, chưa có filter dùng chung ở repository. **Đã chốt (2026-10-25):** bản ghi ngoài phạm vi trả **403**; dây chuyền của máy đánh giá theo thời điểm sự kiện (lưu `line_id` snapshot trên bản ghi, không đánh giá lại khi máy đổi dây chuyền); phân công theo ca và quyền ĐBCL tách sang L73. **Đã chốt đầy đủ (2026-10-25):** (a) `IScopeFilter` lọc bằng predicate SQL ở repository, dùng chung cho list, count, detail, export, thống kê; scope rỗng nghĩa là không có quyền (fail-closed); (b) contract trung tính (`DataScope`, `ScopeCriteria`) ở `pxvb-building-blocks`, mỗi module tự ánh xạ thực thể sang LINE/UNIT; máy → dây chuyền qua `MasterdataQueries`, bổ sung `lineCode` vào `MachineInfo`; (c) ca làm việc không có dây chuyền: phạm vi LINE của ca suy từ máy thuộc ca, không thêm cột vào ca; (d) UNIT mở rộng thành tập dây chuyền lúc truy vấn qua masterdata; (e) nhiều scope là hợp, không mở rộng quyền; (f) đổi scope có hiệu lực khi làm mới token; (g) thứ tự: alerting trước (F10, E04 2.4), rồi áp thử danh sách ca (E02 3.3) | Các phân hệ nghiệp vụ; `pxvb-building-blocks`; `MasterdataQueries`; `AlertService` (E04 2.4, cần cột `line_id` snapshot); `production` (E02 3.3) | Đã xác nhận (Dev lead và PO theo phản hồi của bạn) |
| L08 | Outbox → RabbitMQ/STOMP | Mới có outbox + dispatcher + `OutboxSink`; chưa có sink RabbitMQ | E04 | Dev lead |
| L09 | Spring Modulith `verify()` | Dùng ArchUnit trong E01–E02; bật Modulith `verify()` khi có ≥ 2 module nghiệp vụ | Kiến trúc | Dev lead |
| L10 | Cột `Idempotency-Key` TTL 30 ngày | Chưa có job dọn (`IdempotencyCleanup` ở Quartz, §4.9) | E04/E05 | Dev lead |
| L11 | Worker chưa nạp module nghiệp vụ | Outbox dispatcher và các scanner (SLA duyệt, leo thang cảnh báo) đang chạy trong `pxvb-api` (cờ `PXVB_OUTBOX_DISPATCHER`, `PXVB_WORKFLOW_SCANNER`, `PXVB_ALERT_SCANNER`); chuyển sang worker + Quartz khi worker nạp module | Vận hành/HA | Dev lead |
| L12 | Realtime nhiều tiến trình | STOMP dùng broker trong tiến trình; cảnh báo phát từ worker chưa đẩy được tới tablet (client dùng polling 60s dự phòng). Cần relay RabbitMQ (cùng L08) | FR-CC-053 | Dev lead |
| L13 | Vai trò/SLA của các luồng duyệt seed (Q15) | `FlowSeeder` đặt vai trò + SLA theo giả định (vd `SHIFT_CLOSE` = ALL của 4 bên; chưa tự bỏ qua bên "không phát sinh") | E03, PL/PR/OH | PXVB |
| L14 | Xác nhận điện tử khi duyệt | Engine duyệt chưa bắt re-auth; `SignatureService` cung cấp ký (hash do server tính) để module nghiệp vụ gọi tại bước cần ký | FR-CC-031 | PXVB + Pháp chế (Q13) |
| L15 | "Bản sửa đổi báo cáo" khi điều chỉnh bản ghi đã FINALIZED (FR-CC-011) | Mới có điều chỉnh + audit + sự kiện `adjustment.applied`; tạo `Revision+1` do phân hệ RP thực hiện khi có (xem ADR-0001, câu hỏi L58) | RP | Dev lead |
| L16 | Nguyên nhân xử lý cảnh báo | Hiện là chuỗi tự do (bắt buộc); chuyển sang danh mục `md.ReasonCode(ALERT_CAUSE)` khi có MD | FR-CC-051 | PXVB |
| L17 | Snooze cảnh báo (FR-CC-055, P2), xuất Excel trung tâm cảnh báo (FR-CC-054) | Chưa làm | Alerting | PO |
| L18 | Kênh email cho thông báo (Q22) | Chỉ có in-app + realtime; `Notification.channel` đã sẵn | Alerting | CNTT |
| L19 | Danh mục khai báo bằng đặc tả (`CatalogSpec`) | CRUD/import/export/audit chung bằng JDBC thay vì entity JPA từng bảng; luật riêng ở `BomNormService`, `PairingService`, `ThresholdService` | MD | Dev lead |
| L20 | Checklist, vật tư dự kiến của công việc bảo trì | Lưu dạng cột JSON (`checklist`, `parts`) thay vì bảng `MaintChecklistItem`/`MaintTaskPart` riêng (SDD §5.2); tách bảng nếu PM cần truy vấn theo dòng | MD, PM | Dev lead |
| L21 | Ngưỡng `md.Threshold` | Tạo mới mặc định `ACTIVE` bởi người có quyền ghi (SYS_ADMIN, Phó QĐ); chưa chạy qua luồng duyệt `MD_NORM_CHANGE`. Cache tra cứu trong tiến trình 60s (chưa dùng Redis) | BR-CC-004, BR-MD-008 | PXVB |
| L22 | Duyệt định mức NVL | Duyệt trực tiếp bằng quyền `md.bomNorm.approve` (Phó QĐ/QĐ), chưa nối engine duyệt đa bước của workflow | FR-MD-007 | PXVB |
| L23 | Khóa tham chiếu liên module (BR-MD-003) | Có SPI `UsageChecker`; PL/MT/PM… phải đăng ký để chặn khóa SP/máy/NVL đang có lệnh mở (AC-MD-004) | MD, PL | Dev lead |
| L24 | Nhập Excel bộ ghép cặp vấn–bao | Chưa có mẫu nhập riêng (dùng API/lưới); các danh mục khác và `bom-norms` đã có | FR-CC-070 | PO |
| L25 | Dữ liệu gốc | Giờ ca A/B/C, khung cấp phát 25%, mã dừng A–I, ngưỡng mặc định là giá trị khởi tạo giả định (Q04, Q05, Q19) — cần nghiệp vụ TLSG xác nhận | MD | PXVB |

## Quyết định đã chốt (PO, 2026-10-13)

| # | Quyết định | Việc cần làm tiếp |
|---|---|---|
| L25 | **Giữ** giờ ca A 07–15, B 15–23, C 23–07; 4 khung × 25% mỗi ca. Mã dừng A–I, ngưỡng mặc định vẫn là giá trị khởi tạo, chỉnh qua Excel/API khi có số thực | Không |
| L22 | Duyệt định mức NVL **trực tiếp bằng quyền** (Phó QĐ/QĐ) | Không (giữ hiện trạng) |
| L14 | Xác nhận điện tử (nhập lại mật khẩu/PIN + hash) **chỉ ở bước quan trọng**: chốt ca, duyệt KHSX tuần, xác nhận lệnh SX, nghiệm thu TDT | Module PL/PR/OH gọi `SignatureService` đúng các bước này |
| L21 | Ngưỡng cảnh báo: **Phó QĐ/Quản trị áp dụng trực tiếp** (ACTIVE ngay, có hiệu lực + audit) | Không (giữ hiện trạng) |
| L05 | **Chưa dùng LDAP**, tài khoản cục bộ | Hoãn |
| L18 | **Có gửi email** (SMTP nội bộ) cho: cảnh báo CRITICAL; yêu cầu duyệt/điều chỉnh chờ duyệt; báo cáo ngày/tuần/tháng | Làm kênh email trong alerting; **cần PXVB/CNTT cung cấp host/port/tài khoản SMTP và danh sách người nhận báo cáo** |
| L17 | **Xuất Excel trung tâm cảnh báo làm ngay**; snooze để sau | Làm API xuất Excel cảnh báo |
| L24 | Ghép cặp vấn–bao **nhập trên lưới**, không cần mẫu Excel | Đóng mục |
| L13 | Chốt ca bắt buộc: **Vận hành (Trưởng ca), Thống kê (sản lượng/NVL/tem), KSCL/ĐBCL, Cấp phát NVL**; bên "không phát sinh" tự bỏ qua. Không còn bước "Kỹ thuật" bắt buộc | Sửa luồng seed `SHIFT_CLOSE`; logic tự bỏ qua làm ở PR |
| L02 | DBA **bật `log_bin_trust_function_creators=1`** (hoặc cấp quyền) để migration tạo trigger append-only | Ghi vào hướng dẫn triển khai |
| L16 | Nguyên nhân xử lý cảnh báo **chọn từ danh mục** `ALERT_CAUSE` | Đổi `resolve` sang mã lý do + **PXVB cung cấp danh sách nguyên nhân** |

## Quyết định đã chốt (PO, 2026-10-18)

**Giữ nguyên hiện trạng (đã xác nhận):**

| # | Quyết định |
|---|---|
| L35 | Mở ca thiếu bố trí nhân sự hoặc thiếu lệnh xác nhận: **chỉ cảnh báo**, không chặn |
| L46 | Bên Vận hành (Trưởng ca) xác nhận bằng **chữ ký điện tử khi gửi chốt**; luồng `SHIFT_CLOSE` giữ 3 bên duyệt (Thống kê, KSCL, Cấp phát NVL) |
| L52 | Xác nhận phiếu cấp phát NVL **không ký điện tử**, chỉ bằng quyền |
| L29 | Đăng nhập PIN trên tablet **không** bắt đổi PIN lần đầu |
| L32 | Gợi ý lệnh SX **giữ phân bổ đều** (chưa xét công suất/lịch bảo trì) |
| L54 | **Không bắt buộc** kiểm đếm tồn NVL giữa/cuối ca (giữ cách tính theo cấp vượt định mức) |
| L48 | `CHK_UNSYNCED` **giữ chặn theo mọi thiết bị** đang hoạt động |
| L41 | Chu kỳ ghi sản lượng **giữ 120 phút** (tối thiểu 4 lần/ca) |
| L53 | Tiêu hao lý thuyết máy vấn = định mức × bao của máy bao × `FeedRatio` ghép cặp: **đúng, giữ** |
| L51 | Loại phiếu theo nhóm NVL (sợi/vấn → máy vấn; bao và nhóm khác → máy bao): **xác nhận** |

**Thay đổi so với hiện trạng (L42, L49, L28/L46, L50 đã làm xong 2026-10-18; L55, L40 còn lại — xem `docs/PROGRESS.md` §3b):**

| # | Quyết định | Việc cần làm |
|---|---|---|
| L42 | Performance của OEE **chặn tối đa 100%** | ✅ Đã làm (công thức phiên bản 2). Đặt `CapPerformanceAt100 = true` cho công thức mặc định (phiên bản mới), cập nhật golden test |
| L49 | Ngoài thiếu lượt ghi sản lượng, **cho giải trình + Phó QĐ/QĐ duyệt** thay vì chặn hẳn với: giao nhận NVL trễ (`CHK_MATERIAL`), tranh chấp nhận NVL, thiết bị chưa đồng bộ (`CHK_UNSYNCED`) | ✅ Đã làm: các mục BLOCK tương ứng `approvable = true`; có test |
| L28/L46 | Bên "không phát sinh" **tự bỏ qua theo dữ liệu**: ca không có bản ghi cấp phát NVL → bỏ bước Cấp phát NVL; không có lượt KSCL → bỏ bước KSCL; tương tự Thống kê | ✅ Đã làm: `ApprovalService.start(..., skippedRoleCodes)` + SPI `ShiftCloseParty` (MT đăng ký bên Cấp phát NVL; Thống kê luôn bắt buộc; bên KSCL tự bỏ qua khi QC đăng ký SPI); audit `skippedParty` |
| L50 | `FINALIZED`: **tự động sau N ngày** kể từ `CLOSED` | ✅ Đã làm: `ShiftFinalizer` (cấu hình `SHIFT_FINALIZE_DAYS`, mặc định 7 — **PXVB chọn N**); ca FINALIZED không mở lại bằng điều chỉnh cấp ca |
| L55 | MT cần làm sớm (R1): **xuất PDF/Excel báo cáo tiêu hao**, **xu hướng tiêu hao + đề xuất điều chỉnh định mức**, **phiếu theo segment khi đổi mác**. Đối chiếu ERP (FR-MT-011) để sau | Làm trong đợt hoàn thiện E13 |
| L40 | PR cần làm sớm (R1) cả 4: **tồn điếu** (+ `PR_BUFFER_HIGH/LOW`), **đối chiếu sản lượng thống kê**, **giám sát khu vực + nhập hộ**, **phân tích hiệu suất thiết bị** (Pareto, xu hướng OEE, so sánh dòng máy) | Làm trong đợt hoàn thiện E11/E12 |

## Phát sinh khi làm E09/E10 và các việc nhỏ đã chốt

| # | Vấn đề | Đang áp dụng | Cần |
|---|---|---|---|
| L26 | Email (L18) đã làm; tắt mặc định | Bật bằng `PXVB_MAIL_ENABLED=true` + `spring.mail.*`; gửi cho CRITICAL và yêu cầu duyệt/điều chỉnh chờ duyệt. **Báo cáo ngày/tuần/tháng qua email chưa làm** (chờ RP) | CNTT cấp SMTP + người nhận báo cáo |
| L27 | Nguyên nhân cảnh báo (L16) | Đã đổi sang danh mục `ALERT_CAUSE`; seed 6 mã giả định | PXVB cung cấp danh sách thật |
| L28 | Chốt ca `SHIFT_CLOSE` | 4 bên bắt buộc, mỗi bên là nhóm vai trò "bất kỳ" (Thống kê = STAT_OUTPUT/MATERIAL/STAMP; KSCL = QC_INLINE/QA_INSPECTOR/QA_SUPERVISOR). Tự bỏ qua bên "không phát sinh" làm ở PR | PXVB xác nhận nhóm vai trò |
| L29 | Đăng nhập PIN trên thiết bị | Không bị chặn bởi cờ "phải đổi mật khẩu" (PIN do quản trị cấp); mật khẩu web vẫn bắt buộc đổi lần đầu | PO xác nhận |
| L30 | Đồng bộ: chưa có handler nghiệp vụ | Engine, xung đột, giám sát đã xong; handler `PR.*`, `MT.*`, `QC.*`, `TX.*` làm cùng từng phân hệ. Chưa có tombstone cho bảng hard-delete ngoài cờ `alwaysFull` | Dev lead |
| L31 | Tablet RN | Chỉ có lõi TypeScript (hàng đợi, SyncClient) đã test; adapter op-sqlite và UI **chưa build/chạy** trên thiết bị | Dev lead |
| L32 | Gợi ý lệnh SX | Phân bổ đều tổng sản lượng ngày của SP vào các ô (ca × máy bao) khả dụng theo ghép cặp + SP được phép + trạng thái máy; một máy bao một lệnh/ca. Chưa xét lịch bảo trì (chờ PM) và công suất lý thuyết | PXVB xác nhận thuật toán |
| L33 | Lệnh RUNNING/COMPLETED | Do PR chuyển trạng thái khi mở/chốt ca (chưa có PR) | Dev lead |
| L34 | Theo dõi thực hiện kế hoạch (FR-PL-007/008) | Chờ dữ liệu sản lượng từ PR; hiện chỉ có kế hoạch & tiến độ phân bổ | Dev lead |

## Phát sinh khi làm E11 (PR)

| # | Vấn đề | Đang áp dụng | Cần |
|---|---|---|---|
| L35 | Điều kiện mở ca (FR-PR-001) | Chỉ **cảnh báo** (lưu `warnings` trên phiên) khi chưa công bố bố trí nhân sự hoặc chưa có lệnh xác nhận; không chặn mở ca | PXVB chốt có chặn cứng không |
| L36 | Kết ca | `end` chuyển phiên sang `CLOSING`, tự đóng dừng máy đang mở (`AutoClosed`), tính phút chạy/dừng, lệnh → `COMPLETED`. Bước `CLOSED/FINALIZED` (CC-11, báo cáo ca, OEE chính thức) làm ở E14/E12 | Dev lead |
| L37 | Đổi mác | Tách segment nằm ở PR (`pr_product_change`, `segment_no`); chưa ghi ngược `OrderSegment` bên PL | Dev lead |
| L38 | Xác nhận kỹ thuật (FR-PR-005) | `techCauseCode` là chuỗi tự do (chưa có danh mục nguyên nhân kỹ thuật); quyền MNT_LEAD/ENGINEER/Phó QĐ | PXVB cung cấp danh mục |
| L39 | Tạo yêu cầu sửa chữa 1 chạm từ mã dừng C/D (FR-PR-004) | Chỉ trả cờ `canCreateRepairRequest`; liên kết `RepairRequest` chờ phân hệ PM | PM |
| L40 | Chưa làm trong E11 | Tồn điếu (FR-PR-007), đối chiếu thống kê (FR-PR-011), giám sát khu vực/phạm vi dữ liệu (FR-PR-010), `PR_OEE_LOW`/`PR_BUFFER_*` (chờ OEE E12), cảnh báo dữ liệu > 110% công suất (BR-PR-004, chờ công thức E12) | Dev lead |
| L41 | Giờ ca | `BusinessDate` + giờ ca tính theo múi giờ Asia/Ho_Chi_Minh, lưu UTC; chu kỳ ghi sản lượng `OUTPUT_INTERVAL_MIN` lấy từ ngưỡng (mặc định 120') | PXVB xác nhận |

## Phát sinh khi làm E12 (OEE)

| # | Vấn đề | Đang áp dụng | Cần |
|---|---|---|---|
| L42 | Công thức OEE mặc định | `EXCLUDE_PLANNED_STOPS` + `ASSUME_100` (chất lượng 100%) + không chặn Performance ở 100%; đổi bằng `PUT /api/v1/pr/oee-formula` (tạo phiên bản mới, quyền Phó QĐ/quản trị) | PXVB xác nhận quy ước + 10 bộ số liệu golden (AC-PR-002) |
| L43 | Chất lượng (Q) | Chưa nối dữ liệu NC/SPKPH (QC) nên GoodQty = TotalQty; chế độ `FROM_NC_RECORDS`/`FROM_INLINE_REJECT_RATE` chờ QC | QC |
| L44 | Công suất lý thuyết | Lấy từ `md_machine.theoretical_rate` (`K_RODS_PER_MIN` ×1000 → điếu/phút; `PACKS_PER_MIN`); máy chưa khai báo → Performance/OEE = N/A | PXVB nhập công suất 77 máy |
| L45 | Gộp kỳ | DAY/WEEK/MONTH tính khi đọc từ kết quả theo ca (cộng dồn tử–mẫu), chưa lưu riêng; Performance chặn 100% áp ở cấp gộp. OEE luôn `IsProvisional` cho tới khi chốt ca (E14) và xác nhận hết dừng kỹ thuật | Dev lead |

## Phát sinh khi làm E14 (chốt ca CC-11)

| # | Vấn đề | Đang áp dụng | Cần |
|---|---|---|---|
| L46 | Bên Vận hành trong `SHIFT_CLOSE` | Engine duyệt cấm người đề nghị tự duyệt, nên Trưởng ca xác nhận bằng **chữ ký điện tử khi gửi chốt**; luồng chỉ còn 3 bên duyệt (Thống kê, KSCL, Cấp phát NVL). Đổi L28 tương ứng; bên "không phát sinh" vẫn chưa tự bỏ qua | PXVB xác nhận |
| L47 | Kiểm tra chốt ca chưa có nguồn | `CHK_MATERIAL` (MT), `CHK_QUALITY` (QC), `CHK_STAMP` (TX) là SPI `ShiftCloseCheck`, chưa có hiện thực cho tới khi các phân hệ đó được làm | Dev lead |
| L48 | `CHK_UNSYNCED` | Chặn khi bất kỳ thiết bị đang hoạt động còn `pendingCount > 0` hoặc còn xung đột đồng bộ `PR.*` mở (chưa gắn thiết bị với ca) | PXVB xác nhận |
| L49 | Mục BLOCK giải trình được | Hiện chỉ `CHK_OUTPUT_COMPLETE` (AC-PR-006): người có quyền nhập giải trình, Phó QĐ/QĐ duyệt mới gỡ chặn. Dung sai thời gian `CHK_DOWNTIME` không cần vì phút chạy được tính từ phút kế hoạch trừ dừng | PXVB |
| L50 | Mở lại ca | Qua `AdjustmentRequest` (`ShiftSession`, `status` CLOSED → CLOSING); chốt lại sinh báo cáo ca revision mới. `FINALIZED` (sau khi RP/kế toán khóa hẳn) chưa có luồng. `ShiftCloseWatcher` (cảnh báo ca chưa chốt) chưa làm | Dev lead |

## Phát sinh khi làm E13 (MT)

| # | Vấn đề | Đang áp dụng | Cần |
|---|---|---|---|
| L51 | Loại phiếu & máy tiêu thụ | Loại phiếu lấy từ nhóm NVL (`FIBER`/`ROLLING`/`PACKING`, nhóm khác → `PACKING`); sợi/vấn → máy vấn, bao → máy bao, `consumerType` trên dòng định mức ghi đè (BR-MT-002) | PXVB xác nhận |
| L52 | Xác nhận phiếu | STAT_MATERIAL xác nhận trực tiếp bằng quyền, **không** ký điện tử (L14: chỉ ký ở bước quan trọng) | PXVB xác nhận |
| L53 | Sản lượng quy cho máy vấn | Sản lượng bao của máy bao × `FeedRatio` ghép cặp; tiêu hao lý thuyết theo mác thực tế ghi nhận (segment) | PXVB xác nhận cách quy đổi |
| L54 | Không kiểm đếm (BR-MT-004) | Mặc định coi tồn cuối = tồn đầu + cấp − hoàn − lý thuyết; hao hụt chỉ phát hiện qua cấp vượt, chỉ đánh giá khi ca đã kết thúc. Cờ cấu hình `MT_REQUIRE_COUNT` theo nhóm chưa làm | PXVB |
| L55 | Chưa làm trong E13 | Phân tích xu hướng (FR-MT-010) và đề xuất điều chỉnh định mức; đối chiếu ERP (FR-MT-011); truy vết lô nâng cao (FR-MT-012, mới lưu `LotNo`); xuất PDF/Excel báo cáo tiêu hao (AC-MT-006, hiện trả JSON); đổi mác trong ca sinh thêm phiếu theo segment (BR-MT-006); sinh lại phiếu khi lệnh đổi (chỉ có `regenerate` thủ công); phiếu `COMPLETED`/`CANCELLED` | Dev lead |
| L56 | Handler sync `MT.*` | Đã có `MT.IssueMaterial/ConfirmReceipt/RecordReturn/RecordStockCount` nhưng chưa có test tích hợp qua `/sync/push` (logic dùng chung với API đã test) | Dev lead |

## Phát sinh khi thiết kế E15 (RP) — xem `docs/adr/ADR-0001-rp-fact-grain-projection.md`

| # | Vấn đề | Đang áp dụng | Ảnh hưởng | Cần ai chốt |
|---|---|---|---|---|
| L57 | Gộp OEE khi `CapPerformanceAt100 = true` | `compute` trần **từng máy/ca**, `aggregate` trần ở **tổng gộp**. Ví dụ (suy từ mã, chưa chạy): A 120/100, B 60/100 → trần từng máy/ca = 0,80; trần ở tổng = 0,90. **Hội đồng khuyến nghị trần từng máy/ca (0,80), chờ PO xác nhận**: lưu `perf_num` (tử số đã trần) + số thô + `formula_id`, gộp = Σperf_num/Σtheoretical, sửa `aggregate()`, golden test, báo cáo "máy/ca bị áp trần". Chưa chốt: máy/ca thiếu công suất lý thuyết (loại khỏi mẫu số kèm cảnh báo hay N/A cả nhóm) | AC-RP-003 (OEE), báo cáo ngày/tuần/tháng; ADR-0001 D2/O1 | PO (ý nghĩa nghiệp vụ) + Dev lead PR |
| L58 | Chính sách revision báo cáo khi fact đổi sau lúc đã sinh (điều chỉnh, đồng bộ muộn) | Fact luôn mới nhất, `ReportSnapshot` bất biến; chưa quyết ghi đè hay thay thế, có gửi lại/thông báo không, "ngày trước" của R_DAY dùng số gốc hay mới nhất | FR-RP-008, L15 | PO |
| L59 | Ánh xạ `adjustment.applied` về phiên ca | Chỉ `entityType = ShiftSession` đánh dấu bẩn. Đề xuất (review 2026-10-18, nhất trí): registry theo `entityType`, loại chưa ánh xạ **lỗi lớn tiếng** (cảnh báo `RP_ADJUSTMENT_UNMAPPED` + dead-letter), RP sở hữu bộ kích hoạt; đối soát đêm chỉ là lưới thứ hai. `FINALIZED` bị sửa chưa có người kích hoạt tính lại | RP, L50 | Dev lead |
| L60 | Thời hạn lưu online & đối soát | ⚠ GIẢ ĐỊNH: `RECONCILE_DAYS = 35`, `rp_fact_*` online 3 năm (Q18), archive phân vùng cũ theo chính sách sao lưu | RP, vận hành | CNTT / PXVB |
| L61 | Mốc đo SLA READY ≤ 5' (AC-RP-001) | Đề xuất: từ `created_at` outbox `pr.shift.closed` đến `generated_at` snapshot `READY` (gồm trễ dispatcher + projection), ghi thêm `enqueued_at` để tách chờ/tính; chỉ số P95 + cảnh báo, AC nêu rõ profile tải | AC-RP-001 | Dev lead + PO |
| L62 | Sự kiện/contract nguồn còn thiếu cho RP | **Đã làm (Pha 1):** `pr.output.recorded`, `pr.oee.computed`, `mt.consumption.computed`, `ProductionQueries.shiftFacts/sessionsBetween`, `MaterialQueries.consumption`. Đã có test tương đương `shiftFacts` ↔ `pr_shift_report` tại lúc chốt và `MaterialQueries` ↔ API tiêu hao. Còn: sửa `OeeCalculator.aggregate` (chờ PO xác nhận L57) | Đường găng E15 | Dev lead |
| L63 | Test không ổn định `AlertingIT.notificationsCanBeMarkedRead` | Gọi "đánh dấu đã đọc hết" rồi đếm ngay `unread == 0`; scanner nền có thể tạo thông báo mới giữa hai bước. Quan sát (chưa chứng minh nguyên nhân): đỏ 1 lần ở lượt chạy toàn bộ trên MySQL 8.4, xanh khi chạy riêng 2 lần, trên `HEAD` sạch và ở lượt chạy toàn bộ lại | CI có thể đỏ ngẫu nhiên | Dev lead |

**Còn chờ PXVB cung cấp dữ liệu:** thông tin SMTP + người nhận báo cáo (L18); danh sách nguyên nhân cảnh báo (L16);
danh sách 46 biểu mẫu KSCL, danh mục lỗi và ngưỡng A/B/C (Q10); quy cách tem (Q11).

## Phát sinh khi rà soát E04 (alerting) và thiết kế màn cảnh báo — xem `docs/plans/E04-completion.plan.md`

| # | Vấn đề | Đang áp dụng | Ảnh hưởng | Cần ai chốt |
|---|---|---|---|---|
| L64 | Ghi chú khi `RESOLVED` (FR-CC-051) | **Đã chốt (2026-10-25):** bắt buộc, không được rỗng sau khi cắt khoảng trắng; kiểm ở server, lỗi RFC7807 tiếng Việt. Backend hiện chỉ bắt buộc nguyên nhân, cần sửa (F3) | `AlertService.resolve`, T-12, W-AlertCenter | PXVB (đã duyệt) |
| L65 | Ai được tiếp nhận/xử lý cảnh báo: chỉ người nhận, hay mọi người có quyền trong phạm vi? | **Đã chốt (2026-10-25):** giữ quyền `alerting.alert.handle` hiện tại, không giới hạn theo người nhận (để bàn giao ca). Kiểm phạm vi dây chuyền/máy làm khi có L07. **Rủi ro chấp nhận:** đến khi có L07, người giữ quyền ở dây chuyền A xử lý được cảnh báo dây chuyền B; không đưa ra production nhiều dây chuyền trước khi có L07 (F10) | `AlertController`, F10 | PXVB, Dev lead (đã duyệt) |
| L66 | Tái diễn khi mức tăng (WARN→CRITICAL) và tái diễn trong cửa sổ dedup | **Đã chốt (2026-10-25):** khi mức tăng, nâng mức lưu trên alert và gửi lại thông báo; nếu lên CRITICAL thì gửi email. Tái diễn cùng mức trong cửa sổ dedup chỉ tăng bộ đếm, không gửi lại. Sau cửa sổ dedup thì tạo alert mới. Lỗi hiện tại: nhánh alert đã tồn tại bỏ qua mức yêu cầu (F2) | `AlertService.raise`, F2, F11 | PXVB (đã duyệt) |
| L67 | Ngày hiệu lực của rule (FR-CC-050): phiên bản rule hay chỉ audit? | **Đã chốt (2026-10-25):** không làm bảng phiên bản rule. Lưu audit (trước/sau) + lý do thay đổi bắt buộc; mỗi alert lưu snapshot ngưỡng và mã phiên bản rule tại thời điểm raise. Chỉ làm phiên bản đầy đủ khi có AC hoặc quy định yêu cầu tái hiện theo thời điểm. **Cần kiểm trước khi làm:** CLAUDE.md yêu cầu ngưỡng dùng masterdata có phiên bản; nếu rule cảnh báo được coi là ngưỡng thì phải đổi sang phiên bản (cỡ L). Bản vẽ W-AlertRules đang vẽ "hiệu lực từ / lịch sử phiên bản", cần sửa | `AlertRule.configure`, W-AlertRules | PXVB (đã duyệt; kiểm CLAUDE.md) |
| L68 | "Thống kê tần suất" (FR-CC-054) đếm theo gì? | **Đã chốt (2026-10-25):** đếm theo rule và ngày nghiệp vụ (BusinessDate, không theo UTC), trong khoảng chọn, tối đa 90 ngày. Cần index `(rule_code, raised_at)`; lưu UTC trong DB | API thống kê mới, W-AlertCenter | PXVB (đã duyệt) |
| L69 | Snooze (FR-CC-055, P2) có vào R0 không? | **Đã chốt (2026-10-25):** không đưa vào R0; hoãn sang P2 (đã ghi ở L17). Khi làm, alert CRITICAL không được snooze | FR-CC-055 | PO (đã duyệt) |
| L70 | Chuyển worker và relay RabbitMQ (L11, L12) sang E25? | **Đã chốt (2026-10-25), có điều kiện:** chuyển sang E25 cùng L08, chỉ sau khi xác nhận không có đường R0 nào (kể cả email CRITICAL và outbox như `pl.order.confirmed`) phụ thuộc relay. Đến lúc đó, scanner chạy trên một instance duy nhất | Vận hành, realtime nhiều nút | Dev lead (đã duyệt, còn điều kiện) |
| L71 | Mức hiển thị cảnh báo chung: có dùng "Cấp A/B/C" không, và có tách khỏi cấp lỗi QC không? | **Đã chốt (2026-10-25):** cảnh báo chung hiển thị theo mức `CRITICAL` = **Khẩn** (đỏ), `WARN` = **Cảnh báo** (vàng), `INFO` = **Thông tin** (xanh mực). Chữ "Cấp A/B/C" chỉ dùng cho cấp lỗi QC (`DESIGN-SYSTEM.md` §3). Bậc leo thang hiển thị là "Bậc 0/1/2", không dùng "Cấp" | Bản vẽ T-12, W-AlertCenter, T-13, `DESIGN-SYSTEM.md` §3 (đã cập nhật) | PXVB (xác nhận nhãn) |
| L72 | Nhãn trạng thái cảnh báo | **Đã chốt (2026-10-25):** OPEN = "Chưa xác nhận", ACK = "Đã biết", IN_PROGRESS = "Đang xử lý", RESOLVED = "Đã xử lý", CLOSED = "Đã đóng" | Bản vẽ T-12, W-AlertCenter; `DESIGN-SYSTEM.md` §3 | PXVB (xác nhận nhãn) |
| L73 | Phân công theo ca (Trưởng ca) và quyền ĐBCL "dữ liệu của mình trước khi nộp" (FR-CC-006) | Chưa có mô hình dữ liệu: `DataScope.Type` chỉ có ALL/LINE/UNIT, không gắn theo ca hay theo người tạo bản ghi. Tách khỏi L07 theo quyết định 2026-10-25 | Trưởng ca; ĐBCL; phân hệ production và QC | Dev lead, PO (cần thiết kế riêng) |

## Phát sinh khi rà soát E08 (masterdata: phân cấp lỗi QC) — xem `docs/plans/E08-completion.plan.md`

| # | Vấn đề | Đang áp dụng | Ảnh hưởng | Cần ai chốt |
|---|---|---|---|---|
| L74 | Quy tắc cấp B của phân cấp lỗi QC (UQ3a, FR-QC-005, FR-MD-012) | **Đã chốt (2026-10-25, theo yêu cầu người dùng):** "ngoài biên một lần = B", tức là một lần ngoài biên trong cỡ mẫu thì cấp B. Biểu diễn bằng `md_defect_grade_rule` với `metric = COUNT_IN_SAMPLE`, `grade = B`, `threshold_value = 1`; cỡ mẫu và biên theo biểu mẫu/lỗi. ⚠ Toán tử "đạt ngưỡng" (≥ 1) do E18 hiện thực, cần kiểm khi làm E18. Ngưỡng và cỡ mẫu thật chờ Q10 | `md_defect_grade_rule`, `DefectGradeRules`, E18, `docs/design/README.md` | ĐBCL xác nhận lại nhãn và phạm vi; người chốt ban đầu: người dùng (2026-10-25) |
| L75 | Quy tắc cấp A ("liên tiếp") và định nghĩa cấp C của phân cấp lỗi QC (UQ3b) | **Đã chọn phương án (a) 2026-10-25 (theo yêu cầu người dùng):** E08 chỉ lưu cấp B; cấp A chưa kích hoạt đến khi E18 định nghĩa cửa sổ. **Rủi ro chấp nhận:** SLA 30' (FR-QC-005, BR-QC-004) không được đánh giá cho đến khi có định nghĩa. **Chưa làm trong code:** `defect-grade-rules` vẫn chấp nhận A/B/C. **Vẫn chưa chốt:** bốn mục định nghĩa dưới đây. Bản nháp ghi "liên tiếp = A", nhưng `md_defect_grade_rule` chỉ có `COUNT_IN_SAMPLE` và `RATE_PCT`, không có thứ tự, nên chưa biểu diễn được. Cần trả lời: (1) "liên tiếp" tính trong một mẫu, qua các mẫu liền kề của cùng máy, hay qua ca; (2) cửa sổ N và quy tắc reset khi đứt chuỗi; (3) cấp C là gì (không đạt B và A?), vì bản nháp chưa định nghĩa. Hai phương án: (a) E08 chỉ lưu B, A ở trạng thái chưa kích hoạt đến khi E18 định nghĩa cửa sổ (Architect, QA đề xuất); (b) thêm metric chuỗi (ví dụ `CONSECUTIVE_OUT_OF_LIMIT`) với khóa thứ tự và tham số N, cần migration trong E08. A kích hoạt SLA 30 phút (FR-QC-005, BR-QC-004), nên không đưa vào vận hành khi chưa chốt. ⚠ Ngưỡng thật chờ Q10 | `md_defect_grade_rule`, `DefectGradeRules`, E18 (BR-QC-003), FR-QC-005 | PO đã chọn (a) 2026-10-25; ĐBCL và QC lead chốt định nghĩa trước khi E18 bắt đầu |
