# PXVB – Hệ thiết kế giao diện

Nguồn yêu cầu: `docs/PXVB-SRS-SDD.md` §14.2 (dashboard), §15 (tablet), §16.4 (truy cập, định dạng vi-VN). Quyết định L01: toàn bộ frontend dùng React.

**Bản xem được (canvas 14 bảng):** https://claude.ai/artifact/RYsmJi9rPFJfsjReCWR4jj (riêng tư; chỉ chủ sở hữu mở được cho đến khi chia sẻ).

**Bản vẽ nguồn & trạng thái từng màn hình:** `docs/design/README.md`. Canvas bổ sung (73 màn + 60 bảng trạng thái): https://claude.ai/artifact/976ez4LHLR95GAre8a8Bng.

> **Trạng thái (24/10/2026):** đề xuất thiết kế, chưa duyệt. Đã vẽ đủ 77/77 màn trong §7 và sáu trạng thái cho từng mẫu (§10). Thương hiệu (logo, màu chủ đạo, font) **chưa có**; bộ màu/chữ dưới đây là đề xuất, đổi được qua token. Số liệu trong bản vẽ là dữ liệu minh họa, không phải số thật. Các ngưỡng/chuẩn của biểu mẫu KSCL (T-11) chỉ minh họa, chờ danh mục thật (Q10). Đã dựng thử 128 trang trong Chrome (bố cục, cỡ nút, nhãn ô nhập) nhưng **chưa kiểm tra bàn phím, tương phản bằng công cụ, cỡ 1366×768 và 5 màn dùng vòng lặp canvas**; danh sách việc chưa làm ở §10.

## 1. Hướng thiết kế

- **Mục đích:** ba nhóm người dùng lặp lại cùng thao tác mỗi ngày: văn phòng (quản lý, kế hoạch, duyệt), xưởng (tablet, đeo găng, ánh sáng chói), phòng quản đốc (nhìn từ xa).
- **Giọng điệu:** công nghiệp, điềm tĩnh, dày thông tin. Số liệu và trạng thái là nhân vật chính; màu thương hiệu chỉ cho hành động chính và vị trí hiện tại.
- **Chi tiết nhớ được:** ngôn ngữ trạng thái luôn có **hình + nhãn chữ** (tròn / vuông / tam giác / vành), nên không phụ thuộc màu (SDD §16.4).
- **Ba mật độ cùng một khung:** web (điều khiển cao 36 px), tablet (≥ 56dp, nền sáng tương phản cao), kiosk (nền tối, chữ ≥ 28 px).

## 2. Token

Đặt trong một nguồn duy nhất (CSS variables, dùng chung web/dashboard/kiosk; tablet RN đọc cùng bảng giá trị).

### 2.1 Màu

| Token | Giá trị | Dùng cho |
|---|---|---|
| `--brand` | `#174E86` | Nút chính, mục menu hiện tại, liên kết |
| `--brand-soft` | `#E6EEF8` | Nền mục chọn, chip thông tin |
| `--ink` / `--ink-2` / `--ink-3` | `#16202B` / `#3F4B5A` / `#5E6A7A` | Chữ chính / phụ / chú thích |
| `--bg` / `--surface` / `--surface-2` | `#F3F5F8` / `#FFFFFF` / `#F8FAFC` | Nền trang / thẻ / tiêu đề bảng |
| `--line` / `--line-strong` | `#D9DFE6` / `#B9C2CD` | Viền thường / viền ô nhập |
| `--ok` / `--ok-soft` | `#1E7A46` / `#E2F3E9` | Bình thường (xanh) |
| `--warn` / `--warn-soft` / `--warn-solid` | `#8A5A00` / `#FFF0C7` / `#F2B01E` | Cần chú ý (vàng): chữ / nền / nút lớn |
| `--bad` / `--bad-soft` | `#B3261E` / `#FBE3E0` | Xử lý ngay (đỏ) |
| `--off` / `--off-soft` | `#4A5666` / `#E6E9ED` | Offline, không có dữ liệu |
| Kiosk | nền `#0E1620`, thẻ `#16222F`, chữ `#E8EEF4`, xanh `#4CC38A`, vàng `#F5C451`, đỏ `#FF6B5E` | Chỉ cho kiosk |

Màu trạng thái giữ đúng quy ước SDD §14.2 (xanh / vàng / đỏ) và **tách khỏi màu thương hiệu**. Ngưỡng chuyển màu lấy từ `md.Threshold`, đổi ngưỡng áp dụng sau ≤ 60 giây (AC-RP-004).

### 2.2 Chữ

Be Vietnam Pro (UI; thiết kế cho tiếng Việt, giấy phép OFL) và IBM Plex Mono (mã máy, số phiếu). Bật `font-variant-numeric: tabular-nums` cho mọi số.

| Cỡ | Web | Tablet | Kiosk |
|---|---|---|---|
| Tiêu đề trang | 22/28 · 700 | 36/44 · 700 | 44/52 · 700 |
| Tiêu đề nhóm | 16/22 · 600 | 24/30 · 700 | 32/40 · 600 |
| Nội dung | 14/20 | **20/28 (≥ 18sp)** | 28/36 |
| Ô bảng / phụ | 13 / 12 | 18/26 | 24/30 |
| Số liệu nổi bật | 28/34 · 700 | **32–80 (≥ 24sp)** | 84/92 · 700 |

Định dạng vi-VN: `1.234,5` · `18/10/2026 15:30` · múi giờ Asia/Ho_Chi_Minh. Không dùng chữ in hoa nhỏ (mất dấu tiếng Việt).

### 2.3 Khoảng cách, hình, tương tác

- Thang khoảng cách: 4 · 8 · 12 · 16 · 24 · 32.
- Bo góc: web 6, thẻ 8, tablet 12. Bóng đổ chỉ cho lớp phủ (hộp thoại, ngăn kéo).
- Điều khiển: web cao 36; **tablet ≥ 56dp (mẫu dùng 64–96)**; vùng chạm cách nhau ≥ 12 để chạm găng.
- Tiêu điểm bàn phím: viền 3 px `--brand`, cách 2 px (tablet: viền vàng 4 px). Chuyển động ≤ 150 ms, tôn trọng "giảm chuyển động".
- Tương phản chữ ≥ 4,5:1; màu cần phân biệt phải khác cả độ sáng và có hình/nhãn đi kèm.

## 3. Ngôn ngữ trạng thái

| Trạng thái | Hình | Nhãn mẫu | Màu |
|---|---|---|---|
| Đang chạy / Đạt / Bình thường | tròn đặc | Đang chạy · Đạt | xanh |
| Đang dừng / Chặn / Xử lý ngay | vuông đặc | Đang dừng · Chặn | đỏ |
| Chưa nhập quá hạn / Cần giải trình | tam giác | Chưa nhập · Giải trình | vàng |
| Offline / Không có dữ liệu | vành rỗng | Offline · Chưa có dữ liệu | xám |
| Cấp cảnh báo QC | chữ | Cấp A (đỏ) · Cấp B (vàng) · Cấp C (xanh mực) | |
| Mức cảnh báo chung (CRITICAL / WARN / INFO) | chữ + màu | Khẩn (đỏ) · Cảnh báo (vàng) · Thông tin (xanh mực). "Cấp A/B/C" chỉ dùng cho cấp lỗi QC (L71) | |
| Trạng thái cảnh báo (OPEN / ACK / IN_PROGRESS / RESOLVED / CLOSED) | chữ | Chưa xác nhận · Đã biết · Đang xử lý · Đã xử lý · Đã đóng (L72) | |
| Bậc leo thang | chữ | Bậc 0 · Bậc 1 · Bậc 2 (không gọi là "Cấp") | |
| Vòng đời bản ghi | chữ | Nháp · Chờ duyệt · Đã duyệt · Từ chối · Đã chốt (khóa) | |

Dữ liệu `CONFIRMED`/`LOCKED` hiện biểu tượng khóa và **không có nút Sửa**; sửa đi qua "Yêu cầu điều chỉnh" (đúng quy ước dự án).

## 4. Thành phần

Nút (chính / phụ / ghost / nguy hiểm / vô hiệu / chỉ biểu tượng), ô nhập (thường / lỗi / chỉ đọc-đã khóa, có đơn vị, mã dạng mono), nhãn trạng thái, biểu ngữ cảnh báo (cấp A/B/C), snackbar "Hoàn tác" 10 giây (SDD §15.3), bảng dữ liệu (số căn phải, "—" nghĩa là chưa có dữ liệu, không phải 0), thanh trạng thái tablet (máy · ca · mác · đồng hồ · đồng bộ · chuông; SDD §15.1). Bảng vẽ: "Thành phần & trạng thái".

## 5. Sáu trạng thái nội dung (mỗi màn hình phải có)

| Trạng thái | Quy tắc |
|---|---|
| Đang tải | Giữ nguyên khung (skeleton), có nút Hủy với báo cáo dài |
| Trống | Giải thích vì sao trống + hành động hữu ích |
| Lỗi | Nói rõ lỗi (mã `PXVB-<MOD>-<nnnn>`, tiếng Việt), có "Thử lại"; dữ liệu cục bộ vẫn an toàn |
| Một phần | Hiện phần có dữ liệu, mục thiếu ghi **"Chưa có dữ liệu"**, không điền 0 (vd báo cáo ca khi QC/TX chưa tích hợp) |
| Thành công | Xác nhận + bước kế tiếp hoặc hoàn tác |
| Không có quyền | Nêu ranh giới quyền + đường gửi yêu cầu |

## 6. Mẫu màn hình

| Mẫu | Bảng vẽ | Đặc điểm |
|---|---|---|
| **W1** Danh sách + chi tiết | Web-List | Thanh công cụ (tìm, lọc cột, chọn cột, Excel), bảng 40 px, ngăn chi tiết bên phải có tab |
| **W2** Biểu mẫu có phiên bản/duyệt | Web-Form | Phiên bản + hiệu lực, so sánh với bản cũ, lỗi tại ô, nút Duyệt chỉ mở khi hợp lệ, lịch sử bên phải |
| **W3** Lưới điều độ | Web-Grid | Hàng = máy, cột = ca; kéo-thả kèm nút thay thế bàn phím; ngữ cảnh bên phải |
| **W4** Quy trình duyệt/checklist | Web-ShiftClose | Mục kiểm tra Đạt / Giải trình / Chặn, tiến trình xác nhận nhiều bên, lý do nút vô hiệu |
| **W5** Dashboard | Web-Dashboard | KPI trên cùng, lưới trạng thái, danh sách dừng/cảnh báo, "cập nhật lúc", cảnh báo dữ liệu cũ > 5 phút |
| **W6** Báo cáo | Web-Report | Xem trước dạng giấy (PDF/Excel), mục thiếu nguồn ghi rõ, chọn revision, chữ ký các bên |
| **K1** Kiosk | Kiosk | Nền tối, 4–5 trang xoay vòng, số liệu ≥ 84 px |
| **T-B** Chi tiết + hành động lớn | Tablet-Machine | 6 nút lớn, trạng thái ca/lệnh, lượt KSCL kế tiếp |
| **T-C** Nhập số | Tablet-Output | Bàn phím số lớn, tự điền khoảng, so sánh kế hoạch, cảnh báo ngoài biên |
| **T-D** Chọn một trong N | Tablet-Stop | 9 nút A–I, nhóm theo loại, xác nhận bên phải |
| **T-E** Form đạt/không đạt | Tablet-QC | Đạt/Không đạt một chạm, ô số có chuẩn, dự kiến cấp A/B/C |
| **T-A / T-F** | ghép từ thành phần / khung W4 | Ô lớn & danh sách việc; checklist của lãnh đạo ca |

## 7. Ánh xạ mọi màn hình trong đặc tả

### 7.1 Tablet (SDD §15.2)

| Mẫu | Màn hình |
|---|---|
| T-A | T-01 đăng nhập nhanh · T-02 trang chủ theo vị trí · T-10 danh sách lượt KSCL · T-12 cảnh báo · T-40 việc bảo trì hôm nay · T-70 trạng thái đồng bộ |
| T-B | T-03 chi tiết máy – ca hiện tại |
| T-C | T-04 ghi sản lượng · T-07 tồn điếu · T-20 soạn hàng NVL · T-21 nhận NVL · T-22 kiểm đếm & hoàn trả · T-30 tem xuất dùng · T-31 tem hư/mất |
| T-D | T-05 dừng máy · T-06 đổi mác |
| T-E | T-11 form KSCL động · T-41 thực hiện bảo trì · T-50 ĐBCL |
| T-F | T-60 mở/đóng ca · T-61 xác nhận lệnh & bố trí · T-62 xung đột đồng bộ · T-42 báo sự cố/tiếp nhận/duyệt phiếu · T-32 tem chốt cuối ca |

### 7.2 Web (theo phân hệ; chỉ liệt kê màn hình mà SDD đã nêu)

| Phân hệ | Màn hình → mẫu |
|---|---|
| CC | Yêu cầu điều chỉnh của tôi / chờ tôi duyệt (FR-CC-014) → W4 · Tra cứu audit (FR-CC-022) → W1 · Trung tâm cảnh báo → W1 · Giám sát thiết bị & đồng bộ (FR-CC-066) → W1 · Trạng thái dịch vụ (FR-CC-090) → W5 · Công việc nền Quartz → W1 |
| MD (§5.7) | Danh sách + form từng danh mục → W1 · Lưới ghép cặp vấn–bao → W3 · Trình soạn định mức → W2 · Ma trận ngưỡng → W3 · "Chất lượng dữ liệu" → W1 · Người dùng & ma trận phân quyền → W3 · Trình thiết kế biểu mẫu KSCL (§9.4) → không có mẫu chung, đã vẽ riêng ở trạng thái tĩnh (thao tác kéo-thả và bàn phím chưa thiết kế; xem §10, mục B2) |
| PL (§6.6) | Lịch KHSX tháng, Lưới KHSX tuần, Bảng điều độ lệnh, Bố trí nhân sự → W3 · Xác nhận lệnh đầu ca → T-61 / W4 · Theo dõi thực hiện → W5 |
| MT | Phiếu cấp phát → W1 + W2 · Giao nhận và chốt tiêu hao ca → W4 · Báo cáo tiêu hao → W6 |
| PR | Dashboard ca, giám sát khu vực (FR-PR-010), phân tích OEE (FR-PR-013) → W5 · Phiên ca → W1 · Chốt ca (CC-11) → W4 |
| QC | Lịch kiểm tra, tra cứu phiếu → W1 · Tỷ lệ lỗi thời gian thực → W5 · Xử lý SPKPH → W4 · B21 tháng → W6 |
| TX | Block tem → W1 + W2 · Đối chiếu tem–bao và chốt → W4 · Giao diện kế toán/thuế → W6 |
| PM | Lịch bảo trì tháng, hoán đổi lịch (FR-PM-002/009) → W3 · Phiếu công việc/sửa chữa → W1 + W2 · Lịch sử bảo trì theo máy → W1 · Duyệt sửa chữa → W4 · Dashboard CMMS → W5 |
| OH | KH trung/đại tu năm → W3 · Hồ sơ → W2 · Nghiệm thu, đóng hồ sơ → W4 · Tổng hợp vật tư → W6 · Tiến độ → W5 |
| SP | Thẻ kho → W1 · Nhập/xuất → W2 · Yêu cầu xuất → W4 · Tồn tối thiểu → W1 |
| RP (§14) | 9 dashboard `DB_*` → W5 (cùng khung, khác widget) · `DB_WALL` → K1 · Báo cáo `R_*` → W6 · Subscription → W1 |

SDD chưa mô tả đầy đủ màn hình web của MT, QC, TX, OH, SP; ánh xạ trên suy ra từ bảng chức năng và sẽ được xác nhận khi làm epic tương ứng.

## 8. Hệ quả cho triển khai frontend

- Khung `src/web` (React, L01): đặt token thành CSS variables; thành phần nền (Button, Field, Chip, Banner, DataTable, Drawer, Stepper) trước, rồi ráp 6 mẫu web. Tablet RN dùng cùng bảng giá trị token.
- SDD §2.2 vẫn ghi Angular cho web quản lý; L01 đã đổi sang React, nên SDD cần cập nhật khi chốt thiết kế.
- Mỗi màn hình làm xong phải qua cổng: bàn phím + tiêu điểm hiển thị, nhãn, tương phản, cỡ chạm, sáu trạng thái, nội dung dài, các cỡ web 1366×768 / tablet 10″ / kiosk 1920×1080.

## 9. Việc chưa làm / cần quyết định

| Việc | Ai |
|---|---|
| Duyệt hướng thiết kế; cung cấp logo, màu, font của đơn vị nếu có | PXVB |
| Kiểm tra bản vẽ bằng trình duyệt thật (nhiều cỡ, bàn phím, tương phản) | Làm một phần (bố cục, cỡ nút, nhãn); phần còn lại ở §10, nhóm A |
| Thiết kế riêng: trình thiết kế biểu mẫu KSCL, lịch tháng PL, đăng nhập, mẫu T-A / T-F | Đã vẽ (chờ duyệt), xem §10; lịch PM dùng dạng lưới tuần W3 |
| Chọn vị trí đặt token (package dùng chung web/tablet) khi dựng `src/web` | Dev lead |
| Đo thực tế độ chói và cỡ chạm trên tablet IP54 ở xưởng | Chưa làm |

## 10. Tiến độ thiết kế màn hình

Cập nhật 30/10/2026 (duyệt đợt 7 E08; trước đó 24/10/2026 sau đợt 5). Cách đếm: mỗi mục ngăn bằng "·" trong §7 là một màn hình; RP tính 4 loại (dashboard `DB_*`, `DB_WALL`, báo cáo `R_*`, subscription). Bản vẽ nguồn và trạng thái từng màn: `docs/design/README.md`; bản xem được: https://claude.ai/artifact/976ez4LHLR95GAre8a8Bng.

| Nhóm | Tổng | Đã vẽ | Chưa vẽ |
|---|---|---|---|
| Tablet (§7.1) | 24 | 24 | **0** |
| Web (§7.2) | 53 | 53 | **0** |
| Cộng | 77 | 77 | **0** |

Mọi màn trong §7 đã có bản vẽ **đề xuất**. Đợt 7 (E08 MD, U-1…U-7) đã **duyệt ngày 30/10/2026**; các đợt còn lại vẫn **chờ duyệt**. Trong đó 4 màn tablet (T-03, T-04, T-05, T-11) nằm ở bảng mẫu đợt 1; 73 màn còn lại nằm ở canvas bổ sung. Từ đợt 5, 49 màn được sinh từ bộ dựng `docs/design/generator/` (mẫu W1–W6, K1, T-A…T-F), nên cùng một khung và dễ sửa hàng loạt, nhưng mỗi màn chỉ có **một trạng thái nội dung** và nội dung là dữ liệu minh họa.

### Đã làm (24/10/2026)

| Hạng mục | Kết quả |
|---|---|
| Vẽ đủ màn hình trong §7 | 77/77, đề xuất chờ duyệt |
| Dựng thử trong Chrome (128 trang: 68 màn tĩnh + 60 bảng trạng thái) | Không tràn khung ở 1366×800 / 1280×800 / 1920×1080; nút tablet ≥ 56 px; ô nhập có nhãn gắn đúng |
| Lỗi tìm thấy và đã sửa | Nhãn chưa gắn ô nhập (8 trang); chip xuống dòng trong lưới; kiosk chỉ phân biệt bằng màu; trạng thái Thành công tablet tô sai dòng |
| Sáu trạng thái (§5) | Có cho W1–W6, T-A, T-C, T-F, K1 (60 bảng `S-*`); mọi màn theo trạng thái của mẫu nó dùng |
| Lưu nguồn vào repo | `docs/design/screens/` (134 file) và `docs/design/generator/` (`lib.py`, `specs.py`, `states.py`) |

### Việc chưa làm

**A. Kiểm tra chưa đủ**

| # | Việc | Ghi chú |
|---|---|---|
| A1 | Dựng thử 5 màn viết tay dùng vòng lặp canvas: `W-PlanCalendar`, `W-Permissions`, `W-PrDashboard`, `W-PrOee`, `W-MaintSchedule` | Cần mở trong canvas thật; `W-MaintSchedule` còn chip trong lưới có thể xuống dòng như lỗi đã sửa ở bộ dựng |
| A2 | Bàn phím: thứ tự Tab, tiêu điểm nhìn thấy, thao tác thay thế cho kéo-thả | Mới đọc mã, chưa thử bằng bàn phím thật |
| A3 | Đo tương phản chữ ≥ 4,5:1 bằng công cụ (nhất là chữ xám `--ink-3`, chữ vàng trên nền vàng, kiosk) | Chọn màu theo tiêu chuẩn nhưng chưa đo |
| A4 | Cỡ web 1366×768 | Mới đo 1366×800 |
| A5 | Tablet 10″ thật: độ chói IP54, cỡ chạm khi đeo găng, chữ ở xa | Cần thiết bị ở xưởng |
| A6 | Cảnh báo "dữ liệu cũ hơn 5 phút" trên mọi dashboard | Mới có ở Giám sát khu vực và kiosk (trạng thái Lỗi) |
| A7 | Nội dung dài (tên dài, nhiều dòng, số lớn) | Chưa thử |

**B. Thiết kế chưa vẽ**

| # | Việc | Ghi chú |
|---|---|---|
| B1 | Bộ sáu trạng thái riêng cho mẫu T-B, T-D, T-E | Hiện dùng chung quy tắc với T-A/T-C |
| B2 | Trạng thái kéo-thả và thao tác bàn phím thay thế: lưới KHSX tuần, lịch KHSX tháng, điều độ lệnh, bố trí nhân sự, hoán đổi lịch bảo trì, lưới ghép cặp vấn–bao, trình thiết kế KSCL | Mới vẽ trạng thái tĩnh |
| B3 | Lớp phủ: hộp thoại xác nhận, ngăn kéo chi tiết, chọn ngày, chọn máy | Có bóng đổ theo §2.3; chưa vẽ |
| B4 | Ký điện tử (nhập PIN, xác nhận nhiều bên, tình huống ký thất bại) | Mới vẽ ô chữ ký ở trạng thái Chưa ký / Đã ký |
| B5 | Đăng nhập: sai PIN nhiều lần, khóa tài khoản, hết phiên (web và tablet) | Mới có một trạng thái lỗi ở đăng nhập web |
| B6 | Trạng thái dữ liệu `CONFIRMED`/`LOCKED` và luồng "Yêu cầu điều chỉnh" từ từng màn | Mới có ở Yêu cầu điều chỉnh, Phiếu cấp phát và vài nút bị khóa |
| B7 | Thành phần nền trong bản vẽ (bảng "Thành phần & trạng thái") chưa cập nhật các chip, snackbar, thẻ trạng thái mới | Canvas đợt 1 không có nguồn trong repo (chỉ có liên kết); cần xuất hoặc vẽ lại nếu muốn lưu |
| B8 | Cỡ màn hình hẹp (< 1024 px) cho web quản lý | SDD chưa yêu cầu; cần quyết định có hỗ trợ hay không |
| B9 | Mô hình dữ liệu và chữ cho các biểu mẫu động T-11 theo từng loại kiểm tra thật | Chờ danh mục KSCL (Q10) |

**C. Cần PXVB quyết định / xác nhận**

| # | Việc | Ai |
|---|---|---|
| C1 | Duyệt hướng thiết kế; cung cấp logo, màu, font nếu có | PXVB |
| C2 | Xác nhận các giả định không có trong SDD (danh sách ở `docs/design/README.md`: PIN 6 số, cách xử lý xung đột, quy tắc cấp A/B/C, điều kiện mở ca, các màn web MT/QC/TX/OH/SP); ghi vào `docs/OPEN-QUESTIONS.md` khi duyệt | PXVB, BA |
| C3 | Số liệu minh họa trong bản vẽ phải thay bằng dữ liệu thật khi làm epic tương ứng | Dev, BA |
| C4 | Cập nhật SDD §2.2 (đang ghi Angular) theo quyết định L01 (React) | BA |

**D. Triển khai frontend** (chưa bắt đầu từ bản vẽ này)

| # | Việc | Ghi chú |
|---|---|---|
| D1 | Chọn nơi đặt token dùng chung web/tablet khi dựng `src/web` | Dev lead (đã nêu ở §9) |
| D2 | Dựng thành phần nền React: Button, Field, Chip, Banner, DataTable, Drawer, Stepper, Skeleton, Snackbar | Lấy token và quy tắc từ §2–§4 |
| D3 | Ráp sáu mẫu web, kiosk, ba mẫu tablet có trạng thái từ `docs/design/generator/lib.py` làm tham chiếu | Bản vẽ chỉ là HTML tĩnh, không dùng làm mã sản phẩm |
| D4 | Cổng kiểm tra mỗi màn khi làm xong (§8) | Áp dụng cho từng epic |
