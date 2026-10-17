# PXVB – Hướng dẫn cho Claude Code

- Nguồn sự thật yêu cầu: `docs/PXVB-SRS-SDD.md`. Tham chiếu ID (FR/BR/AC/NFR) trong commit & PR.
- Kiến trúc: modular monolith Java 21 + Spring Boot 3.5 (Maven); mỗi module có package
  `domain/application/infrastructure/api/contract`; không tham chiếu chéo trừ `contract`
  (ArchUnit trong `hosts/pxvb-api/src/test`); nhóm bảng MySQL riêng theo tiền tố, Flyway migration theo module,
  MySQL 8 (tương thích 8.0/8.4).
- Quy ước: UUIDv7 khóa chính; UTC trong DB; BusinessDate+ShiftCode; enum lưu chuỗi UPPER_SNAKE; lỗi RFC7807
  tiếng Việt, mã `PXVB-<MOD>-<nnnn>`.
- Mọi lệnh ghi từ tablet: `Idempotency-Key` + `X-Device-Id`; xung đột không ghi đè.
- Dữ liệu đã CONFIRMED/LOCKED không sửa trực tiếp → AdjustmentRequest.
- Không hard-code ngưỡng/giờ ca/mã lý do/công thức: dùng masterdata có phiên bản.
- UI 100% tiếng Việt, định dạng vi-VN; frontend toàn bộ dùng React (quyết định Q16); tablet: nút ≥ 56dp,
  pass/fail ≤ 3 chạm.
- Trước khi coi là xong: build + test xanh, permission & audit đã áp, AC liên quan đạt, OpenAPI cập nhật.
- Chỗ nào "⚠ GIẢ ĐỊNH" trong tài liệu: làm cấu hình được và ghi vào `docs/OPEN-QUESTIONS.md`.

## Lệnh thường dùng

- Build + test backend (cần Java 21): `cd src/backend && ./mvnw verify`
- Không có Java cục bộ: `docker run --rm --network host -v /var/run/docker.sock:/var/run/docker.sock
  -v "$PWD":/w -v pxvb-m2:/root/.m2 -w /w maven:3.9-eclipse-temurin-21 mvn -B verify`
  (test tích hợp dùng Testcontainers nên cần socket Docker).
- Test trên MySQL 8.4: thêm `-Dpxvb.test.mysql=8.4`.
- Hạ tầng dev: `docker compose -f docker-compose.dev.yml up -d`.
- Biến môi trường bắt buộc khi chạy `pxvb-api`: `PXVB_SECURITY_JWT_SECRET` (≥ 32 byte), `PXVB_DB_URL`,
  `PXVB_DB_USER`, `PXVB_DB_PASSWORD`; tùy chọn `PXVB_BOOTSTRAP_ADMIN_PASSWORD` để tạo tài khoản `admin` lần đầu.

## Trạng thái triển khai

Bảng theo dõi chi tiết từng epic: `docs/PROGRESS.md` (cập nhật khi hoàn thành/đổi trạng thái một epic).

Đã làm: Sprint 0 phần backend (E00), E01 (building blocks), E02 (identity), E03 (module `workflow`: luồng duyệt,
ủy quyền, xác nhận điện tử, điều chỉnh sau khóa), E04 (module `alerting`: rule, cảnh báo, thông báo, STOMP).
E05 (module `dataio`: nhập/xuất Excel), E06–E08 (module `masterdata`: máy, cụm, ghép cặp, ca, SP, NVL, định mức
có phiên bản, ngưỡng, mã dừng/lý do, mẫu KSCL, lỗi/luật, bảo trì, phụ tùng, liên kết).
E09 (module `sync`: push/pull/xung đột/giám sát thiết bị; lõi ngoại tuyến tablet `src/tablet`), E10 (module
`planning`: KHSX tháng/tuần, lệnh SX, bố trí nhân sự), E11 (module `production`: phiên ca, sản lượng, dừng máy,
đổi mác, dashboard thời gian thực, cảnh báo `PR_OUTPUT_BEHIND`/`PR_STOP_LONG`, handler sync `PR.*`), E12 (OEE: `OeeCalculator` thuần miền + golden test, công thức có phiên bản, `PR_OEE_LOW`), E14 (chốt ca CC-11: bộ kiểm tra `ShiftCloseCheck`, giải trình, ký + luồng `SHIFT_CLOSE`, báo cáo ca, mở lại qua điều chỉnh).
Chưa làm: khung frontend React (`src/web`, dashboard, UI tablet RN), E13 (MT) và E15 trở đi (xem SDD §18.2).

## Đồng bộ ngoại tuyến & kế hoạch

- Lệnh từ tablet: hiện thực `sync.contract.SyncCommandHandler` cho từng loại lệnh (idempotent do engine lo; ném
  `ConflictException`/`RejectedException`). Dữ liệu kéo về: hiện thực `common.sync.SyncPullProvider`.
- Lõi tablet kiểm thử: `cd src/tablet && npm test` (cần Node ≥ 22.5, dùng `node:sqlite`).
- Lệnh SX xác nhận phát `PlanningEvents.OrderConfirmed` (+ outbox `pl.order.confirmed`) để MT/QC/PR sinh phiếu/lịch/phiên.
- PR: ghi nhận chỉ khi phiên ca `OPEN` (BR-PR-001); thời gian lấy từ `OccurredAt`; dừng máy không chồng lấn
  (ràng buộc cả ở DB: một sự kiện mở/máy). Lệnh sync: `PR.RecordOutput`, `PR.StartStop`, `PR.EndStop`, `PR.ChangeProduct`.
  PR chỉ đọc PL qua `planning.contract.PlanningQueries`; mở ca tạo `MachineRun` cho mọi máy đang hoạt động.
- Luồng duyệt có thể khai báo vai trò dạng danh sách (`"A,B"` = bất kỳ vai trò nào) trong `roleCode` của bước.

## Masterdata: thêm danh mục mới

- Khai báo `CatalogSpec` trong `Catalogs.java` + bảng `md_*` trong migration (tên cột = snake_case của tên thuộc tính).
  API `/api/v1/md/{resource}`, quyền `md.<permKey>.<view|create|update|export|approve>`, mẫu import và export tự có.
- Luật riêng (phiên bản, ràng buộc chéo) viết thành dịch vụ riêng, không nhồi vào `CatalogService`.
- Module khác lấy dữ liệu qua `MasterdataQueries` (ngưỡng, định mức hiệu lực, ghép cặp); chặn khóa bằng `UsageChecker`.
- Nhập Excel: hiện thực `dataio.contract.ImportTemplate` + `ImportTemplate.Provider`.

## Quy ước tích hợp giữa module

- Module chỉ dùng `identity` qua `vn.pxvb.identity.contract` (`IdentityDirectory`, `AccessTokenVerifier`).
- Module nghiệp vụ khai báo quyền bằng bean `PermissionContributor` (vai trò mặc định chỉ gán một lần).
- Điều chỉnh dữ liệu đã khóa: module đăng ký bean `AdjustmentApplier` cho `entityType` của mình.
- Phát cảnh báo: gọi `AlertRaiser.raise(...)` theo mã rule; duyệt: `ApprovalService.start(flowCode, entityId, ...)`
  và lắng nghe `WorkflowEvents.ApprovalCompleted`; ký: `SignatureService.sign(...)` với hash tính phía server.
- Repository dạng interface lồng trong lớp `Repositories` (đã bật `considerNestedRepositories`).
