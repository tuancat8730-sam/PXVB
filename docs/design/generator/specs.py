import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from lib import *  # noqa: E402,F401,F403
import lib  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'screens')


def tl(ctx):
    return '<b style="font-size:22px">' + ctx + '</b><span>Ca 2 · 18/10/2026</span>'


LAB = {'ok': 'Xong', 'warn': 'Giải trình', 'bad': 'Chặn', 'off': 'Chưa làm'}
SPECS = []  # (file, board_title, kind, html)


def add(fname, title, kind, html):
    SPECS.append((fname, title, kind, html))


# ---------------- Tablet ----------------
add('T06-ChangeBrand', 'T-06 Đổi mác', 'tab', t_pick({
    'title': 'T-06 Đổi mác', 'left': tl('VM-04'), 'h1': 'Đổi mác',
    'groups': [('Mác thay thế', ['Mác A', 'Mác B', 'Mác C', 'Mác D', 'Mác E', 'Mác F'], 1),
               ('Lý do đổi mác', ['Theo kế hoạch', 'Hết nguyên liệu', 'Yêu cầu khách', 'Khác'], 0)],
    'side': {'title': 'Xác nhận đổi mác', 'text': 'VM-04 đổi từ Mác A sang Mác B lúc 15:30. Máy sẽ dừng ngắn để thay vật tư. Sản lượng sau thời điểm này tính cho Mác B.',
             'primary': 'Xác nhận đổi mác', 'second': ['Hủy']}}))

add('T07-CigStock', 'T-07 Tồn điếu', 'tab', t_num({
    'title': 'T-07 Tồn điếu', 'left': tl('VM-04'), 'h1': 'Tồn điếu cuối giờ',
    'items': [('Điếu thành phẩm tại máy', 'Lần nhập 14:00 · 4.200 điếu', 'ok', 'Đã nhập'),
              ('Điếu chờ vào bao', 'Chưa nhập giờ 15:00', 'warn', 'Chưa nhập', True),
              ('Điếu loại bỏ', 'Lần nhập 14:00 · 120 điếu', 'ok', 'Đã nhập')],
    'entry': 'Điếu chờ vào bao', 'value': '3.850', 'unit': 'điếu', 'hint': ('ok', 'Trong biên', 'Lần trước 4.100, chênh -250'), 'primary': 'Lưu'}))

add('T21-MaterialReceive', 'T-21 Nhận NVL', 'tab', t_num({
    'title': 'T-21 Nhận NVL', 'left': tl('VM-04'), 'h1': 'Nhận nguyên vật liệu',
    'items': [('Giấy vấn', 'Cấp 120,0 kg', 'ok', 'Đã nhận'),
              ('Sợi thuốc', 'Cấp 850,0 kg', 'warn', 'Chưa nhận', True),
              ('Đầu lọc', 'Cấp 1.200 cái', 'bad', 'Lệch 20')],
    'entry': 'Sợi thuốc – số lượng nhận', 'value': '850,0', 'unit': 'kg', 'hint': ('ok', 'Khớp số cấp', 'Phiếu CP-2610-0142'), 'primary': 'Xác nhận đã nhận'}))

add('T22-CountReturn', 'T-22 Kiểm đếm và hoàn trả', 'tab', t_num({
    'title': 'T-22 Kiểm đếm và hoàn trả', 'left': tl('VM-04'), 'h1': 'Kiểm đếm cuối ca',
    'items': [('Giấy vấn', 'Còn dư tại máy', 'ok', 'Đã đếm'),
              ('Sợi thuốc', 'Còn dư tại máy', 'warn', 'Chưa đếm', True),
              ('Đầu lọc', 'Còn dư tại máy', 'off', 'Chưa đếm')],
    'entry': 'Sợi thuốc còn dư', 'value': '42,5', 'unit': 'kg', 'hint': ('warn', 'Hoàn trả', 'Dự kiến hoàn trả 42,5 kg về kho'), 'primary': 'Lưu và hoàn trả'}))

add('T30-StampIssue', 'T-30 Tem xuất dùng', 'tab', t_num({
    'title': 'T-30 Tem xuất dùng', 'left': tl('VM-04'), 'h1': 'Xuất tem dùng',
    'items': [('Block BL-0142', 'Dải 000001 đến 002000', 'ok', 'Đang dùng', True),
              ('Block BL-0141', 'Dải 002001 đến 004000', 'off', 'Chưa dùng')],
    'entry': 'Số tem xuất dùng', 'value': '200', 'unit': 'tem', 'hint': ('ok', 'Trong block', 'Sau khi xuất còn 617 tem'), 'primary': 'Xuất tem'}))

add('T31-StampLost', 'T-31 Tem hư/mất', 'tab', t_num({
    'title': 'T-31 Tem hư/mất', 'left': tl('VM-04'), 'h1': 'Báo tem hư hoặc mất',
    'items': [('Tem hư', 'Rách, lem, dán sai', 'warn', 'Đang nhập', True), ('Tem mất', 'Không tìm thấy khi kiểm', 'off', 'Chưa nhập')],
    'entry': 'Số tem hư', 'value': '3', 'unit': 'tem', 'hint': ('warn', 'Cần lý do', 'Chọn lý do ở bước sau'), 'primary': 'Tiếp tục'}))

add('T32-StampClose', 'T-32 Tem chốt cuối ca', 'tab', t_steps({
    'title': 'T-32 Tem chốt cuối ca', 'left': tl('VM-04'), 'h1': 'Chốt tem cuối ca', 'labels': LAB,
    'steps': [('ok', 'Đếm tem còn lại', 'Còn 617 tem trong BL-0142'),
              ('ok', 'Đối chiếu tem đã dùng với số bao', 'Đã dùng 200, bao đã đóng 198'),
              ('warn', 'Giải trình chênh lệch 2 tem', 'Nhập lý do hoặc báo tem hư'),
              ('off', 'Ký chốt tem', 'Lãnh đạo ca nhập PIN')],
    'side': {'title': 'Chốt tem ca 2', 'text': 'Còn 1 mục cần giải trình trước khi ký chốt.', 'primary': '-Ký chốt tem', 'second': ['Quay lại']}}))

add('T41-MaintExec', 'T-41 Thực hiện bảo trì', 'tab', t_form({
    'title': 'T-41 Thực hiện bảo trì', 'left': tl('VM-04'), 'h1': 'Bảo trì định kỳ tuần',
    'items': [('Vệ sinh khu vực dao cắt', 'pf', 1), ('Kiểm tra dây curoa', 'pf', 1), ('Độ rơ vòng bi', 'num', ('0,05', 'mm')), ('Mức dầu hộp số', 'pf', 2)],
    'side': {'title': 'Dự kiến kết quả', 'text': '1 mục không đạt. Phiếu sẽ chuyển sang chờ duyệt sửa chữa.', 'primary': 'Hoàn tất phiếu', 'second': ['Chụp ảnh']}}))

add('T42-Incident', 'T-42 Báo sự cố / tiếp nhận / duyệt phiếu', 'tab', t_steps({
    'title': 'T-42 Báo sự cố', 'left': tl('VM-02'), 'h1': 'Phiếu sự cố VM-02', 'labels': {'ok': 'Xong', 'warn': 'Chờ bạn', 'bad': 'Chặn', 'off': 'Chưa đến'},
    'steps': [('ok', 'Báo sự cố', 'Kẹt cơ cấu cấp giấy · 06:40 · Nguyễn Văn A'),
              ('ok', 'Tiếp nhận', 'Hoàng Văn E nhận lúc 06:48'),
              ('warn', 'Duyệt phiếu sửa chữa', 'Cần lãnh đạo ca duyệt vật tư xuất kho'),
              ('off', 'Nghiệm thu', 'Sau khi sửa xong')],
    'side': {'title': 'Duyệt phiếu', 'text': 'Vật tư: dây curoa máy vấn x1. Duyệt để thợ bắt đầu sửa.', 'primary': 'Duyệt phiếu', 'second': ['Từ chối']}}))

add('T50-Qa', 'T-50 ĐBCL', 'tab', t_form({
    'title': 'T-50 ĐBCL', 'left': tl('Khu vực 1'), 'h1': 'Kiểm tra đảm bảo chất lượng',
    'items': [('Ngoại quan bao thuốc', 'pf', 1), ('Độ kín bao', 'pf', 1), ('Trọng lượng bao', 'num', ('19,8', 'g')), ('Vị trí tem', 'pf', 0)],
    'side': {'title': 'Dự kiến cấp', 'text': 'Chưa đủ mục để xác định. Nhập hết các mục.', 'primary': '-Gửi kết quả', 'second': ['Ghi chú']}}))

# ---------------- Web: CC ----------------
add('W-Audit', 'CC – Tra cứu audit', 'web', w_list({
    'nav': 'Danh mục và quản trị', 'crumb': 'Quản trị', 'title': 'Tra cứu audit', 'search': 'Tìm theo người dùng, đối tượng, hành động',
    'tools': ['Thời gian: hôm nay', 'Hành động'], 'tools_r': ['Excel'],
    'cols': [('Thời điểm', ''), ('Người dùng', ''), ('Hành động', ''), ('Đối tượng', ''), ('Kết quả', '')],
    'rows': [['18/10 15:12', 'Lê Văn C', 'Duyệt điều chỉnh', 'm:DC-2610-007', 'ok:Thành công'], ['18/10 14:55', 'Trần Thị B', 'Sửa quyền vai trò', 'Lãnh đạo ca', 'ok:Thành công'],
             ['18/10 14:10', 'Lê Văn C', 'Tạo yêu cầu điều chỉnh', 'm:DC-2610-007', 'ok:Thành công'], ['18/10 13:48', 'Nguyễn Văn A', 'Đăng nhập', 'TB-0412', 'bad:Sai PIN'],
             ['18/10 13:40', 'Phạm Văn F', 'Chốt ca 1', 'Phiên ca 1', 'ok:Thành công']], 'sel': 0,
    'side': {'title': 'Chi tiết bản ghi', 'lines': [('Người dùng', 'Lê Văn C'), ('Thiết bị', 'm:TB-0412'), ('Trước', 'Chờ duyệt'), ('Sau', 'Đã duyệt'), ('Chữ ký', 'ok:Hợp lệ')],
             'note': ('off', 'Bản ghi audit chỉ đọc, không sửa không xóa.'), 'actions': ['Xem đối tượng']}}))

add('W-AlertCenter', 'CC – Trung tâm cảnh báo', 'web', w_list({
    'nav': 'Tổng quan', 'crumb': 'Tổng quan', 'title': 'Trung tâm cảnh báo', 'search': 'Tìm theo rule, nguồn',
    'tools': ['Mức: Tất cả', 'Trạng thái: Tất cả', 'Ca: Ca 2', 'Máy: Tất cả', 'Ngày: 18/10/2026'],
    'tools_r': ['Thống kê tần suất', 'Excel', 'Quy tắc cảnh báo'],
    'cols': [('Thời điểm', ''), ('Mức', ''), ('Quy tắc', ''), ('Nguồn', ''), ('Trạng thái', ''), ('Bậc leo thang', '')],
    'rows': [['15:12', 'bad:Khẩn', 'Khối lượng điếu ngoài biên', 'm:VM-04', 'warn:Chưa xác nhận', 'Bậc 0'],
             ['15:05', 'warn:Cảnh báo', 'Sản lượng chậm', 'm:VM-04', 'warn:Chưa xác nhận', 'Bậc 0'],
             ['15:00', 'warn:Cảnh báo', 'OEE thấp', 'Dây chuyền 2', 'ok:Đã biết', 'Bậc 1'],
             ['14:30', 'info:Thông tin', 'Tồn tem thấp', 'm:BL-0142', 'off:Đã xử lý', 'Không']], 'sel': 0,
    'foot': ('off', 'Thống kê ca 2 (minh họa): Khẩn 1 · Cảnh báo 2 · Thông tin 1. Khối lượng điếu ngoài biên tái diễn 3 lần trong 30 phút.'),
    'side': {'title': 'Cảnh báo đang chọn', 'chip': ('bad', 'Khẩn'),
             'lines': [('Quy tắc', 'Khối lượng điếu ngoài biên'), ('Nguồn', 'm:VM-04'), ('Gửi tới', 'Lãnh đạo ca, QC'),
                       ('Nguyên nhân', 'Chưa chọn'), ('Bậc leo thang', 'Bậc 0, còn 18 phút tới Phó QĐ'), ('Số lần xảy ra', '3')],
             'note': ('off', 'Lịch sử: 15:12 Mở (hệ thống) → 15:14 Nhận (Nguyễn Văn B). Chưa có xử lý.'),
             'actions': ['Xem lượt KSCL', '*Xác nhận đã biết', '-Đã xử lý']}}))

add('W-AlertRules', 'CC – Quy tắc cảnh báo', 'web', w_form({
    'nav': 'Danh mục và quản trị', 'crumb': 'Quản trị', 'title': 'Quy tắc cảnh báo: Khối lượng điếu ngoài biên',
    'fields': [('Mã rule', 'QC_DEFECT_A', True, True), ('Phân hệ', 'QC', True), ('Mức', 'Khẩn (CRITICAL)'), ('Cửa sổ khử trùng (phút)', '10'),
               ('Leo thang sau (phút)', '30'), ('Trạng thái', 'Đang bật'), ('Số lần sửa', '3', True, True), ('Lý do thay đổi (bắt buộc)', '')],
    'table_title': 'Người nhận theo bước',
    'cols': [('Bước', 'n'), ('Vai trò', ''), ('Thời điểm', ''), ('Ghi chú', '')],
    'rows': [['0', 'Trưởng ca (SHIFT_LEADER)', 'Ngay khi phát sinh', 'Mọi người giữ vai trò'],
             ['1', 'Phó quản đốc (DEPUTY_DIRECTOR)', 'Sau 30 phút chưa xác nhận', 'Nhận thêm, không thay thế']],
    'note': ('warn', 'Thay đổi áp dụng cho alert phát sinh sau khi lưu. Mỗi alert giữ snapshot cấu hình rule lúc phát sinh, nên alert đang mở không đổi. Mọi lần lưu bắt buộc nhập lý do và ghi audit trước/sau.'),
    'side_title': 'Lịch sử thay đổi (audit)',
    'history': ['18/10/2026 · Nguyễn Văn A · Cửa sổ khử trùng 30 → 10 phút · Lý do: Giảm trễ phát hiện điếu ngoài biên', '02/10/2026 · Lê Văn C · Thêm bước leo thang · Lý do: Theo đề nghị Quản đốc', '01/10/2026 · Hệ thống · Seed mặc định'],
    'actions': ['Hủy', '*Lưu thay đổi']}))

add('W-Notifications', 'CC – Thông báo trong ứng dụng', 'web', w_list({
    'nav': 'Tổng quan', 'crumb': 'Tổng quan', 'title': 'Thông báo', 'search': 'Tìm theo nội dung',
    'tools': ['Chưa đọc', 'Loại: Tất cả'], 'tools_r': ['Đánh dấu tất cả đã đọc'],
    'cols': [('Thời điểm', ''), ('Loại', ''), ('Nội dung', ''), ('Liên kết', ''), ('Trạng thái', '')],
    'rows': [['15:12', 'Cảnh báo', 'Khối lượng điếu ngoài biên · VM-04', 'm:/alerts/…', 'warn:Chưa đọc'],
             ['15:10', 'Duyệt', 'Yêu cầu duyệt KHSX tuần 43', 'm:/approvals/…', 'warn:Chưa đọc'],
             ['14:58', 'Điều chỉnh', 'DC-2610-007 đã được duyệt', 'm:/adjustments/…', 'ok:Đã đọc']], 'sel': 0,
    'side': {'title': 'Thông báo đang chọn', 'chip': ('warn', 'Chưa đọc'),
             'lines': [('Loại', 'Cảnh báo'), ('Nội dung', 'Khối lượng điếu ngoài biên · VM-04'), ('Thời điểm', '15:12')],
             'actions': ['Mở cảnh báo', '*Đánh dấu đã đọc']}}))

def t12_alerts():
    items = [('Khối lượng điếu ngoài biên liên tiếp', 'Lượt KSCL · 15:12', 'bad', 'Khẩn', True),
             ('Sản lượng chậm so với kế hoạch giờ này', 'Giờ 15:00 · 15:05', 'warn', 'Cảnh báo'),
             ('Tem còn dưới 100 chiếc', 'Lúc 13:20 · đã biết', 'info', 'Thông tin')]
    steps = [('ok', 'Tiếp nhận cảnh báo', 'Nguyễn Văn B · 15:14'),
             ('warn', 'Đang xử lý', 'Kiểm tra lượt KSCL lúc 15:12'),
             ('off', 'Chọn nguyên nhân', 'Bắt buộc, chọn từ danh mục ALERT_CAUSE'),
             ('off', 'Ghi chú xử lý', 'Bắt buộc, không để trống'),
             ('off', 'Hoàn tất xử lý', 'Chỉ mở khi đã có nguyên nhân và ghi chú')]
    labels = {'ok': 'Xong', 'warn': 'Đang làm', 'bad': 'Chặn', 'off': 'Chưa làm'}
    st = ''
    for i, (k, t, s) in enumerate(steps, 1):
        bd = 'border-color:#F2B01E;' if k == 'warn' else ''
        st += ('<div class="row" style="' + bd + 'min-height:84px"><span style="width:44px;height:44px;border-radius:50%;background:var(--offs);display:flex;align-items:center;justify-content:center;font-weight:700;font-size:22px;flex:none">'
               + str(i) + '</span><span style="flex:1"><b>' + t + '</b><small>' + s + '</small></span>' + chip(k, labels[k]) + '</div>')
    side = {'title': 'Xử lý cảnh báo', 'text': 'Hoàn tất xử lý ghi nguyên nhân và ghi chú vào lịch sử. Nút mở khi đủ hai mục bắt buộc.',
            'primary': '-Hoàn tất xử lý', 'second': ['Tiếp nhận', 'Xem lượt KSCL']}
    body = ('<section style="width:340px;display:flex;flex-direction:column;gap:12px">' + t_h1('Cảnh báo') + rows_html(items) + '</section>'
            '<section style="flex:1;display:flex;flex-direction:column;gap:12px;min-width:0">' + t_h1('Khẩn · Khối lượng điếu ngoài biên') + st + '</section>' + tside(side, 340))
    return t_page({'title': 'T-12 Cảnh báo', 'left': tl('VM-04')}, body)

add('T12-Alerts', 'T-12 Cảnh báo', 'tab', t12_alerts())

add('T13-Notifications', 'T-13 Thông báo', 'tab', t_list({
    'title': 'T-13 Thông báo', 'left': tl('VM-04'), 'h1': 'Thông báo',
    'items': [('Cảnh báo mới: Khối lượng điếu ngoài biên', 'VM-04 · 15:12', 'bad', 'Khẩn', True),
              ('Yêu cầu duyệt KHSX tuần 43', 'Từ Lê Văn C · 15:10', 'warn', 'Chờ duyệt'),
              ('Điều chỉnh DC-2610-007 đã được duyệt', '14:58', 'ok', 'Đã đọc')],
    'side': {'title': 'Thông báo đã chọn', 'text': 'Mở để xem nội dung, tiếp nhận và xử lý cảnh báo. Đánh dấu đã đọc không xóa thông báo.',
             'primary': 'Mở cảnh báo', 'second': ['Đánh dấu đã đọc']}}))

add('W-DeviceSync', 'CC – Giám sát thiết bị và đồng bộ', 'web', w_list({
    'nav': 'Danh mục và quản trị', 'crumb': 'Quản trị', 'title': 'Giám sát thiết bị và đồng bộ', 'search': 'Tìm theo mã thiết bị, máy',
    'tools': ['Trạng thái: Tất cả'], 'tools_r': ['Excel'],
    'cols': [('Thiết bị', ''), ('Máy', ''), ('Đồng bộ cuối', ''), ('Chờ gửi', 'n'), ('Xung đột', 'n'), ('Trạng thái', '')],
    'rows': [['m:TB-0412', 'm:VM-04', '15:28', '0', '0', 'ok:Trực tuyến'], ['m:TB-0417', 'm:VM-02', '15:20', '2', '1', 'warn:Có xung đột'], ['m:TB-0423', 'm:VM-05', '15:16', '5', '0', 'off:Offline'],
             ['m:TB-0431', 'm:VM-07', '15:29', '0', '0', 'ok:Trực tuyến']], 'sel': 1,
    'side': {'title': 'TB-0417', 'chip': ('warn', 'Có xung đột'), 'lines': [('Máy', 'm:VM-02'), ('Phiên bản ứng dụng', 'm:1.4.2'), ('Lệnh chờ gửi', '2'), ('Xung đột', '1')],
             'note': ('warn', 'Xung đột không bị ghi đè. Lãnh đạo ca cần xử lý trên tablet.'), 'actions': ['Xem lệnh chờ']}}))

add('W-ServiceStatus', 'CC – Trạng thái dịch vụ', 'web', w_dash({
    'nav': 'Danh mục và quản trị', 'crumb': 'Quản trị', 'title': 'Trạng thái dịch vụ',
    'kpis': [('Dịch vụ hoạt động', '5 / 6', 'warn', 'Có 1 cảnh báo'), ('Độ trễ API', '120 ms', 'ok', 'Bình thường'), ('Lệnh đồng bộ chờ', '7', 'ok', 'Bình thường'), ('Công việc nền lỗi', '0', 'ok', 'Bình thường')],
    'cards_title': 'Dịch vụ',
    'cards': [('API', 'ok', 'Đang chạy', 'Phản hồi 120 ms', 96), ('Cơ sở dữ liệu', 'ok', 'Đang chạy', 'Kết nối 14 / 50', 28), ('Hàng đợi outbox', 'warn', 'Chậm', 'Trễ 90 giây', 60),
              ('Bộ lập lịch', 'ok', 'Đang chạy', '12 công việc', None), ('Kênh thời gian thực', 'ok', 'Đang chạy', '38 kết nối', None), ('Lưu trữ tệp', 'ok', 'Đang chạy', 'Còn trống 72%', 28)],
    'list_title': 'Sự kiện gần đây',
    'list': [('Outbox trễ hơn 60 giây', 'warn', 'Cảnh báo', '15:21'), ('Khởi động lại API', 'ok', 'Đã xong', '09:02'), ('Sao lưu cơ sở dữ liệu', 'ok', 'Đã xong', '02:00')]}))

add('W-Quartz', 'CC – Công việc nền Quartz', 'web', w_list({
    'nav': 'Danh mục và quản trị', 'crumb': 'Quản trị', 'title': 'Công việc nền', 'search': 'Tìm theo tên công việc',
    'tools': ['Trạng thái: Tất cả'], 'tools_r': ['Chạy ngay'],
    'cols': [('Công việc', ''), ('Lịch chạy', ''), ('Lần chạy cuối', ''), ('Lần kế tiếp', ''), ('Trạng thái', '')],
    'rows': [['Xử lý outbox', 'Mỗi 30 giây', '15:30:00', '15:30:30', 'ok:Đang chạy'], ['Tạo lịch kiểm tra KSCL', 'Đầu mỗi ca', '14:00', '22:00', 'ok:Đã xong'],
             ['Tính OEE ca', 'Mỗi giờ', '15:00', '16:00', 'ok:Đã xong'], ['Sao lưu dữ liệu', '02:00 hằng ngày', '02:00', '02:00', 'ok:Đã xong'], ['Gửi báo cáo đăng ký', '06:00 hằng ngày', '06:00', '06:00', 'bad:Lỗi']], 'sel': 4,
    'side': {'title': 'Gửi báo cáo đăng ký', 'chip': ('bad', 'Lỗi'), 'lines': [('Lần lỗi', '06:00 hôm nay'), ('Mã lỗi', 'm:PXVB-RP-0012'), ('Thử lại', '3 / 3')],
             'note': ('bad', 'Không gửi được thư. Kiểm tra cấu hình máy chủ thư rồi chạy lại.'), 'actions': ['Xem nhật ký', '*Chạy lại']}}))

# ---------------- Web: MD ----------------
add('W-MdCatalog', 'MD – Danh mục máy', 'web', w_list({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục', 'title': 'Máy', 'search': 'Tìm theo mã hoặc tên máy',
    'tools': ['Dây chuyền: Tất cả'], 'tools_r': ['Nhập Excel', 'Xuất Excel', '*Thêm máy'],
    'cols': [('Mã', ''), ('Tên', ''), ('Dây chuyền', ''), ('Công suất', 'n'), ('Trạng thái', '')],
    'rows': [['m:VM-01', 'Máy vấn 1', 'Dây chuyền 1', '12.000', 'ok:Hoạt động'], ['m:VM-02', 'Máy vấn 2', 'Dây chuyền 1', '12.000', 'ok:Hoạt động'], ['m:VM-03', 'Máy vấn 3', 'Dây chuyền 2', '10.000', 'ok:Hoạt động'],
             ['m:VM-08', 'Máy vấn 8', 'Dây chuyền 3', '—', 'off:Ngừng sử dụng']], 'sel': 0,
    'side': {'title': 'Máy VM-01', 'lines': [('Mã', 'm:VM-01'), ('Tên', 'Máy vấn 1'), ('Dây chuyền', 'Dây chuyền 1'), ('Công suất (điếu/giờ)', '12.000')],
             'note': ('off', 'Mã máy đã được dùng ở phiên ca nên không đổi được.'), 'actions': ['Hủy', '*Lưu']}}))

add('W-MdPairing', 'MD – Lưới ghép cặp vấn–bao', 'web', w_grid({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục', 'title': 'Ghép cặp máy vấn và máy bao', 'tools': ['Phiên bản: 3'], 'tools_r': ['*Lưu thay đổi'],
    'corner': 'Máy vấn', 'cols': ['BA-01', 'BA-02', 'BA-03', 'BA-04'], 'rows': ['VM-01', 'VM-02', 'VM-03', 'VM-04', 'VM-05'],
    'cells': [['ok:Ghép', '', '', ''], ['ok:Ghép', '', '', ''], ['', 'ok:Ghép', '', ''], ['', '', 'warn:Đổi chưa lưu', ''], ['', '', '', 'ok:Ghép']], 'sel': (3, 2),
    'legend': [('ok', 'Đang ghép'), ('warn', 'Thay đổi chưa lưu'), ('off', 'Trống')],
    'side': {'title': 'VM-04 và BA-03', 'lines': [('Hiệu lực từ', '01/11/2026'), ('Trước đây', 'BA-02')], 'note': ('warn', 'Một máy bao nhận tối đa 2 máy vấn.'), 'actions': ['Hoàn tác']}}))

add('W-MdBom', 'MD – Trình soạn định mức', 'web', w_form({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục / Định mức', 'title': 'Định mức Mác A', 'status': ('off', 'Nháp'),
    'fields': [('Sản phẩm', 'Mác A', True), ('Phiên bản', '4 (nháp)', True), ('Hiệu lực từ', '01/11/2026'), ('Hao hụt cho phép (%)', '1,5', False, True)],
    'table_title': 'Dòng nguyên vật liệu cho 1.000 điếu', 'cols': [('Mã NVL', ''), ('Tên', ''), ('Bản 3', 'n'), ('Bản 4', 'n'), ('Đơn vị', '')],
    'rows': [['m:NVL-0001', 'Giấy vấn', '0,100', 'b:0,100', 'kg'], ['m:NVL-0007', 'Sợi thuốc', '0,700', 'b:0,690', 'kg'], ['m:NVL-0012', 'Đầu lọc', '1.000', 'b:1.000', 'cái']],
    'note': ('warn', 'Sợi thuốc giảm 0,010 kg so với bản 3. Cần duyệt trước khi có hiệu lực.'),
    'actions': ['So sánh với bản cũ', 'Lưu nháp', '*Gửi duyệt'], 'history': ['Bản 3 · 01/08/2026 · Đã duyệt', 'Bản 2 · 01/03/2026 · Đã duyệt', 'Bản 1 · 01/01/2026 · Đã duyệt']}))

add('W-MdThreshold', 'MD – Ma trận ngưỡng', 'web', w_grid({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục', 'title': 'Ma trận ngưỡng', 'tools': ['Phiên bản: 5'], 'tools_r': ['*Lưu thay đổi'],
    'corner': 'Chỉ số', 'first': 200, 'mono_rows': False, 'cols': ['Dây chuyền 1', 'Dây chuyền 2', 'Dây chuyền 3'],
    'rows': ['OEE tối thiểu', 'Sản lượng so với kế hoạch', 'Thời gian dừng một lần', 'Chênh lệch khối lượng điếu'],
    'cells': [['≥ 75%', '≥ 75%', '≥ 70%'], ['≥ 90%', '≥ 90%', '≥ 85%'], ['≤ 30 phút', '≤ 30 phút', '≤ 45 phút'], ['± 0,05 g', '± 0,05 g', '± 0,05 g']], 'sel': (0, 2),
    'side': {'title': 'OEE tối thiểu – DC3', 'lines': [('Hiện tại', '≥ 70%'), ('Hiệu lực', '01/08/2026')], 'note': ('warn', 'Thay đổi áp dụng cho dashboard sau tối đa 60 giây.'), 'actions': ['Hoàn tác']}}))

add('W-MdDataQuality', 'MD – Chất lượng dữ liệu', 'web', w_list({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục', 'title': 'Chất lượng dữ liệu danh mục', 'search': 'Tìm theo danh mục',
    'tools': ['Mức: Tất cả'], 'tools_r': ['Quét lại'],
    'cols': [('Danh mục', ''), ('Vấn đề', ''), ('Số bản ghi', 'n'), ('Mức', ''), ('Trạng thái', '')],
    'rows': [['Máy', 'Thiếu công suất', '1', 'warn:Cần chú ý', 'warn:Chưa xử lý'], ['Định mức', 'Hết hiệu lực chưa có bản mới', '2', 'bad:Nghiêm trọng', 'warn:Chưa xử lý'],
             ['Mã lý do dừng', 'Trùng tên', '1', 'warn:Cần chú ý', 'ok:Đã xử lý'], ['Ngưỡng', 'Không có', '0', 'ok:Tốt', 'ok:Đã xử lý'],
             ['Biểu mẫu KSCL', 'Chưa có bản đang dùng', '1', 'warn:Cần chú ý', 'warn:Chưa xử lý'], ['Luật lỗi A/B/C', 'Chồng lấn khoảng hiệu lực', '2', 'bad:Nghiêm trọng', 'warn:Chưa xử lý'],
             ['Bảo trì chuẩn', 'Dùng phụ tùng không tồn tại', '1', 'bad:Nghiêm trọng', 'warn:Chưa xử lý'], ['Phụ tùng', 'Thiếu NCC mặc định', '3', 'warn:Cần chú ý', 'warn:Chưa xử lý']], 'sel': 5,
    'side': {'title': 'Luật lỗi chồng lấn', 'chip': ('bad', 'Nghiêm trọng'), 'lines': [('Số bản ghi', '2'), ('Ảnh hưởng', 'Một lỗi có thể bị tính hai luật cấp B')], 'actions': ['Xem danh sách', '*Mở lưới luật']}}))

# ---------------- Web: MD (đợt 7, E08) ----------------
add('W-KsclDesigner', 'MD – Trình thiết kế biểu mẫu KSCL', 'web', w_form({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục / Biểu mẫu KSCL', 'title': 'Trình thiết kế B06.01 (nháp v4)', 'status': ('bad', 'Lược đồ có lỗi'),
    'fields': [('Mã biểu mẫu', 'B06.01', True), ('Nhóm', 'B06'), ('Loại', 'Trong ca'), ('Phiên bản', '4 (nháp)', True)],
    'table_title': 'Mục và trường (đổi thứ tự bằng nút Lên / Xuống)', 'cols': [('STT', ''), ('Mục', ''), ('Mã trường', ''), ('Kiểu', ''), ('Bắt buộc', ''), ('Sắp xếp', '')],
    'rows': [['1', 'Thông số', 'khoi_luong', 'NUMBER', 'Có', 'Lên · Xuống'], ['2', 'Thông số', 'do_cung', 'NUMBER', 'Có', 'Lên · Xuống'],
             ['3', 'Cảm quan', 'cam_quan', 'CHECK', 'Không', 'Lên · Xuống'], ['4', 'Cảm quan', 'chon_co', 'SELECT', 'Có', 'Lên · Xuống']],
    'note': ('bad', 'Trường "chon_co" (kiểu SELECT) chưa có danh sách lựa chọn. Chưa lưu được lược đồ.'),
    'actions': ['Xem trước form', 'Thêm trường', '*Lưu nháp'], 'side_title': 'Phiên bản', 'history': ['v3 · 01/10/2026 · Đang dùng', 'v2 · 01/06/2026 · Đã ngừng']}))

add('W-KsclForms', 'MD – Biểu mẫu KSCL: danh sách và phiên bản', 'web', w_list({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục', 'title': 'Biểu mẫu KSCL/ĐBCL', 'search': 'Tìm theo mã hoặc tên biểu mẫu',
    'tools': ['Nhóm: Tất cả', 'Trạng thái: Tất cả'], 'tools_r': ['Nhập Excel', 'Xuất Excel', '*Thêm biểu mẫu'],
    'cols': [('Mã', ''), ('Tên', ''), ('Nhóm', ''), ('Loại', ''), ('Phiên bản', 'n'), ('Trạng thái', '')],
    'rows': [['B06.01', 'Kiểm tra khối lượng điếu', 'B06', 'Trong ca', '3', 'ok:Đang dùng'], ['B06.02', 'Kiểm tra độ cứng bao', 'B06', 'Đầu ca', '2', 'ok:Đang dùng'],
             ['B08.01', 'Kiểm tra tem', 'B08', 'Trong ca', '1', 'warn:Nháp'], ['B08.03', 'Biểu mẫu cũ', 'B08', 'Trong ca', '1', 'off:Đã ngừng']], 'sel': 0,
    'side': {'title': 'B06.01 · phiên bản', 'chip': ('ok', 'Đang dùng'), 'lines': [('Đang dùng', 'v3 (từ 01/10/2026)'), ('Bản nháp', 'v4 (chưa duyệt)'), ('Lịch sử', 'v1 đã ngừng · v2 đã ngừng · v3 đang dùng')],
             'note': ('warn', 'Bản đang dùng không sửa trực tiếp. Muốn đổi, nhân bản thành phiên bản mới.'), 'actions': ['Xem trước', 'Nhân bản', '*Kích hoạt phiên bản']}}))

add('W-DefectRules', 'MD – Lỗi và luật phân cấp A/B/C', 'web', w_grid({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục / Lỗi và luật', 'title': 'Lỗi và luật phân cấp A/B/C', 'tools': ['Nhóm áp dụng: ROD'], 'tools_r': ['*Thêm luật'],
    'corner': 'Lỗi', 'first': 200, 'mono_rows': False, 'cols': ['Cấp A', 'Cấp B', 'Cấp C'],
    'rows': ['Sứt đầu điếu', 'Điếu bị gãy', 'Lem keo'],
    'cells': [['off:Chưa kích hoạt (L75)', 'ok:Ngoài biên 1 lần = B', 'off:Chưa định nghĩa (L75)'],
              ['off:Chưa kích hoạt (L75)', 'warn:Trùng hiệu lực (2 luật)', 'off:Chưa định nghĩa (L75)'],
              ['', 'ok:Ngoài biên 1 lần = B', '']], 'sel': (0, 1),
    'legend': [('ok', 'Đã có luật'), ('warn', 'Trùng hiệu lực'), ('off', 'Chưa định nghĩa')],
    'side': {'title': 'Sứt đầu điếu · cấp B', 'chip': ('ok', 'Đã chốt'), 'lines': [('Cách đo', 'Đếm trong mẫu'), ('Cỡ mẫu', '200 điếu (minh họa)'), ('Ngưỡng', '≥ 1 lần'), ('Hiệu lực', 'từ 01/11/2026')],
             'note': ('warn', 'Cấp A và C chưa được định nghĩa (OPEN-QUESTIONS L75). Màn này không lưu luật cấp A.'), 'actions': ['Hủy', '*Lưu luật']}}))

add('W-MaintTemplate', 'MD – Công việc bảo trì chuẩn', 'web', w_form({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục / Bảo trì chuẩn', 'title': 'Vệ sinh dao cắt', 'status': ('off', 'Nháp'),
    'fields': [('Mã công việc', 'BT-VS-DC', True), ('Loại', 'Định kỳ'), ('Chu kỳ', '7 ngày'), ('Kỹ năng', 'Thợ bảo trì')],
    'table_title': 'Checklist', 'cols': [('STT', ''), ('Nội dung', ''), ('Kiểu kết quả', ''), ('Min', ''), ('Max', '')],
    'rows': [['1', 'Vệ sinh khu vực dao cắt', 'OK / NG', '—', '—'], ['2', 'Đo độ mòn lưỡi', 'Số', '0,00', '0,05'], ['3', 'Kiểm tra dầu bôi trơn', 'OK / NG', '—', '—']],
    'note': ('warn', 'Vật tư dự kiến: Dao cắt DC-01 × 1; Dầu bôi trơn DB-03 × 0,5 lít. Chọn từ danh mục phụ tùng, không gõ tự do.'),
    'actions': ['Hủy', 'Lưu nháp', '*Kích hoạt phiên bản'], 'side_title': 'Lịch sử phiên bản', 'history': ['Bản 1 · Nháp', 'Chưa có bản đang dùng']}))

add('W-PartLinks', 'MD – Liên kết thiết bị–phụ tùng', 'web', w_list({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục', 'title': 'Liên kết thiết bị–phụ tùng', 'search': 'Tìm theo máy hoặc phụ tùng',
    'tools': ['Chế độ: Theo máy', 'Đổi sang theo phụ tùng'], 'tools_r': ['*Thêm liên kết'],
    'cols': [('Máy', ''), ('Cụm', ''), ('Phụ tùng', ''), ('Phạm vi', ''), ('Công việc bảo trì', '')],
    'rows': [['m:VM-01', 'Cụm cấp giấy', 'Dao cắt DC-01', 'Trực tiếp', 'BT-VS-DC'], ['m:VM-02', 'Cụm cấp giấy', 'Dao cắt DC-01', 'Theo dòng máy', 'BT-VS-DC'],
             ['m:VM-01', 'Cụm đóng gói', 'Băng tải BT-12', 'Trực tiếp', '—']], 'sel': 0,
    'side': {'title': 'Dao cắt DC-01 · theo phụ tùng', 'lines': [('Đang gắn ở', '2 máy (VM-01, VM-02)'), ('Mã kho', 'DC-01'), ('Thẻ kho', 'TK-0412')],
             'note': ('warn', 'VM-02 nhận liên kết theo dòng máy (G6): phụ tùng này áp cho mọi máy cùng dòng.'), 'actions': ['Hủy', '*Lưu']}}))

add('W-MdCommonCatalog', 'MD – Danh mục chung (phụ tùng, NCC, lý do, loại tem)', 'web', w_list({
    'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục', 'title': 'Phụ tùng', 'search': 'Tìm theo mã hàng, tên hoặc thẻ kho',
    'tools': ['Nhóm: Tất cả'], 'tools_r': ['Nhập Excel', 'Xuất Excel', '*Thêm phụ tùng'],
    'cols': [('Mã hàng', ''), ('Tên', ''), ('Thẻ kho', ''), ('ĐVT', ''), ('NCC mặc định', ''), ('Trạng thái', '')],
    'rows': [['DC-01', 'Dao cắt', 'TK-0412', 'cái', 'Thép Bắc', 'ok:Đang dùng'], ['DB-03', 'Dầu bôi trơn', 'TK-0530', 'lít', '—', 'warn:Thiếu NCC'],
             ['BT-12', 'Băng tải', 'TK-0877', 'cái', 'Cơ khí Nam', 'off:Ngừng dùng']], 'sel': 0,
    'side': {'title': 'Kiểm soát · DC-01', 'lines': [('Người phụ trách', 'Phó giám đốc'), ('Ngày cập nhật', '18/10/2026 15:20'), ('Phiên bản', '3'), ('Trạng thái', 'Đang dùng')],
             'note': ('off', 'Người phụ trách, phiên bản và trạng thái chỉ đọc. Thay đổi qua luồng duyệt của danh mục (D4).'), 'actions': ['Xem lịch sử', '*Nhân bản']}}))

# ---------------- Web: PL ----------------
add('W-PlanWeek', 'PL – Lưới KHSX tuần', 'web', w_grid({
    'nav': 'Kế hoạch sản xuất', 'crumb': 'Kế hoạch sản xuất', 'title': 'Lưới KHSX tuần 19/10 đến 25/10', 'status': ('warn', 'Chờ duyệt'), 'tools': ['Tuần trước', 'Tuần này', 'Tuần sau'], 'tools_r': ['Excel', '*Gửi duyệt'],
    'corner': 'Máy', 'cols': ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'], 'rows': ['VM-01', 'VM-02', 'VM-03', 'VM-04', 'VM-05'],
    'cells': [['info:Mác A', 'info:Mác A', 'info:Mác B', 'info:Mác B', 'info:Mác A', 'info:Mác A', ''], ['bad:Sửa chữa', 'bad:Sửa chữa', 'info:Mác A', 'info:Mác A', 'info:Mác A', '', ''],
              ['info:Mác B', 'info:Mác B', 'info:Mác B', 'warn:Bảo trì', 'info:Mác B', 'info:Mác B', ''], ['warn:Bảo trì', 'info:Mác A', 'info:Mác A', 'info:Mác A', 'info:Mác A', 'info:Mác A', ''], ['info:Mác C', 'info:Mác C', 'info:Mác C', 'info:Mác C', 'warn:Bảo trì', '', '']],
    'sel': (3, 0), 'legend': [('info', 'Mác sản xuất'), ('warn', 'Bảo trì'), ('bad', 'Sửa chữa'), ('off', 'Trống')],
    'side': {'title': 'VM-04 · T2 19/10', 'lines': [('Ca 1', 'warn:Bảo trì'), ('Ca 2', 'info:Mác A'), ('Ca 3', 'info:Mác A')], 'note': ('warn', 'Ca 1 trùng lịch bảo trì 08:00 đến 10:00.'), 'actions': ['Dời lệnh']}}))

add('W-PlanOrders', 'PL – Bảng điều độ lệnh', 'web', w_grid({
    'nav': 'Kế hoạch sản xuất', 'crumb': 'Kế hoạch sản xuất', 'title': 'Điều độ lệnh sản xuất 19/10', 'tools': ['Ngày: 19/10/2026'], 'tools_r': ['*Tạo lệnh'],
    'corner': 'Lệnh', 'first': 150, 'cols': ['Ca 1', 'Ca 2', 'Ca 3'], 'rows': ['LSX-2610-011', 'LSX-2610-012', 'LSX-2610-014', 'LSX-2610-015'],
    'cells': [['ok:VM-01 · đã xác nhận', '', ''], ['info:VM-03 · nháp', '', ''], ['', 'warn:VM-04 · chờ xác nhận', ''], ['', '', 'info:VM-05 · nháp']], 'sel': (2, 1),
    'legend': [('ok', 'Đã xác nhận'), ('warn', 'Chờ xác nhận'), ('info', 'Nháp')],
    'side': {'title': 'LSX-2610-014', 'chip': ('warn', 'Chờ xác nhận'), 'lines': [('Mác', 'Mác A'), ('Kế hoạch', '12.000 điếu'), ('Máy', 'm:VM-04')], 'note': ('off', 'Xác nhận lệnh sẽ tự sinh phiếu cấp phát NVL.'), 'actions': ['Sửa', '*Xác nhận lệnh']}}))

add('W-PlanStaff', 'PL – Bố trí nhân sự', 'web', w_grid({
    'nav': 'Kế hoạch sản xuất', 'crumb': 'Kế hoạch sản xuất', 'title': 'Bố trí nhân sự tuần 19/10', 'tools': ['Tuần này'], 'tools_r': ['*Lưu bố trí'],
    'corner': 'Nhân sự', 'first': 170, 'mono_rows': False, 'cols': ['T2', 'T3', 'T4', 'T5', 'T6'], 'rows': ['Nguyễn Văn A', 'Trần Thị B', 'Lê Văn C', 'Phạm Văn D'],
    'cells': [['info:VM-04 · Ca 2', 'info:VM-04 · Ca 2', 'info:VM-04 · Ca 2', 'off:Nghỉ', 'info:VM-04 · Ca 2'], ['info:VM-04 · Ca 2', 'info:VM-04 · Ca 2', 'warn:Trùng ca', 'info:VM-04 · Ca 2', 'info:VM-04 · Ca 2'],
              ['info:Lãnh đạo ca 2', 'info:Lãnh đạo ca 2', 'info:Lãnh đạo ca 2', 'info:Lãnh đạo ca 2', 'info:Lãnh đạo ca 2'], ['info:KSCL Ca 2', 'info:KSCL Ca 2', 'info:KSCL Ca 2', 'info:KSCL Ca 2', '']], 'sel': (1, 2),
    'legend': [('info', 'Đã bố trí'), ('warn', 'Xung đột'), ('off', 'Nghỉ')],
    'side': {'title': 'Trần Thị B · T4', 'lines': [('Ca 2', 'm:VM-04'), ('Ca 3', 'm:VM-02')], 'note': ('warn', 'Đang bị xếp hai ca liền nhau. Chọn một ca.'), 'actions': ['Giữ ca 2', 'Giữ ca 3']}}))

add('W-PlanConfirm', 'PL – Xác nhận lệnh đầu ca', 'web', w_flow({
    'nav': 'Kế hoạch sản xuất', 'crumb': 'Kế hoạch sản xuất', 'title': 'LSX-2610-014', 'status': ('warn', 'Chờ xác nhận'), 'h1': 'Xác nhận lệnh đầu ca 2',
    'items': [('ok', 'Đạt', 'Máy sẵn sàng', 'VM-04 không có việc bảo trì trong ca'), ('ok', 'Đạt', 'Định mức hiệu lực', 'Mác A phiên bản 4'), ('warn', 'Giải trình', 'Nhân sự thiếu 1 người', 'Phạm Văn D chưa xác nhận có mặt'),
              ('bad', 'Chặn', 'Nguyên vật liệu chưa cấp', 'CP-2610-0142 còn chờ nhận')],
    'text': 'Phạm Văn D đang trên đường tới, dự kiến có mặt 14:15.', 'reason': 'Chưa xác nhận được: còn 1 mục Chặn.', 'actions': ['Lưu giải trình', '-Xác nhận lệnh'],
    'sign': [('off', 'Chưa ký', 'Lãnh đạo ca'), ('off', 'Chưa ký', 'Quản đốc')], 'side_note': 'Xác nhận lệnh sẽ mở phiên chạy cho máy.'}))

add('W-PlanTrack', 'PL – Theo dõi thực hiện', 'web', w_dash({
    'nav': 'Kế hoạch sản xuất', 'crumb': 'Kế hoạch sản xuất', 'title': 'Theo dõi thực hiện kế hoạch',
    'kpis': [('Hoàn thành tháng', '64%', 'ok', 'Theo tiến độ'), ('Lệnh trễ', '3', 'warn', 'Cần chú ý'), ('Lệnh đang chạy', '9', 'ok', 'Bình thường'), ('Thiếu NVL', '1', 'bad', 'Xử lý ngay')],
    'cards_title': 'Lệnh sản xuất trong ngày',
    'cards': [('LSX-2610-011', 'ok', 'Đúng tiến độ', 'VM-01 · Mác A', 82), ('LSX-2610-012', 'ok', 'Đúng tiến độ', 'VM-03 · Mác B', 71), ('LSX-2610-014', 'warn', 'Chậm', 'VM-04 · Mác A', 48),
              ('LSX-2610-015', 'bad', 'Thiếu NVL', 'VM-05 · Mác C', 12), ('LSX-2610-016', 'off', 'Chưa bắt đầu', 'VM-06 · Mác A', None), ('LSX-2610-017', 'ok', 'Đúng tiến độ', 'VM-07 · Mác B', 66)],
    'list_title': 'Cần xử lý', 'list': [('LSX-2610-015 thiếu sợi thuốc', 'bad', 'Chặn', 'Cần cấp bổ sung'), ('LSX-2610-014 chậm 22%', 'warn', 'Cấp B', 'VM-04'), ('Lịch tuần chờ duyệt', 'warn', 'Chờ duyệt', 'Phòng kế hoạch')]}))

# ---------------- Web: MT ----------------
add('W-MtHandover', 'MT – Giao nhận và chốt tiêu hao ca', 'web', w_flow({
    'nav': 'Nguyên vật liệu', 'crumb': 'Nguyên vật liệu', 'title': 'Chốt tiêu hao ca 1', 'status': ('warn', 'Chờ chốt'), 'h1': 'Giao nhận và chốt tiêu hao',
    'items': [('ok', 'Đạt', 'Mọi phiếu cấp phát đã nhận', '3 / 3 phiếu'), ('ok', 'Đạt', 'Đã kiểm đếm cuối ca', 'Hoàn trả 42,5 kg sợi thuốc'), ('warn', 'Giải trình', 'Hao hụt sợi thuốc cao', '1,9% so với mức cho phép 1,5%'),
              ('off', 'Chưa có dữ liệu', 'Đối chiếu sản lượng thực', 'Chờ chốt ca sản xuất')],
    'text': 'Hao hụt cao do kẹt giấy ở VM-02 làm bỏ một mẻ sợi.', 'reason': 'Cần giải trình hao hụt trước khi chốt.', 'actions': ['Lưu giải trình', '-Chốt tiêu hao'],
    'sign': [('ok', 'Đã ký', 'Người giao: Nguyễn Văn A'), ('off', 'Chưa ký', 'Người nhận: Kho NVL'), ('off', 'Chưa ký', 'Lãnh đạo ca')], 'side_note': 'Cảnh báo hao hụt cao được gửi tới lãnh đạo ca.'}))

add('W-MtReport', 'MT – Báo cáo tiêu hao', 'web', w_report({
    'nav': 'Nguyên vật liệu', 'crumb': 'Nguyên vật liệu', 'title': 'Báo cáo tiêu hao NVL', 'paper_title': 'BÁO CÁO TIÊU HAO NGUYÊN VẬT LIỆU', 'paper_sub': 'Ngày 18/10/2026 · Ca 1',
    'sections': [('Tiêu hao theo vật tư', ([('Vật tư', ''), ('Định mức', 'n'), ('Thực tế', 'n'), ('Chênh', 'n')], [['Giấy vấn', '120,0', '121,2', '+1,0%'], ['Sợi thuốc', '850,0', '866,0', 'warn:+1,9%'], ['Đầu lọc', '1.200', '1.200', '0,0%']])),
                 ('Hoàn trả về kho', 'Sợi thuốc 42,5 kg')],
    'signers': ['Người lập', 'Lãnh đạo ca', 'Quản đốc'], 'rev': 'Revision 2', 'period': 'Ca 1 · 18/10/2026', 'missing': 'Chưa có dữ liệu sản lượng thực của ca vì ca chưa chốt.', 'actions': ['Xuất Excel', '*Xuất PDF']}))

# ---------------- Web: QC ----------------
add('W-QcSchedule', 'QC – Lịch kiểm tra và tra cứu phiếu', 'web', w_list({
    'nav': 'Chất lượng', 'crumb': 'Chất lượng', 'title': 'Lịch kiểm tra và phiếu KSCL', 'search': 'Tìm theo máy, số phiếu',
    'tools': ['Ngày: 18/10/2026', 'Kết quả: Tất cả'], 'tools_r': ['Excel'],
    'cols': [('Giờ', ''), ('Máy', ''), ('Biểu mẫu', ''), ('Người kiểm', ''), ('Kết quả', '')],
    'rows': [['14:45', 'm:VM-04', 'KSCL máy vấn', '—', 'warn:Quá hạn'], ['13:45', 'm:VM-04', 'KSCL máy vấn', 'Phạm Văn D', 'ok:Đạt'], ['12:45', 'm:VM-04', 'KSCL máy vấn', 'Phạm Văn D', 'bad:Không đạt · B'], ['14:45', 'm:VM-03', 'KSCL máy vấn', 'Phạm Văn D', 'ok:Đạt']], 'sel': 2,
    'side': {'title': 'Phiếu 12:45 · VM-04', 'chip': ('bad', 'Không đạt'), 'lines': [('Khối lượng điếu', '0,93 g'), ('Chuẩn', '0,80 đến 0,90'), ('Cấp', 'warn:Cấp B')], 'actions': ['Mở hồ sơ SPKPH']}}))

add('W-QcDefectRate', 'QC – Tỷ lệ lỗi thời gian thực', 'web', w_dash({
    'nav': 'Chất lượng', 'crumb': 'Chất lượng', 'title': 'Tỷ lệ lỗi thời gian thực',
    'kpis': [('Tỷ lệ lỗi ca', '1,8%', 'warn', 'Trên ngưỡng 1,5%'), ('Lượt kiểm đạt', '92%', 'ok', 'Bình thường'), ('Cảnh báo cấp A', '1', 'bad', 'Xử lý ngay'), ('Hồ sơ SPKPH mở', '2', 'warn', 'Cần chú ý')],
    'cards_title': 'Theo máy', 'cards': [('VM-01', 'ok', 'Đạt', 'Lỗi 0,9%', 20), ('VM-02', 'warn', 'Cao', 'Lỗi 2,4%', 60), ('VM-03', 'ok', 'Đạt', 'Lỗi 1,0%', 25), ('VM-04', 'bad', 'Vượt', 'Lỗi 3,1%', 80), ('VM-05', 'off', 'Chưa có dữ liệu', 'Offline', None), ('VM-06', 'ok', 'Đạt', 'Lỗi 1,1%', 28)],
    'list_title': 'Lỗi hay gặp', 'list': [('Khối lượng điếu ngoài biên', 'bad', '14 lần', 'VM-04, VM-02'), ('Rách giấy', 'warn', '6 lần', 'VM-02'), ('Đầu lọc lệch', 'info', '3 lần', 'VM-03')]}))

add('W-QcB21', 'QC – B21 tháng', 'web', w_report({
    'nav': 'Chất lượng', 'crumb': 'Chất lượng', 'title': 'Báo cáo B21 tháng', 'paper_title': 'BÁO CÁO B21', 'paper_sub': 'Tháng 10/2026',
    'sections': [('Kết quả kiểm tra theo dây chuyền', ([('Dây chuyền', ''), ('Số lượt', 'n'), ('Đạt', 'n'), ('Tỷ lệ đạt', 'n')], [['Dây chuyền 1', '420', '392', '93,3%'], ['Dây chuyền 2', '380', '348', '91,6%'], ['Dây chuyền 3', '—', '—', '—']])),
                 ('Sản phẩm không phù hợp', ([('Mã hồ sơ', ''), ('Số lượng', 'n'), ('Xử lý', '')], [['m:SP-2610-001', '1.200', 'Hủy'], ['m:SP-2610-003', '2.400', 'warn:Đang xử lý']]))],
    'signers': ['Người lập', 'QA', 'Quản đốc'], 'rev': 'Revision 1', 'period': 'Tháng 10/2026', 'missing': 'Dây chuyền 3 chưa có dữ liệu KSCL trong tháng.', 'actions': ['Xuất Excel', '*Xuất PDF']}))

# ---------------- Web: TX ----------------
add('W-TxReconcile', 'TX – Đối chiếu tem–bao và chốt', 'web', w_flow({
    'nav': 'Tem', 'crumb': 'Tem', 'title': 'Đối chiếu BL-0140', 'status': ('warn', 'Chờ đối chiếu'), 'h1': 'Đối chiếu tem với bao và chốt block',
    'items': [('ok', 'Đạt', 'Số tem xuất khớp số bao đã đóng', '2.000 / 2.000'), ('ok', 'Đạt', 'Tem hư và mất đã có lý do', '5 tem'), ('warn', 'Giải trình', 'Dải số 004801 đến 004804 chưa dùng', 'Cần xác nhận nơi lưu giữ'),
              ('bad', 'Chặn', 'Chưa có chữ ký kho', 'Cần thủ kho ký trước khi chốt')],
    'text': 'Bốn tem còn nguyên, đang giữ tại tủ kho tem.', 'reason': 'Chưa chốt được: còn 1 mục Chặn.', 'actions': ['Lưu giải trình', '-Chốt block'],
    'sign': [('ok', 'Đã ký', 'Nhân viên tem'), ('off', 'Chưa ký', 'Thủ kho'), ('off', 'Chưa ký', 'Lãnh đạo ca')], 'side_note': 'Block đã chốt không sửa trực tiếp.'}))

add('W-TxTax', 'TX – Giao diện kế toán/thuế', 'web', w_report({
    'nav': 'Tem', 'crumb': 'Tem', 'title': 'Giao diện kế toán và thuế', 'paper_title': 'BẢNG KÊ SỬ DỤNG TEM', 'paper_sub': 'Tháng 10/2026',
    'sections': [('Tổng hợp theo block', ([('Block', ''), ('Nhận', 'n'), ('Đã dùng', 'n'), ('Hư/mất', 'n'), ('Tồn', 'n')], [['m:BL-0139', '2.000', '1.996', '4', '0'], ['m:BL-0140', '2.000', '2.000', '5', '0'], ['m:BL-0142', '2.000', '1.183', '3', '814']])),
                 ('Ghi chú', 'Số liệu chỉ lấy từ các block đã chốt. BL-0142 đang dùng nên chưa tính.')],
    'signers': ['Người lập', 'Kế toán', 'Giám đốc'], 'rev': 'Revision 1', 'period': 'Tháng 10/2026', 'missing': 'BL-0142 chưa chốt nên chưa vào bảng kê.', 'actions': ['Xuất Excel', '*Gửi kế toán']}))

# ---------------- Web: PM ----------------
add('W-PmSwap', 'PM – Hoán đổi lịch bảo trì', 'web', w_grid({
    'nav': 'Bảo trì', 'crumb': 'Bảo trì', 'title': 'Hoán đổi lịch bảo trì', 'tools': ['Tuần 19/10'], 'tools_r': ['*Áp dụng hoán đổi'],
    'corner': 'Máy', 'cols': ['T2', 'T3', 'T4', 'T5', 'T6'], 'rows': ['VM-01', 'VM-03', 'VM-04', 'VM-05'],
    'cells': [['', 'info:Định kỳ', '', '', ''], ['', '', '', 'info:Thay dầu', ''], ['warn:Trùng ca SX', '', 'ok:Đề xuất', '', ''], ['', '', '', '', 'info:Định kỳ']], 'sel': (2, 2),
    'legend': [('warn', 'Xung đột với sản xuất'), ('ok', 'Ngày đề xuất'), ('info', 'Đã có lịch')],
    'side': {'title': 'VM-04: T2 sang T4', 'lines': [('Từ', 'T2 19/10'), ('Sang', 'T4 21/10'), ('Xung đột', 'ok:Không')], 'note': ('ok', 'Ngày mới không trùng ca sản xuất.'), 'actions': ['Hủy', '*Xác nhận']}}))

add('W-PmOrders', 'PM – Phiếu công việc/sửa chữa', 'web', w_list({
    'nav': 'Bảo trì', 'crumb': 'Bảo trì', 'title': 'Phiếu công việc và sửa chữa', 'search': 'Tìm theo số phiếu, máy',
    'tools': ['Loại: Tất cả', 'Trạng thái'], 'tools_r': ['Excel', '*Tạo phiếu'],
    'cols': [('Số phiếu', ''), ('Máy', ''), ('Loại', ''), ('Thợ', ''), ('Trạng thái', '')],
    'rows': [['m:CV-2610-031', 'm:VM-02', 'Sửa chữa', 'Hoàng Văn E', 'bad:Đang sửa'], ['m:CV-2610-030', 'm:VM-04', 'Định kỳ', 'Hoàng Văn E', 'warn:Chờ bắt đầu'], ['m:CV-2610-029', 'm:VM-01', 'Định kỳ', 'Đỗ Văn G', 'ok:Hoàn thành'], ['m:CV-2610-028', 'm:VM-07', 'Sửa chữa', 'Đỗ Văn G', 'ok:Đã đóng']], 'sel': 0,
    'side': {'title': 'CV-2610-031', 'chip': ('bad', 'Đang sửa'), 'lines': [('Máy', 'm:VM-02'), ('Báo lúc', '06:40'), ('Sự cố', 'Kẹt cơ cấu cấp giấy'), ('Vật tư', 'Dây curoa x1')], 'actions': ['Xem lịch sử', '*Cập nhật']}}))

add('W-PmHistory', 'PM – Lịch sử bảo trì theo máy', 'web', w_list({
    'nav': 'Bảo trì', 'crumb': 'Bảo trì', 'title': 'Lịch sử bảo trì VM-02', 'search': 'Tìm trong lịch sử',
    'tools': ['Máy: VM-02', 'Năm 2026'], 'tools_r': ['Excel'],
    'cols': [('Ngày', ''), ('Loại', ''), ('Nội dung', ''), ('Thời gian dừng', 'n'), ('Phiếu', '')],
    'rows': [['18/10/2026', 'Sửa chữa', 'Kẹt cơ cấu cấp giấy', '80 phút', 'm:CV-2610-031'], ['05/10/2026', 'Định kỳ', 'Bảo trì tuần', '90 phút', 'm:CV-2610-019'], ['21/09/2026', 'Sửa chữa', 'Thay vòng bi', '150 phút', 'm:CV-2609-044'], ['07/09/2026', 'Định kỳ', 'Bảo trì tuần', '90 phút', 'm:CV-2609-021']], 'sel': 0,
    'side': {'title': 'Tổng hợp VM-02', 'lines': [('Số lần sửa 2026', '7'), ('Dừng do sự cố', '14 giờ'), ('Khoảng giữa hai sự cố', '28 ngày')], 'note': ('warn', 'Số lần sửa cao hơn trung bình dây chuyền.')}}))

add('W-PmApprove', 'PM – Duyệt sửa chữa', 'web', w_flow({
    'nav': 'Bảo trì', 'crumb': 'Bảo trì', 'title': 'CV-2610-031', 'status': ('warn', 'Chờ duyệt'), 'h1': 'Duyệt phiếu sửa chữa VM-02',
    'items': [('ok', 'Đạt', 'Nguyên nhân đã ghi nhận', 'Dây curoa đứt do mòn'), ('ok', 'Đạt', 'Vật tư có trong kho', 'PT-0021 còn 2 cái'), ('warn', 'Giải trình', 'Chi phí cao hơn dự kiến', 'Thêm công thay vòng bi'), ('bad', 'Chặn', 'Thiếu ảnh hiện trường', 'Cần ít nhất 1 ảnh')],
    'text': 'Phát hiện thêm vòng bi mòn khi tháo dây curoa, nên thay luôn.', 'reason': 'Chưa duyệt được: còn 1 mục Chặn.', 'actions': ['!Từ chối', '-Duyệt'],
    'sign': [('ok', 'Đã ký', 'Thợ sửa'), ('off', 'Chưa ký', 'Trưởng bảo trì'), ('off', 'Chưa ký', 'Quản đốc')], 'side_note': 'Phiếu đã duyệt không sửa trực tiếp.'}))

add('W-PmCmms', 'PM – Dashboard CMMS', 'web', w_dash({
    'nav': 'Bảo trì', 'crumb': 'Bảo trì', 'title': 'Dashboard CMMS',
    'kpis': [('Phiếu mở', '12', 'warn', 'Cần chú ý'), ('Sửa chữa đang chạy', '2', 'bad', 'Xử lý ngay'), ('Bảo trì đúng hạn', '91%', 'ok', 'Đạt'), ('Phụ tùng dưới tối thiểu', '3', 'warn', 'Cần chú ý')],
    'cards_title': 'Máy cần chú ý', 'cards': [('VM-02', 'bad', 'Đang sửa', 'Kẹt cấp giấy · 80 phút', 70), ('VM-04', 'warn', 'Sắp bảo trì', '08:00 ngày mai', 20), ('VM-05', 'off', 'Offline', 'Mất kết nối', None), ('VM-07', 'ok', 'Bình thường', 'Bảo trì xong 13:00', 100), ('VM-01', 'ok', 'Bình thường', 'Định kỳ 22/10', 40), ('VM-03', 'ok', 'Bình thường', 'Định kỳ 23/10', 30)],
    'list_title': 'Phụ tùng dưới mức tối thiểu', 'list': [('Dây curoa máy vấn', 'bad', 'Tồn 2 / 4', 'PT-0021'), ('Vòng bi 6204', 'warn', 'Sát mức', 'PT-0034'), ('Cảm biến quang', 'off', 'Chưa có dữ liệu', 'PT-0063')]}))

# ---------------- Web: OH ----------------
add('W-OhPlan', 'OH – Kế hoạch trung/đại tu năm', 'web', w_grid({
    'nav': 'Bảo trì', 'crumb': 'Bảo trì / Đại tu', 'title': 'Kế hoạch trung và đại tu năm 2027', 'status': ('off', 'Nháp'), 'tools': ['Năm 2027'], 'tools_r': ['*Gửi duyệt'],
    'corner': 'Máy', 'cols': ['Quý 1', 'Quý 2', 'Quý 3', 'Quý 4'], 'rows': ['VM-01', 'VM-02', 'VM-03', 'VM-04', 'VM-05'],
    'cells': [['', 'info:Trung tu', '', ''], ['info:Đại tu', '', '', ''], ['', '', 'info:Trung tu', ''], ['', 'warn:Trùng cao điểm', '', 'info:Đại tu'], ['', '', '', 'info:Trung tu']], 'sel': (3, 1),
    'legend': [('info', 'Có kế hoạch'), ('warn', 'Trùng cao điểm sản xuất')],
    'side': {'title': 'VM-04 · Quý 2', 'lines': [('Loại', 'Trung tu'), ('Thời gian', '14 ngày'), ('Ngân sách', '—')], 'note': ('warn', 'Quý 2 là cao điểm. Đề nghị dời sang quý 3.'), 'actions': ['Dời quý 3']}}))

add('W-OhDossier', 'OH – Hồ sơ đại tu', 'web', w_form({
    'nav': 'Bảo trì', 'crumb': 'Bảo trì / Đại tu', 'title': 'Hồ sơ đại tu VM-02', 'status': ('off', 'Nháp'),
    'fields': [('Máy', 'VM-02', True, True), ('Loại', 'Đại tu', True), ('Bắt đầu', '10/01/2027'), ('Kết thúc dự kiến', '24/01/2027')],
    'table_title': 'Hạng mục', 'cols': [('Hạng mục', ''), ('Phụ trách', ''), ('Dự kiến (ngày)', 'n'), ('Trạng thái', '')],
    'rows': [['Tháo và kiểm tra cụm vấn', 'Hoàng Văn E', '4', 'off:Chưa bắt đầu'], ['Thay vòng bi và dây curoa', 'Đỗ Văn G', '3', 'off:Chưa bắt đầu'], ['Căn chỉnh và chạy thử', 'Hoàng Văn E', '3', 'off:Chưa bắt đầu']],
    'note': ('warn', 'Chưa có dự toán vật tư. Cần bổ sung trước khi gửi duyệt.'), 'actions': ['Lưu nháp', '*Gửi duyệt'], 'history': ['Tạo 18/10/2026 · Hoàng Văn E']}))

add('W-OhAcceptance', 'OH – Nghiệm thu và đóng hồ sơ', 'web', w_flow({
    'nav': 'Bảo trì', 'crumb': 'Bảo trì / Đại tu', 'title': 'Hồ sơ đại tu VM-07', 'status': ('warn', 'Chờ nghiệm thu'), 'h1': 'Nghiệm thu và đóng hồ sơ',
    'items': [('ok', 'Đạt', 'Mọi hạng mục đã hoàn thành', '8 / 8'), ('ok', 'Đạt', 'Chạy thử đạt chỉ tiêu', 'Sản lượng thử 98% công suất'), ('warn', 'Giải trình', 'Vượt tiến độ 2 ngày', 'Nhập lý do'), ('bad', 'Chặn', 'Chưa quyết toán vật tư', 'Còn 1 phiếu xuất chưa đối chiếu')],
    'text': 'Chờ phụ tùng về muộn 2 ngày.', 'reason': 'Chưa đóng được hồ sơ: còn 1 mục Chặn.', 'actions': ['Lưu giải trình', '-Đóng hồ sơ'],
    'sign': [('ok', 'Đã ký', 'Trưởng bảo trì'), ('off', 'Chưa ký', 'Quản đốc'), ('off', 'Chưa ký', 'Đại diện sản xuất')], 'side_note': 'Hồ sơ đã đóng chỉ sửa qua Yêu cầu điều chỉnh.'}))

add('W-OhMaterials', 'OH – Tổng hợp vật tư', 'web', w_report({
    'nav': 'Bảo trì', 'crumb': 'Bảo trì / Đại tu', 'title': 'Tổng hợp vật tư đại tu', 'paper_title': 'TỔNG HỢP VẬT TƯ ĐẠI TU', 'paper_sub': 'Năm 2027 · Quý 1',
    'sections': [('Vật tư theo máy', ([('Máy', ''), ('Vật tư', ''), ('Dự trù', 'n'), ('Đã xuất', 'n'), ('Chênh', 'n')], [['VM-02', 'Vòng bi 6204', '8', '—', '—'], ['VM-02', 'Dây curoa máy vấn', '4', '—', '—'], ['VM-07', 'Dầu hộp số (lít)', '20', '22', '+10%']]))],
    'signers': ['Người lập', 'Trưởng bảo trì', 'Kế toán'], 'rev': 'Revision 1', 'period': 'Quý 1 / 2027', 'missing': 'VM-02 chưa bắt đầu nên cột Đã xuất chưa có dữ liệu.', 'actions': ['Xuất Excel', '*Xuất PDF']}))

add('W-OhProgress', 'OH – Tiến độ đại tu', 'web', w_dash({
    'nav': 'Bảo trì', 'crumb': 'Bảo trì / Đại tu', 'title': 'Tiến độ đại tu',
    'kpis': [('Hồ sơ đang thực hiện', '2', 'ok', 'Bình thường'), ('Trễ tiến độ', '1', 'warn', 'Cần chú ý'), ('Hoàn thành năm', '3 / 10', 'ok', 'Theo kế hoạch'), ('Chờ nghiệm thu', '1', 'warn', 'Cần chú ý')],
    'cards_title': 'Hồ sơ', 'cards': [('VM-07 · Đại tu', 'warn', 'Chờ nghiệm thu', 'Trễ 2 ngày', 100), ('VM-03 · Trung tu', 'ok', 'Đang thực hiện', 'Ngày 6 / 10', 60), ('VM-05 · Trung tu', 'ok', 'Đang thực hiện', 'Ngày 2 / 8', 25), ('VM-02 · Đại tu', 'off', 'Chưa bắt đầu', 'Từ 10/01/2027', None), ('VM-04 · Đại tu', 'off', 'Chưa bắt đầu', 'Quý 4', None), ('VM-01 · Trung tu', 'ok', 'Hoàn thành', 'Đã đóng', 100)],
    'list_title': 'Việc cần làm', 'list': [('Nghiệm thu VM-07', 'warn', 'Chờ', 'Quản đốc'), ('Quyết toán vật tư VM-07', 'bad', 'Chặn', 'Kế toán'), ('Gửi duyệt kế hoạch 2027', 'off', 'Nháp', 'Trưởng bảo trì')]}))

# ---------------- Web: SP ----------------
add('W-SpInOut', 'SP – Nhập/xuất kho phụ tùng', 'web', w_form({
    'nav': 'Phụ tùng', 'crumb': 'Phụ tùng', 'title': 'Phiếu nhập kho', 'status': ('off', 'Nháp'),
    'fields': [('Số phiếu', 'NK-2610-015', True, True), ('Ngày nhập', '18/10/2026'), ('Nhà cung cấp', 'Công ty cung ứng A'), ('Người nhận', 'Thủ kho')],
    'table_title': 'Dòng phụ tùng', 'cols': [('Mã', ''), ('Tên', ''), ('Số lượng', 'n'), ('Đơn vị', ''), ('Tồn sau nhập', 'n')],
    'rows': [['m:PT-0021', 'Dây curoa máy vấn', '4', 'cái', '6'], ['m:PT-0034', 'Vòng bi 6204', '10', 'cái', '21']],
    'note': ('ok', 'Sau khi nhập, PT-0021 vượt mức tối thiểu 4.'), 'actions': ['Lưu nháp', '*Ghi sổ kho'], 'history': ['Tạo 18/10/2026 15:10 · Thủ kho'], 'side_title': 'Lịch sử phiếu'}))

add('W-SpRequest', 'SP – Yêu cầu xuất', 'web', w_flow({
    'nav': 'Phụ tùng', 'crumb': 'Phụ tùng', 'title': 'YC-2610-009', 'status': ('warn', 'Chờ duyệt'), 'h1': 'Yêu cầu xuất phụ tùng cho CV-2610-031',
    'items': [('ok', 'Đạt', 'Có phiếu công việc', 'CV-2610-031 · VM-02'), ('ok', 'Đạt', 'Đủ tồn kho', 'PT-0021 còn 2, cần 1'), ('warn', 'Giải trình', 'Sau xuất dưới mức tối thiểu', 'Tồn còn 1, mức tối thiểu 4'), ('off', 'Chưa có dữ liệu', 'Đơn giá', 'Kế toán chưa cập nhật')],
    'text': 'Đã lập yêu cầu bổ sung PT-0021.', 'reason': '', 'actions': ['!Từ chối', '*Duyệt xuất'],
    'sign': [('ok', 'Đã ký', 'Người yêu cầu'), ('off', 'Chưa ký', 'Trưởng bảo trì'), ('off', 'Chưa ký', 'Thủ kho')], 'side_note': 'Phiếu xuất đã duyệt không sửa trực tiếp.'}))

add('W-SpMinStock', 'SP – Tồn tối thiểu', 'web', w_list({
    'nav': 'Phụ tùng', 'crumb': 'Phụ tùng', 'title': 'Mức tồn tối thiểu', 'search': 'Tìm theo mã hoặc tên',
    'tools': ['Chỉ dưới mức tối thiểu'], 'tools_r': ['Nhập Excel', '*Lưu thay đổi'],
    'cols': [('Mã', ''), ('Tên phụ tùng', ''), ('Tồn', 'n'), ('Tối thiểu', 'n'), ('Đề xuất đặt', 'n'), ('Tình trạng', '')],
    'rows': [['m:PT-0021', 'Dây curoa máy vấn', '2', '4', '4', 'bad:Dưới mức'], ['m:PT-0034', 'Vòng bi 6204', '11', '10', '—', 'warn:Sát mức'], ['m:PT-0042', 'Dao cắt đầu lọc', '25', '8', '—', 'ok:Đủ'], ['m:PT-0063', 'Cảm biến quang', '—', '2', '—', 'off:Chưa có dữ liệu']], 'sel': 0,
    'side': {'title': 'PT-0021', 'chip': ('bad', 'Dưới mức'), 'lines': [('Tồn', '2'), ('Tối thiểu', '4'), ('Thời gian giao', '7 ngày')], 'note': ('warn', 'Mức tối thiểu mới áp dụng từ ngày lưu.'), 'actions': ['*Tạo yêu cầu bổ sung']}}))

# ---------------- Web: RP ----------------
add('W-RpDashboards', 'RP – Dashboard điều hành', 'web', w_dash({
    'nav': 'Báo cáo', 'crumb': 'Báo cáo', 'title': 'Dashboard điều hành',
    'kpis': [('Sản lượng hôm nay', '312.400', 'ok', 'Theo kế hoạch'), ('OEE trung bình', '73,8%', 'warn', 'Dưới ngưỡng'), ('Tỷ lệ lỗi', '1,8%', 'warn', 'Trên ngưỡng'), ('Máy dừng', '2', 'bad', 'Xử lý ngay')],
    'cards_title': 'Các dashboard', 'cards': [('Điều hành sản xuất', 'ok', 'Mới nhất', 'Cập nhật 15:30', None), ('Chất lượng', 'ok', 'Mới nhất', 'Cập nhật 15:30', None), ('Bảo trì', 'ok', 'Mới nhất', 'Cập nhật 15:29', None),
              ('Nguyên vật liệu', 'warn', 'Dữ liệu cũ', 'Cập nhật 15:21', None), ('Tem', 'ok', 'Mới nhất', 'Cập nhật 15:28', None), ('Phụ tùng', 'ok', 'Mới nhất', 'Cập nhật 15:10', None),
              ('Đại tu', 'ok', 'Mới nhất', 'Cập nhật 14:00', None), ('Nhân sự ca', 'ok', 'Mới nhất', 'Cập nhật 15:30', None), ('Màn hình xưởng', 'ok', 'Đang chiếu', '5 trang xoay vòng', None)],
    'list_title': 'Đang chú ý', 'list': [('VM-02 dừng 50 phút', 'bad', 'Đang dừng', 'Kẹt cấp giấy'), ('Dữ liệu NVL chậm 9 phút', 'warn', 'Dữ liệu cũ', 'Nguyên vật liệu'), ('Tỷ lệ lỗi DC1 cao', 'warn', 'Cấp B', 'Chất lượng')]}))

add('W-RpKiosk', 'RP – DB_WALL (màn hình xưởng)', 'kiosk', w_kiosk({
    'title': 'Dây chuyền 1', 'sub': 'Ca 2 · 18/10/2026',
    'cards': [('Sản lượng so với kế hoạch', '78%', '#F5C451', 'Chậm 22%'), ('OEE', '71,4%', '#F5C451', 'Dưới ngưỡng 75%'), ('Máy đang chạy', '12/14', '#4CC38A', 'Bình thường'), ('Máy đang dừng', '2', '#FF6B5E', 'VM-02, VM-09')],
    'foot': 'Trang 1 / 5 · chuyển trang sau 20 giây'}))

add('W-RpReports', 'RP – Báo cáo sản lượng ca', 'web', w_report({
    'nav': 'Báo cáo', 'crumb': 'Báo cáo', 'title': 'Báo cáo sản lượng ca', 'paper_title': 'BÁO CÁO SẢN LƯỢNG CA', 'paper_sub': 'Ca 1 · 18/10/2026',
    'sections': [('Sản lượng theo máy', ([('Máy', ''), ('Mác', ''), ('Kế hoạch', 'n'), ('Thực tế', 'n'), ('Đạt', 'n')], [['VM-01', 'Mác A', '10.000', '9.480', '95%'], ['VM-02', 'Mác A', '10.000', '2.480', 'bad:25%'], ['VM-03', 'Mác B', '10.000', '10.080', '101%']])),
                 ('Chỉ số', ([('Chỉ số', ''), ('Giá trị', 'n')], [['OEE', '71,4%'], ['Tỷ lệ lỗi', '—'], ['Tiêu hao NVL', '—']]))],
    'signers': ['Công nhân vận hành', 'Lãnh đạo ca', 'Quản đốc'], 'rev': 'Revision 1', 'period': 'Ca 1 · 18/10/2026', 'missing': 'Tỷ lệ lỗi và tiêu hao NVL chưa có dữ liệu vì QC và MT chưa chốt ca.', 'actions': ['Xuất Excel', '*Xuất PDF']}))

add('W-RpSubs', 'RP – Đăng ký nhận báo cáo', 'web', w_list({
    'nav': 'Báo cáo', 'crumb': 'Báo cáo', 'title': 'Đăng ký nhận báo cáo', 'search': 'Tìm theo báo cáo, người nhận',
    'tools': ['Báo cáo: Tất cả'], 'tools_r': ['*Thêm đăng ký'],
    'cols': [('Báo cáo', ''), ('Người nhận', ''), ('Lịch gửi', ''), ('Định dạng', ''), ('Trạng thái', '')],
    'rows': [['Sản lượng ca', 'Quản đốc', 'Sau chốt ca', 'PDF', 'ok:Đang gửi'], ['B21 tháng', 'QA', 'Ngày 1 hằng tháng', 'Excel', 'ok:Đang gửi'], ['Tiêu hao NVL', 'Kế toán', '06:00 hằng ngày', 'Excel', 'bad:Lỗi gửi'], ['OEE tuần', 'Giám đốc', 'Thứ hai 07:00', 'PDF', 'off:Tạm dừng']], 'sel': 2,
    'side': {'title': 'Tiêu hao NVL', 'chip': ('bad', 'Lỗi gửi'), 'lines': [('Người nhận', 'Kế toán'), ('Lần gửi cuối', '06:00 · thất bại'), ('Mã lỗi', 'm:PXVB-RP-0012')], 'note': ('bad', 'Không gửi được thư. Kiểm tra địa chỉ người nhận.'), 'actions': ['Tạm dừng', '*Gửi lại']}}))


# ---------------- Ghi file + canvas ----------------
def main():
    cj_path = os.path.join(OUT, 'canvas.json')
    with open(cj_path, encoding='utf-8') as f:
        cj = json.load(f)
    tab = [s for s in SPECS if s[2] == 'tab']
    web = [s for s in SPECS if s[2] == 'web']
    kiosk = [s for s in SPECS if s[2] == 'kiosk']
    names = []
    for fname, title, kind, html in SPECS:
        full = fname + '.dc.html'
        if kind == 'web':
            doc = page(title, 1366, 800, lib.WEB_CSS, html)
        elif kind == 'kiosk':
            doc = page(title, 1920, 1080, lib.TOKENS, html)
        else:
            doc = html
        with open(os.path.join(OUT, full), 'w', encoding='utf-8') as f:
            f.write(doc)
        names.append(full)
    for i, (fname, title, kind, _) in enumerate(tab):
        cj['boards'][fname + '.dc.html'] = {'x': (i % 5) * 1360, 'y': 6100 + (i // 5) * 900, 'w': 1280, 'h': 800, 'title': title}
    for i, (fname, title, kind, _) in enumerate(web):
        cj['boards'][fname + '.dc.html'] = {'x': (i % 5) * 1446, 'y': 8300 + (i // 5) * 900, 'w': 1366, 'h': 800, 'title': title}
    ky = 8300 + ((len(web) - 1) // 5 + 1) * 900 + 300
    for fname, title, kind, _ in kiosk:
        cj['boards'][fname + '.dc.html'] = {'x': 0, 'y': ky, 'w': 1920, 'h': 1080, 'title': title}
    for n in names:
        if n not in cj['order']:
            cj['order'].append(n)
    cj['notes']['t6'] = {'x': 0, 'y': 5800, 'text': 'Tablet – đợt 5 (hoàn tất)', 'kind': 'title1', 'maxW': 6720}
    cj['notes']['t7'] = {'x': 0, 'y': 8000, 'text': 'Web – đợt 5 (hoàn tất)', 'kind': 'title1', 'maxW': 7150}
    cj['notes']['t8'] = {'x': 0, 'y': ky - 300, 'text': 'Màn hình xưởng (kiosk)', 'kind': 'title1', 'maxW': 1920}
    with open(cj_path, 'w', encoding='utf-8') as f:
        json.dump(cj, f, ensure_ascii=False)
    print(len(names), 'files')
    print(json.dumps(['project/' + n for n in names], ensure_ascii=False))


main()
