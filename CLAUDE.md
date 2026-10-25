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

## Docker và test tích hợp (macOS + Colima)

Máy phát triển dùng Colima (Docker Engine 29). Test tích hợp (`*IT`) chạy MySQL thật bằng Testcontainers, nên cần
Docker đang chạy. Đã kiểm chứng 2026-10-25: `MasterdataIT` 9/9, `MasterdataE08IT` 4/4 trên MySQL 8.0 và 8.4.

**Quy trình chuẩn**

1. Bật Colima và kiểm tra: `colima status`, rồi `env -u DOCKER_HOST docker info`.
2. Chạy mọi lệnh Docker/Testcontainers với `env -u DOCKER_HOST` nếu shell có `DOCKER_HOST` trỏ tới socket không
   tồn tại (ví dụ `~/.docker/run/docker.sock` của Docker Desktop). Testcontainers dùng `/var/run/docker.sock`, là
   symlink tới socket của Colima, nên không cần biến nào khác.
3. Chạy IT **qua failsafe** (`verify`), không qua `-Dtest=`. Chỉ failsafe mới nạp `api.version=1.44` từ
   `hosts/pxvb-api/pom.xml`; nếu bỏ qua, Testcontainers gửi API 1.32 và Docker 29 từ chối.
   - Một IT: `env -u DOCKER_HOST ./mvnw -B -pl hosts/pxvb-api -am verify -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MasterdataIT -Dfailsafe.failIfNoSpecifiedTests=false`
   - Toàn bộ: `env -u DOCKER_HOST ./mvnw -B verify`
   - MySQL 8.4: thêm `-Dpxvb.test.mysql=8.4` (mặc định 8.0).
4. Không cần đặt `TESTCONTAINERS_RYUK_DISABLED`. Chỉ đặt khi gặp lỗi `error while creating mount source path ...
   operation not supported` (Ryuk không mount được socket Colima): `TESTCONTAINERS_RYUK_DISABLED=true`.

**Lỗi thường gặp**

| Thông báo | Nguyên nhân | Cách xử lý |
|---|---|---|
| `Could not find a valid Docker environment` | `DOCKER_HOST` trỏ sai | Bước 2 |
| `client version 1.32 is too old` | Chạy IT qua surefire (`-Dtest=`) | Bước 3 |
| `mkdir .../docker.sock: operation not supported` | Ryuk mount socket | Bước 4 |

**Baseline đã biết** (chưa thuộc epic nào được sửa; không tính là lỗi của phần đang làm): full `verify` trên 8.0
ngày 2026-10-25 có 14 test đỏ: `ProductionIT` 11 và `MaterialIT` 2 (lệch thời gian ca). Mọi test còn lại xanh.

## Kế hoạch theo epic (`docs/plans/`)

Mẫu: `docs/plans/TEMPLATE.md` (cấu trúc lấy từ `docs/plans/E04-completion.plan.md`). Mỗi epic đang làm có một tệp.

**Tạo plan cho một epic**

1. Đặt tên `docs/plans/E<nn>-completion.plan.md` (epic đã có code) hoặc `E<nn>-<slug>.plan.md` (epic chưa làm). Chép
   `TEMPLATE.md` làm khung, rồi điền từng mục.
2. Trước khi viết pha, đọc hiện trạng code (lớp, endpoint, migration, test đã có) và bản vẽ nếu có UI. Ghi bằng chứng
   là đường dẫn file/dòng. Không suy đoán: "đã có" hay "chưa có" phải kiểm trong repo.
3. Lấy FR/BR/AC/NFR từ SRS §5–§18 của epic; ghi phát hiện là F#, giả định là G#, quyết định/câu hỏi là D# hoặc L#.
4. Việc UI: kế hoạch và bản vẽ nằm trong `docs/design/<EPIC>-ui-plan.md`; plan chính chỉ tham chiếu tới đó.
5. Trạng thái ✅ chỉ khi đã chạy kiểm chứng (test, lệnh cụ thể). Ghi rõ phần chưa chạy được và lý do.

**Sau khi xong mỗi task (bắt buộc, ngay sau task, không gom cuối đợt)**

- Cập nhật dòng task trong mục 7 của plan: trạng thái và kết quả (số test, lệnh, tệp).
- Nếu task làm đổi trạng thái một yêu cầu, cập nhật mục 8.
- Thêm một dòng vào mục 10 (nhật ký): ngày, ID task, kết quả kiểm chứng, và chỗ nào lệch so với kế hoạch.
- Nếu phát hiện mới trong lúc làm, thêm vào mục 3 (F#) hoặc mục 6 (D#/L#); không để chỉ nằm trong câu trả lời.
- Không commit trừ khi được yêu cầu.

## Thiết kế UI (`docs/design/`)

Hệ thiết kế: `docs/DESIGN-SYSTEM.md` (token, ngôn ngữ trạng thái §3, thành phần §4, sáu trạng thái §5, mẫu §6,
cổng kiểm tra §8, duyệt §9). Mọi kết quả thiết kế nằm trong `docs/design/`; không tạo bản vẽ ở nơi khác.

**Nguồn nội dung một màn**

- `docs/design/generator/specs.py`: nội dung từng màn (tablet `T-…`, web `W-…`, kiosk). Muốn đổi nội dung một màn,
  sửa mục của nó ở đây. Muốn đổi giao diện chung, sửa `lib.py`.
- `docs/design/generator/states.py`: sinh sáu trạng thái (`S-<mẫu>-<trạng thái>.dc.html`) cho từng mẫu.
- **Không sửa tay** `docs/design/screens/*.dc.html`: các tệp này được sinh lại mỗi lần chạy generator.

**Quy trình cho một màn mới hoặc đổi màn**

1. Xác định mẫu (`DESIGN-SYSTEM.md` §6) và phân loại: tablet (T-A/T-B/T-C/T-D/T-E/T-F), web (W1–W6), kiosk (K1).
   Tên tệp: `T<nn>-<Tên>.dc.html` cho tablet, `W-<Tên>.dc.html` cho web và kiosk (ví dụ `W-RpKiosk.dc.html`). Tiêu đề màn:
   "<Mã> – <Tên tiếng Việt>".
2. Lấy yêu cầu từ SRS: FR/BR/AC của epic liên quan (§4–§15). Ghi ID vào tiêu đề hoặc ghi chú của màn để truy vết.
3. Viết nội dung trong `specs.py` theo đúng ngôn ngữ trạng thái §3: "Khẩn / Cảnh báo / Thông tin" cho mức cảnh báo;
   "Cấp A/B/C" chỉ cho cấp lỗi QC; không hard-code ngưỡng thật, dùng số minh họa và ghi chú "dữ liệu minh họa".
4. Chạy sinh lại: `cd docs/design/generator && python3 specs.py && python3 states.py`. Nếu cần sáu trạng thái cho
   mẫu mới, thêm vào `states.py`. Kiểm tra `git status`: chỉ các tệp của màn đang làm được đổi; thay đổi ngoài phạm vi
   (ví dụ tiêu đề header dùng chung) phải ghi rõ trong nhật ký.
5. Tiêu đề, ghi chú và mẫu đều bằng tiếng Việt có dấu; định dạng ngày/số theo vi-VN.

**Cổng kiểm tra trước khi ghi là xong** (`DESIGN-SYSTEM.md` §8)

- Đủ sáu trạng thái (đang tải, trống, lỗi, một phần, thành công, không có quyền) qua `states.py`.
- Bàn phím: thứ tự Tab, tiêu điểm nhìn thấy; thao tác thay thế cho kéo-thả (nút Lên/Xuống, menu).
- Nhãn gắn với ô nhập; tương phản chữ ≥ 4,5:1 (đo bằng công cụ, không ghi "đạt" khi chưa đo).
- Cỡ chạm tablet ≥ 56 px (CLAUDE.md: nút ≥ 56 dp); tablet pass/fail ≤ 3 chạm.
- Không tràn khung ở 1366×768 (web), tablet 10″, kiosk 1920×1080; nội dung dài không vỡ bố cục.
- Chỉ ghi "đã kiểm trình duyệt" khi đã mở và đo thật (Chrome). Nếu chưa, ghi "chưa kiểm" và liệt kê việc còn lại.

**Trạng thái và ghi nhận** (sau mỗi màn hoặc đợt)

- Trạng thái màn: `Đã vẽ, chờ duyệt` → `Đã duyệt (ngày, người duyệt)`. Không build UI khi chưa duyệt (`DESIGN-SYSTEM.md` §9).
- Cập nhật `docs/design/README.md`: bảng của đợt tương ứng (màn, file, thay đổi, trạng thái).
- Cập nhật `docs/DESIGN-SYSTEM.md` §10 khi số màn đã vẽ hoặc việc chưa làm thay đổi.
- Kế hoạch UI của epic: `docs/design/<EPIC>-ui-plan.md` (ví dụ `E08-ui-plan.md`). Sau mỗi màn, cập nhật dòng tương ứng
  và thêm nhật ký. Plan epic trong `docs/plans/` chỉ tham chiếu tới đó (xem mục "Kế hoạch theo epic").
- Không commit trừ khi được yêu cầu.

## Trạng thái triển khai

Bảng theo dõi chi tiết từng epic: `docs/PROGRESS.md` (cập nhật khi hoàn thành/đổi trạng thái một epic).

Đã làm: Sprint 0 phần backend (E00), E01 (building blocks), E02 (identity), E03 (module `workflow`: luồng duyệt,
ủy quyền, xác nhận điện tử, điều chỉnh sau khóa), E04 (module `alerting`: rule, cảnh báo, thông báo, STOMP).
E05 (module `dataio`: nhập/xuất Excel), E06–E08 (module `masterdata`: máy, cụm, ghép cặp, ca, SP, NVL, định mức
có phiên bản, ngưỡng, mã dừng/lý do, mẫu KSCL, lỗi/luật, bảo trì, phụ tùng, liên kết).
E09 (module `sync`: push/pull/xung đột/giám sát thiết bị; lõi ngoại tuyến tablet `src/tablet`), E10 (module
`planning`: KHSX tháng/tuần, lệnh SX, bố trí nhân sự), E11 (module `production`: phiên ca, sản lượng, dừng máy,
đổi mác, dashboard thời gian thực, cảnh báo `PR_OUTPUT_BEHIND`/`PR_STOP_LONG`, handler sync `PR.*`), E12 (OEE: `OeeCalculator` thuần miền + golden test, công thức có phiên bản, `PR_OEE_LOW`), E14 (chốt ca CC-11: bộ kiểm tra `ShiftCloseCheck`, giải trình, ký + luồng `SHIFT_CLOSE`, báo cáo ca, mở lại qua điều chỉnh), E13 (module `material`: phiếu cấp phát tự sinh khi xác nhận lệnh, cấp/nhận, hoàn trả, kiểm đếm, tiêu hao + `MT_WASTE_HIGH`, `CHK_MATERIAL`, handler sync `MT.*`).
Chưa làm: khung frontend React (`src/web`, dashboard, UI tablet RN), E15 trở đi (xem SDD §18.2).

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
