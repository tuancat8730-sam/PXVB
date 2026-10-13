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

Đã làm: Sprint 0 phần backend (E00), E01 (building blocks), E02 (identity), E03 (module `workflow`: luồng duyệt,
ủy quyền, xác nhận điện tử, điều chỉnh sau khóa), E04 (module `alerting`: rule, cảnh báo, thông báo, STOMP).
Chưa làm: khung frontend React (`src/web`, dashboard, tablet), E05 trở đi (xem SDD §18.2).

## Quy ước tích hợp giữa module

- Module chỉ dùng `identity` qua `vn.pxvb.identity.contract` (`IdentityDirectory`, `AccessTokenVerifier`).
- Module nghiệp vụ khai báo quyền bằng bean `PermissionContributor` (vai trò mặc định chỉ gán một lần).
- Điều chỉnh dữ liệu đã khóa: module đăng ký bean `AdjustmentApplier` cho `entityType` của mình.
- Phát cảnh báo: gọi `AlertRaiser.raise(...)` theo mã rule; duyệt: `ApprovalService.start(flowCode, entityId, ...)`
  và lắng nghe `WorkflowEvents.ApprovalCompleted`; ký: `SignatureService.sign(...)` với hash tính phía server.
- Repository dạng interface lồng trong lớp `Repositories` (đã bật `considerNestedRepositories`).
