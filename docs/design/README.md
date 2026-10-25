# PXVB – Bản vẽ màn hình & trạng thái

Hệ thiết kế (token, thành phần, mẫu): `docs/DESIGN-SYSTEM.md`. Thư mục này lưu **bản vẽ nguồn** và **trạng thái từng màn hình**.

- Canvas xem được (14 bảng mẫu, đợt 1): https://claude.ai/artifact/RYsmJi9rPFJfsjReCWR4jj
- Canvas xem được (9 màn bổ sung, đợt 2): https://claude.ai/artifact/976ez4LHLR95GAre8a8Bng
- Nguồn đợt 2: `docs/design/screens/*.dc.html` (định dạng Design Component; `canvas.json` là bố cục canvas). Cả hai canvas đều riêng tư cho đến khi chủ sở hữu chia sẻ.

> Số liệu, tên người, mã phiếu trong bản vẽ là **dữ liệu minh họa**. Chưa kiểm tra bằng trình duyệt ở nhiều cỡ màn hình, chưa qua cổng kiểm tra §8 của DESIGN-SYSTEM.md.

## Trạng thái đợt 2 (đề xuất, chưa duyệt)

| Màn hình | Mẫu | File | Trạng thái |
|---|---|---|---|
| T-01 Đăng nhập nhanh | T-A | `Main.dc.html` | Đã vẽ, chờ duyệt |
| T-02 Trang chủ theo vị trí | T-A | `T02-Home.dc.html` | Đã vẽ, chờ duyệt |
| T-60 Mở ca | T-F | `T60-ShiftOpen.dc.html` | Đã vẽ, chờ duyệt |
| T-70 / T-62 Đồng bộ & xung đột | T-A / T-F | `T70-Sync.dc.html` | Đã vẽ, chờ duyệt |
| Web – Đăng nhập | – | `W-Login.dc.html` | Đã vẽ, chờ duyệt |
| PL – Lịch KHSX tháng | lịch (mới) | `W-PlanCalendar.dc.html` | Đã vẽ, chờ duyệt |
| MD – Trình thiết kế biểu mẫu KSCL | mới | `W-KsclDesigner.dc.html` | Đã vẽ, chờ duyệt |
| MD – Ma trận phân quyền | W3 | `W-Permissions.dc.html` | Đã vẽ, chờ duyệt |
| MT – Phiếu cấp phát | W1 + W2 | `W-Voucher.dc.html` | Đã vẽ, chờ duyệt |
| T-10 Danh sách lượt KSCL | T-A | `T10-QcList.dc.html` | Đã vẽ, chờ duyệt (đợt 3) |
| T-20 Soạn hàng NVL | T-C | `T20-MaterialPick.dc.html` | Đã vẽ, chờ duyệt (đợt 3) |
| T-61 Xác nhận lệnh & bố trí | T-F | `T61-OrderConfirm.dc.html` | Đã vẽ, chờ duyệt (đợt 3) |
| T-40 Việc bảo trì hôm nay | T-A | `T40-MaintToday.dc.html` | Đã vẽ, chờ duyệt (đợt 3) |
| CC – Yêu cầu điều chỉnh chờ duyệt | W4 | `W-AdjustRequests.dc.html` | Đã vẽ, chờ duyệt (đợt 3) |
| QC – Xử lý SPKPH | W4 | `W-Spkph.dc.html` | Đã vẽ, chờ duyệt (đợt 3) |
| TX – Block tem | W1 + W2 | `W-StampBlock.dc.html` | Đã vẽ, chờ duyệt (đợt 3) |
| SP – Thẻ kho phụ tùng | W1 | `W-StockCard.dc.html` | Đã vẽ, chờ duyệt (đợt 3) |
| PM – Lịch bảo trì tuần | W3 | `W-MaintSchedule.dc.html` | Đã vẽ, chờ duyệt (đợt 3) |
| T-12 Cảnh báo | T-A | `T12-Alerts.dc.html` | Đã vẽ, chờ duyệt (đợt 4) |
| PR – Dashboard ca | W5 | `W-PrDashboard.dc.html` | Đã vẽ, chờ duyệt (đợt 4) |
| PR – Giám sát khu vực | W5 | `W-PrZone.dc.html` | Đã vẽ, chờ duyệt (đợt 4); có trạng thái dữ liệu cũ |
| PR – Phân tích OEE | W5 | `W-PrOee.dc.html` | Đã vẽ, chờ duyệt (đợt 4) |
| PR – Phiên ca | W1 | `W-PrSessions.dc.html` | Đã vẽ, chờ duyệt (đợt 4) |
| PR – Chốt ca (CC-11) | W4 | `W-ShiftClose.dc.html` | Đã vẽ, chờ duyệt (đợt 4) |

Mỗi màn mới chỉ vẽ **một trạng thái nội dung** (không phải đủ sáu theo §5): các trạng thái tải, trống, lỗi, một phần, không có quyền còn thiếu.

## Đợt 5 – 49 màn còn lại (sinh từ bộ dựng)

Sinh bằng `docs/design/generator/` (`python3 specs.py` ghi vào `docs/design/screens/`). Nội dung từng màn khai báo trong `specs.py`; mẫu W1–W6, K1, T-A…T-F nằm trong `lib.py`. Muốn đổi giao diện chung thì sửa `lib.py`; muốn đổi nội dung một màn thì sửa mục của nó trong `specs.py`.

| Màn hình | File | Trạng thái |
|---|---|---|
| T-06 Đổi mác | `T06-ChangeBrand.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| T-07 Tồn điếu | `T07-CigStock.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| T-21 Nhận NVL | `T21-MaterialReceive.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| T-22 Kiểm đếm và hoàn trả | `T22-CountReturn.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| T-30 Tem xuất dùng | `T30-StampIssue.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| T-31 Tem hư/mất | `T31-StampLost.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| T-32 Tem chốt cuối ca | `T32-StampClose.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| T-41 Thực hiện bảo trì | `T41-MaintExec.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| T-42 Báo sự cố / tiếp nhận / duyệt phiếu | `T42-Incident.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| T-50 ĐBCL | `T50-Qa.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| CC – Tra cứu audit | `W-Audit.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| CC – Trung tâm cảnh báo | `W-AlertCenter.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| CC – Giám sát thiết bị và đồng bộ | `W-DeviceSync.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| CC – Trạng thái dịch vụ | `W-ServiceStatus.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| CC – Công việc nền Quartz | `W-Quartz.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| MD – Danh mục máy | `W-MdCatalog.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| MD – Lưới ghép cặp vấn–bao | `W-MdPairing.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| MD – Trình soạn định mức | `W-MdBom.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| MD – Ma trận ngưỡng | `W-MdThreshold.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| MD – Chất lượng dữ liệu | `W-MdDataQuality.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| PL – Lưới KHSX tuần | `W-PlanWeek.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| PL – Bảng điều độ lệnh | `W-PlanOrders.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| PL – Bố trí nhân sự | `W-PlanStaff.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| PL – Xác nhận lệnh đầu ca | `W-PlanConfirm.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| PL – Theo dõi thực hiện | `W-PlanTrack.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| MT – Giao nhận và chốt tiêu hao ca | `W-MtHandover.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| MT – Báo cáo tiêu hao | `W-MtReport.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| QC – Lịch kiểm tra và tra cứu phiếu | `W-QcSchedule.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| QC – Tỷ lệ lỗi thời gian thực | `W-QcDefectRate.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| QC – B21 tháng | `W-QcB21.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| TX – Đối chiếu tem–bao và chốt | `W-TxReconcile.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| TX – Giao diện kế toán/thuế | `W-TxTax.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| PM – Hoán đổi lịch bảo trì | `W-PmSwap.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| PM – Phiếu công việc/sửa chữa | `W-PmOrders.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| PM – Lịch sử bảo trì theo máy | `W-PmHistory.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| PM – Duyệt sửa chữa | `W-PmApprove.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| PM – Dashboard CMMS | `W-PmCmms.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| OH – Kế hoạch trung/đại tu năm | `W-OhPlan.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| OH – Hồ sơ đại tu | `W-OhDossier.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| OH – Nghiệm thu và đóng hồ sơ | `W-OhAcceptance.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| OH – Tổng hợp vật tư | `W-OhMaterials.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| OH – Tiến độ đại tu | `W-OhProgress.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| SP – Nhập/xuất kho phụ tùng | `W-SpInOut.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| SP – Yêu cầu xuất | `W-SpRequest.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| SP – Tồn tối thiểu | `W-SpMinStock.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| RP – Dashboard điều hành | `W-RpDashboards.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| RP – DB_WALL (màn hình xưởng) | `W-RpKiosk.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| RP – Báo cáo sản lượng ca | `W-RpReports.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |
| RP – Đăng ký nhận báo cáo | `W-RpSubs.dc.html` | Đã vẽ, chờ duyệt (đợt 5) |

## Đợt 6 – màn cảnh báo và thông báo (E04, 25/10/2026)

| Màn hình | File | Thay đổi | Trạng thái |
|---|---|---|---|
| T-12 Cảnh báo (tablet) | `T12-Alerts.dc.html` | Vẽ lại: luồng xử lý 5 bước (tiếp nhận, đang xử lý, chọn nguyên nhân, ghi chú, hoàn tất). Nút "Hoàn tất xử lý" chỉ mở khi đủ hai mục bắt buộc | Đã vẽ, chờ duyệt (đợt 6) |
| T-13 Thông báo (tablet) | `T13-Notifications.dc.html` | Mới: danh sách thông báo, mở cảnh báo, đánh dấu đã đọc | Đã vẽ, chờ duyệt (đợt 6) |
| CC – Trung tâm cảnh báo | `W-AlertCenter.dc.html` | Bổ sung lọc theo ca, máy, ngày; nút Excel; thống kê tần suất; cột leo thang; chi tiết có nguyên nhân và lịch sử xử lý | Đã vẽ, chờ duyệt (đợt 6) |
| CC – Quy tắc cảnh báo | `W-AlertRules.dc.html` | Mới (mẫu W2): người nhận theo bước, cửa sổ khử trùng, leo thang, số lần sửa, ô lý do bắt buộc, lịch sử thay đổi (audit) | Đã vẽ, chờ duyệt (đợt 6). Đã sửa theo L67 (plan D8): không còn "hiệu lực từ" và "lịch sử phiên bản"; ghi chú nêu alert giữ snapshot rule |
| CC – Thông báo trong ứng dụng | `W-Notifications.dc.html` | Mới: danh sách, lọc chưa đọc, đánh dấu đã đọc | Đã vẽ, chờ duyệt (đợt 6) |

- Nút **Thông báo** (có số chưa đọc) đã thêm vào header của mọi màn web (`lib.web_shell`) và thanh trạng thái tablet (`lib.tab_shell`). Thay đổi này làm mọi tệp web và tablet đổi phần header.
- Nhãn mức và trạng thái đã chốt theo L71–L72: Khẩn / Cảnh báo / Thông tin; trạng thái Chưa xác nhận → Đã đóng; "Cấp A/B/C" chỉ dùng cho cấp lỗi QC (`docs/plans/E04-completion.plan.md`, F18).
- Số liệu, tên, mã trong các màn là **dữ liệu minh họa**. Chưa kiểm tra trong trình duyệt (cổng §8 chưa chạy).

## Dựng thử trong trình duyệt (24/10/2026)

Cách làm: chuyển 68 màn tĩnh + 60 màn trạng thái (128 trang) thành HTML thường, mở qua máy chủ cục bộ trong Chrome, quét tự động từng trang rồi xem ảnh chụp vài màn đại diện (W-PlanWeek, T41, W-RpKiosk, W-QcB21, S-W1-partial, S-TC-success).

| Kiểm tra | Kết quả |
|---|---|
| Nội dung tràn khỏi khung 1366×800 / 1280×800 / 1920×1080 | Không có (128/128) |
| Nút tablet cao tối thiểu 56 px | Đạt (128/128) |
| Ô nhập, ô chọn có nhãn gắn đúng | Đạt sau khi sửa 8 trang thiếu `for`/`id` |
| Chip trong lưới bị xuống dòng thành hình 2 dòng | Đã sửa (bo góc nhỏ hơn) |
| Kiosk phân biệt trạng thái chỉ bằng màu | Đã sửa: thêm hình ● ▲ ■ trước nhãn |
| Trạng thái Thành công trên tablet tô sáng sai dòng | Đã sửa |

**Chưa kiểm tra được:** 5 màn viết tay dùng vòng lặp của canvas (`W-PlanCalendar`, `W-Permissions`, `W-PrDashboard`, `W-PrOee`, `W-MaintSchedule`) cần runtime của canvas nên không dựng tĩnh được; mới xem được `W-MaintSchedule` ở bản tĩnh trước đó. Chưa kiểm tra: bàn phím và thứ tự Tab thật, tương phản đo bằng công cụ, cỡ màn hình web 1366×768 (mới đo 1366×800), độ chói và cỡ chạm trên tablet IP54 ở xưởng.

## Sáu trạng thái nội dung (DESIGN-SYSTEM §5)

Sinh bằng `generator/states.py`, mỗi **mẫu** có đủ sáu trạng thái; mọi màn dùng mẫu đó theo cùng quy tắc, chỉ thay danh từ, mã lỗi và quyền.

| Mẫu | File | Đủ 6 trạng thái |
|---|---|---|
| W1 Danh sách + chi tiết | `S-W1-*.dc.html` | Có |
| W2 Biểu mẫu có phiên bản | `S-W2-*.dc.html` | Có |
| W3 Lưới điều độ | `S-W3-*.dc.html` | Có |
| W4 Quy trình duyệt/checklist | `S-W4-*.dc.html` | Có |
| W5 Dashboard | `S-W5-*.dc.html` | Có |
| W6 Báo cáo | `S-W6-*.dc.html` | Có |
| T-A / T-C / T-F | `S-TA-*`, `S-TC-*`, `S-TF-*` | Có |
| K1 Kiosk | `S-K1-*.dc.html` | Có (đang tải, trống, mất kết nối, một phần, ca đã chốt, chưa cấp quyền) |

Hậu tố: `loading`, `empty`, `error`, `partial`, `success`, `noperm`. Mẫu T-B, T-D, T-E chưa có bộ riêng vì chung quy tắc với T-A/T-C (khung giữ nguyên, vùng nội dung thay bằng thẻ trạng thái).

## Giả định cần xác nhận (ghi vào `docs/OPEN-QUESTIONS.md` khi duyệt)

- PIN 6 chữ số và danh sách "người dùng gần đây" trên T-01 (SDD chưa mô tả chi tiết).
- Xung đột đồng bộ (T-62): hai lựa chọn "Dùng số liệu máy chủ" / "Gửi cho lãnh đạo ca xem xét"; không ghi đè ngầm.
- Quy tắc cấp A/B/C trong trình thiết kế KSCL: ngoài biên một lần = B, liên tiếp = A. Ngưỡng/chuẩn chỉ minh họa, chờ Q10.
- Điều kiện mở ca (T-60): đủ nhân sự, lệnh đã xác nhận, kiểm tra máy trước ca, ký mở ca.
- Màn web MT/QC/TX/OH/SP suy từ bảng chức năng, chưa có trong SDD (xem DESIGN-SYSTEM.md §7.2).

## Chưa làm

- Mọi màn trong §7 đã có bản vẽ đề xuất. Còn lại: sáu trạng thái nội dung cho từng màn, kiểm tra trình duyệt, duyệt của PXVB (xem DESIGN-SYSTEM.md §9, §10).
- Sáu trạng thái nội dung cho từng màn; kiểm tra trình duyệt (1366×768, tablet 10″, kiosk 1920×1080), bàn phím/tiêu điểm, tương phản.
- Thương hiệu (logo, màu, font) vẫn chưa có; bản vẽ dùng "PXVB" dạng chữ tạm.
