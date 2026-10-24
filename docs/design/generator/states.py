"""Sáu trạng thái nội dung (DESIGN-SYSTEM §5) cho từng mẫu: tải, trống, lỗi, một phần, thành công, không có quyền."""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lib  # noqa: E402
from lib import chip, btn, esc, table, note, web_shell, tab_shell, page, t_h1  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'screens')
STATES = ['loading', 'empty', 'error', 'partial', 'success', 'noperm']
STATE_VN = {'loading': 'Đang tải', 'empty': 'Trống', 'error': 'Lỗi', 'partial': 'Một phần', 'success': 'Thành công', 'noperm': 'Không có quyền'}
SK_CSS = '.sk{background:#E6E9ED;border-radius:6px}.sk.d{background:#223244}'
LOCK = ('<svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#4A5666" stroke-width="2" aria-hidden="true">'
        '<rect x="5" y="11" width="14" height="9" rx="2"></rect><path d="M8 11V8a4 4 0 0 1 8 0v3"></path></svg>')
CHECK = ('<svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#1E7A46" stroke-width="2.5" aria-hidden="true">'
         '<circle cx="12" cy="12" r="10"></circle><path d="M7 12.5l3.5 3.5L17 9"></path></svg>')
GLYPH = {
    'empty': '<span aria-hidden="true" style="width:44px;height:44px;border-radius:50%;border:4px solid #B9C2CD;display:block"></span>',
    'error': '<span aria-hidden="true" style="width:44px;height:44px;border-radius:6px;background:#B3261E;display:block"></span>',
    'noperm': LOCK, 'success': CHECK,
}


def sk(w, h, dark=False, extra=''):
    return '<div class="sk' + (' d' if dark else '') + '" style="width:' + str(w) + ';height:' + str(h) + 'px;' + extra + '"></div>'


def center(state, title, text, actions, code=None, big=False):
    cd = '<span class="mono" style="font-size:' + ('18' if big else '12') + 'px;color:var(--ink3)">' + esc(code) + '</span>' if code else ''
    acts = ''.join(actions)
    return ('<div style="flex:1;display:flex;align-items:center;justify-content:center;padding:24px"><div class="card" style="max-width:' + ('760' if big else '520') + 'px;padding:' + ('40' if big else '32') + 'px;text-align:center;display:flex;flex-direction:column;gap:' + ('16' if big else '12') + 'px;align-items:center;'
            + ('border-width:2px;border-radius:12px;' if big else '') + '">' + GLYPH[state]
            + '<h2 style="margin:0;font-size:' + ('32' if big else '18') + 'px;line-height:' + ('40' if big else '24') + 'px">' + esc(title) + '</h2>'
            + '<p style="margin:0;color:var(--ink2);font-size:' + ('22' if big else '14') + 'px;line-height:' + ('30' if big else '20') + 'px">' + esc(text) + '</p>' + cd
            + '<div style="display:flex;gap:' + ('12' if big else '8') + 'px;flex-wrap:wrap;justify-content:center;margin-top:8px">' + acts + '</div></div></div>')


# ------------------------- Web -------------------------
WEB = {
    'list': {'code': 'W1', 'name': 'Danh sách + chi tiết', 'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục', 'title': 'Máy', 'noun': 'máy', 'perm': 'md.machine.view', 'err': 'PXVB-MD-0500',
             'why': 'Chưa có máy nào được khai báo, hoặc bộ lọc đang quá hẹp.', 'acts': ['Xóa bộ lọc', '*Thêm máy'], 'ok': 'Đã lưu máy VM-04.',
             'cols': [('Mã', ''), ('Tên', ''), ('Dây chuyền', ''), ('Công suất', 'n'), ('Trạng thái', '')],
             'rows': [['m:VM-01', 'Máy vấn 1', 'Dây chuyền 1', '12.000', 'ok:Hoạt động'], ['m:VM-02', 'Máy vấn 2', 'Dây chuyền 1', '12.000', 'ok:Hoạt động'], ['m:VM-03', 'Máy vấn 3', 'Dây chuyền 2', '10.000', 'ok:Hoạt động']],
             'missing': 'Công suất của VM-02 và VM-03 chưa có dữ liệu.'},
    'form': {'code': 'W2', 'name': 'Biểu mẫu có phiên bản', 'nav': 'Danh mục và quản trị', 'crumb': 'Danh mục / Định mức', 'title': 'Định mức Mác A', 'noun': 'định mức', 'perm': 'md.bom.update', 'err': 'PXVB-MD-0500',
             'why': 'Mác này chưa có định mức. Cần tạo phiên bản đầu tiên.', 'acts': ['*Tạo phiên bản 1'], 'ok': 'Đã gửi định mức phiên bản 4 để duyệt.',
             'cols': [('Mã NVL', ''), ('Tên', ''), ('Bản 3', 'n'), ('Bản 4', 'n')], 'rows': [['m:NVL-0001', 'Giấy vấn', '0,100', 'b:0,100'], ['m:NVL-0007', 'Sợi thuốc', '0,700', 'b:0,690'], ['m:NVL-0012', 'Đầu lọc', '1.000', 'b:1.000']],
             'missing': 'Hao hụt cho phép chưa có dữ liệu.'},
    'grid': {'code': 'W3', 'name': 'Lưới điều độ', 'nav': 'Kế hoạch sản xuất', 'crumb': 'Kế hoạch sản xuất', 'title': 'Lưới KHSX tuần', 'noun': 'kế hoạch tuần', 'perm': 'pl.plan.view', 'err': 'PXVB-PL-0500',
             'why': 'Tuần này chưa có kế hoạch. Có thể sao chép từ tuần trước.', 'acts': ['Sao chép tuần trước', '*Tạo kế hoạch'], 'ok': 'Đã lưu bố trí. Có 1 xung đột đã được tự giải quyết.',
             'cols': [('Máy', ''), ('T2', ''), ('T3', ''), ('T4', '')], 'rows': [['m:VM-01', 'info:Mác A', 'info:Mác A', 'info:Mác B'], ['m:VM-02', 'bad:Sửa chữa', 'bad:Sửa chữa', 'info:Mác A'], ['m:VM-03', 'info:Mác B', 'info:Mác B', 'warn:Bảo trì']],
             'missing': 'Ngày T5 trở đi chưa có kế hoạch.'},
    'flow': {'code': 'W4', 'name': 'Quy trình duyệt/checklist', 'nav': 'Sản xuất', 'crumb': 'Sản xuất / Phiên ca', 'title': 'Chốt ca 1', 'noun': 'mục kiểm tra', 'perm': 'pr.shift.close', 'err': 'PXVB-PR-0500',
             'why': 'Ca này chưa có mục kiểm tra nào. Hệ thống sẽ tạo khi phiên ca bắt đầu.', 'acts': ['Quay lại phiên ca'], 'ok': 'Đã chốt ca 1 và ký. Báo cáo ca đang được tạo.',
             'cols': [('Mục kiểm tra', ''), ('Kết quả', ''), ('Người', '')], 'rows': [['Sản lượng ghi đủ', 'ok:Đạt', 'Nguyễn Văn A'], ['Dừng máy có lý do', 'ok:Đạt', 'Nguyễn Văn A'], ['Phiếu cấp phát đã chốt', 'warn:Giải trình', 'Lê Văn C']],
             'missing': 'Đối chiếu tem chưa có dữ liệu vì phân hệ tem chưa gửi.'},
    'dash': {'code': 'W5', 'name': 'Dashboard', 'nav': 'Sản xuất', 'crumb': 'Sản xuất', 'title': 'Dashboard ca', 'noun': 'dữ liệu ca', 'perm': 'pr.dashboard.view', 'err': 'PXVB-PR-0500',
             'why': 'Chưa có ca nào đang mở nên chưa có số liệu để hiển thị.', 'acts': ['Xem ca gần nhất'], 'ok': 'Đã làm mới số liệu lúc 15:30.',
             'cols': [('Máy', ''), ('Trạng thái', ''), ('Sản lượng', 'n'), ('Đạt kế hoạch', 'n')], 'rows': [['m:VM-01', 'ok:Đang chạy', '9.480', '95%'], ['m:VM-02', 'bad:Đang dừng', '2.480', '25%'], ['m:VM-03', 'ok:Đang chạy', '10.080', '101%']],
             'missing': 'Tỷ lệ lỗi và tiêu hao NVL chưa có dữ liệu.'},
    'report': {'code': 'W6', 'name': 'Báo cáo', 'nav': 'Báo cáo', 'crumb': 'Báo cáo', 'title': 'Báo cáo sản lượng ca', 'noun': 'báo cáo', 'perm': 'rp.report.view', 'err': 'PXVB-RP-0500',
               'why': 'Kỳ này chưa có dữ liệu để lập báo cáo. Chọn kỳ khác.', 'acts': ['Chọn kỳ khác'], 'ok': 'Đã tạo báo cáo revision 2. Bản PDF sẵn sàng tải.',
               'cols': [('Máy', ''), ('Kế hoạch', 'n'), ('Thực tế', 'n'), ('Đạt', 'n')], 'rows': [['m:VM-01', '10.000', '9.480', '95%'], ['m:VM-02', '10.000', '2.480', '25%'], ['m:VM-03', '10.000', '10.080', '101%']],
               'missing': 'Tỷ lệ lỗi và tiêu hao NVL chưa có dữ liệu vì QC và MT chưa chốt ca.'},
}


def web_skeleton(hint):
    row = sk('100%', 16, extra='margin:12px 12px;width:calc(100% - 24px)')
    rows = ''.join('<div style="border-bottom:1px solid var(--line)">' + row + '</div>' for _ in range(8))
    tools = '<div style="display:flex;gap:8px">' + sk(300, 36) + sk(110, 36) + sk(110, 36) + '</div>'
    if hint == 'dash':
        return ('<div style="display:flex;gap:12px">' + ''.join('<div class="kpi">' + sk(120, 12) + '<div style="height:8px"></div>' + sk(90, 28) + '</div>' for _ in range(4)) + '</div>'
                '<div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px">' + ''.join('<div class="card" style="padding:12px">' + sk('60%', 14) + '<div style="height:8px"></div>' + sk('90%', 10) + '</div>' for _ in range(6)) + '</div>')
    if hint == 'grid':
        return tools + '<div class="card" style="padding:12px;display:grid;grid-template-columns:90px repeat(6,minmax(0,1fr));gap:8px">' + ''.join(sk('100%', 36) for _ in range(42)) + '</div>'
    if hint == 'report':
        return ('<div style="display:flex;justify-content:center;background:#E6E9ED;padding:16px;flex:1"><div style="width:600px;background:#fff;padding:28px;display:flex;flex-direction:column;gap:12px">'
                + sk('50%', 20, extra='align-self:center') + ''.join(sk('100%', 14) for _ in range(9)) + '</div></div>')
    if hint in ('form', 'flow'):
        return '<div class="card" style="padding:20px;display:flex;flex-direction:column;gap:14px">' + ''.join(sk('100%', 40) for _ in range(6)) + '</div>'
    return tools + '<div class="card" style="overflow:hidden">' + rows + '</div>'


def web_body(hint, state):
    e = WEB[hint]
    if state == 'loading':
        cancel = btn('Hủy') if hint == 'report' else ''
        head = ('<div style="display:flex;gap:12px;align-items:center">' + chip('info', 'Đang tải dữ liệu')
                + '<span style="color:var(--ink2)">Giữ nguyên khung, số liệu sẽ hiện khi tải xong.</span><span style="flex:1"></span>' + cancel + '</div>')
        return '<main style="flex:1;padding:16px 24px;display:flex;flex-direction:column;gap:12px;min-height:0;overflow:hidden">' + head + web_skeleton(hint) + '</main>'
    if state == 'empty':
        return center('empty', 'Chưa có ' + e['noun'], e['why'], [btn(t) for t in e['acts']])
    if state == 'error':
        banner = '<div role="alert" style="background:var(--bads);color:var(--bad);padding:10px 24px;border-bottom:1px solid var(--bad);font-weight:600">Không tải được ' + esc(e['noun']) + '.</div>'
        return banner + center('error', 'Không tải được dữ liệu', 'Máy chủ không phản hồi. Dữ liệu bạn đã nhập không bị mất. Thử lại sau ít phút.', [btn('*Thử lại'), btn('Xem hướng dẫn')], e['err'])
    if state == 'noperm':
        return center('noperm', 'Bạn chưa có quyền xem ' + e['noun'], 'Tài khoản của bạn chưa được cấp quyền này. Gửi yêu cầu để quản trị vai trò xem xét.',
                      [btn('*Gửi yêu cầu cấp quyền'), btn('Về trang chủ')], 'Quyền cần có: ' + e['perm'])
    part = state == 'partial'
    vals = [('Đạt kế hoạch', '78%', 'ok', 'Bình thường'), ('Hiệu suất', '71,4%', 'warn', 'Cần chú ý'),
            ('Tỷ lệ lỗi', '—' if part else '1,8%', 'off' if part else 'ok', 'Chưa có dữ liệu' if part else 'Bình thường'),
            ('Tiêu hao NVL', '—' if part else '2,1%', 'off' if part else 'ok', 'Chưa có dữ liệu' if part else 'Bình thường')]
    kp = '<div style="display:flex;gap:12px">' + ''.join('<div class="kpi"><small>' + esc(l) + '</small><b>' + esc(v) + '</b>' + chip(k, t) + '</div>' for l, v, k, t in vals) + '</div>'
    rows = [list(r) for r in e['rows']]
    if part:
        for r in rows[1:]:
            for j in range(len(r) - 2, len(r)):
                r[j] = 'off:Chưa có dữ liệu' if j == len(r) - 1 else '—'
    tb = '<div class="card" style="overflow:hidden">' + table(e['cols'], rows, 0 if state == 'success' else None) + '</div>'
    if part:
        top = note('warn', e['missing'] + ' Phần còn lại vẫn hiển thị, ô thiếu ghi "Chưa có dữ liệu", không điền 0.')
        return '<main style="flex:1;padding:16px 24px;display:flex;flex-direction:column;gap:12px;min-height:0">' + top + kp + tb + '</main>'
    top = ('<div role="status" style="background:var(--oks);color:var(--ok);padding:10px 24px;border-bottom:1px solid var(--ok);font-weight:600;display:flex;gap:10px;align-items:center">'
           + chip('ok', 'Thành công') + esc(e['ok']) + '</div>')
    snack = ('<div style="position:absolute;left:24px;bottom:24px;background:#16202B;color:#fff;border-radius:8px;padding:10px 16px;display:flex;gap:16px;align-items:center;box-shadow:0 4px 16px rgba(0,0,0,.25)">'
             '<span>Đã lưu thay đổi.</span><button style="background:none;border:0;color:#9CC4F0;font-weight:700;text-decoration:underline">Hoàn tác (10 giây)</button></div>')
    return (top + '<main style="flex:1;padding:16px 24px;display:flex;flex-direction:column;gap:12px;min-height:0;position:relative">' + kp + tb
            + '<span style="color:var(--ink2)">Bước kế tiếp: kiểm tra kết quả hoặc quay lại danh sách.</span>' + snack + '</main>')


def web_state_page(hint, state):
    e = WEB[hint]
    body = web_shell(e['nav'], e['crumb'], e['title'], web_body(hint, state), updated='Cập nhật lúc 15:21' if state == 'error' else 'Cập nhật lúc 15:30')
    return page('Trạng thái ' + STATE_VN[state] + ' – ' + e['code'], 1366, 800, lib.WEB_CSS + SK_CSS, body)


# ------------------------- Tablet -------------------------
TAB = {
    'list': {'code': 'T-A', 'name': 'Danh sách + ô lớn', 'h1': 'Lượt kiểm tra chất lượng', 'noun': 'lượt kiểm tra', 'perm': 'qc.check.perform', 'err': 'PXVB-QC-0500',
             'why': 'Ca này chưa có lượt kiểm tra nào được tạo. Lượt đầu tiên sẽ xuất hiện khi ca bắt đầu.', 'ok': 'Đã lưu lượt kiểm tra 14:45.', 'next': 'Lượt kế tiếp lúc 15:45.', 'done': 0, 'done_sub': 'Biểu mẫu KSCL máy vấn · vừa lưu',
             'rows': [('14:45', 'Biểu mẫu KSCL máy vấn', 'ok', 'Đạt'), ('15:45', 'Biểu mẫu KSCL máy vấn', 'off', 'Sắp đến'), ('16:45', 'Biểu mẫu KSCL máy vấn', 'off', 'Sắp đến')]},
    'num': {'code': 'T-C', 'name': 'Nhập số', 'h1': 'Ghi sản lượng', 'noun': 'sản lượng', 'perm': 'pr.output.record', 'err': 'PXVB-PR-0500',
            'why': 'Chưa có khoảng giờ nào để ghi. Ca cần ở trạng thái đang mở.', 'ok': 'Đã ghi 1.250 điếu cho giờ 15:00.', 'next': 'Giờ kế tiếp 16:00.', 'done': 2, 'done_sub': '1.250 điếu · vừa lưu',
            'rows': [('Giờ 13:00', '1.180 điếu', 'ok', 'Đã nhập'), ('Giờ 14:00', '1.250 điếu', 'ok', 'Đã nhập'), ('Giờ 15:00', 'chưa nhập', 'warn', 'Chưa nhập')]},
    'steps': {'code': 'T-F', 'name': 'Checklist lãnh đạo ca', 'h1': 'Mở ca 2', 'noun': 'bước mở ca', 'perm': 'pr.shift.open', 'err': 'PXVB-PR-0500',
              'why': 'Chưa có lệnh sản xuất nào cho ca này nên chưa mở ca được.', 'ok': 'Đã mở ca 2 lúc 13:58.', 'next': 'Chuyển sang trang chủ máy để bắt đầu ghi sản lượng.', 'done': 2, 'done_sub': 'Đã ký mở ca lúc 13:58',
              'rows': [('Nhân sự đã bố trí', 'Nguyễn Văn A, Trần Thị B', 'ok', 'Đạt'), ('Xác nhận lệnh sản xuất', 'LSX-2610-014', 'ok', 'Đạt'), ('Kiểm tra máy trước ca', 'Chưa làm', 'off', 'Chưa làm')]},
}


def tab_rows(rows, partial=False, hl=None):
    out = ''
    for i, (a, b, k, c) in enumerate(rows):
        if partial and i >= 1:
            b, k, c = 'Chưa có dữ liệu', 'off', 'Chưa có dữ liệu'
        out += '<div class="row' + (' sel' if hl == i else '') + '"><span style="flex:1"><b>' + esc(a) + '</b><small>' + esc(b) + '</small></span>' + chip(k, c) + '</div>'
    return out


def done_rows(e):
    rows = list(e['rows'])
    i = e['done']
    rows[i] = (rows[i][0], e['done_sub'], 'ok', 'Đã lưu')
    return rows


def b80(t, primary=False):
    return '<button class="btn' + (' p' if primary else '') + '">' + esc(t) + '</button>'


def tab_body(hint, state):
    e = TAB[hint]
    if state == 'loading':
        sks = ''.join('<div class="row">' + sk('45%', 22) + '<span style="flex:1"></span>' + sk(120, 32) + '</div>' for _ in range(4))
        return ('<section style="flex:1;display:flex;flex-direction:column;gap:12px;min-width:0">' + t_h1(e['h1']) + '<div style="display:flex;gap:12px;align-items:center">' + chip('info', 'Đang tải')
                + '<span style="font-size:20px;color:var(--ink2)">Đang lấy dữ liệu từ máy chủ…</span></div>' + sks + '</section>')
    if state == 'empty':
        return center('empty', 'Chưa có ' + e['noun'], e['why'], [b80('Quay lại'), b80('Đồng bộ ngay', True)], None, True)
    if state == 'error':
        return center('error', 'Không tải được dữ liệu', 'Máy chủ không phản hồi. Dữ liệu đã nhập trên tablet vẫn được giữ an toàn và sẽ gửi khi có mạng.',
                      [b80('Xem dữ liệu đã lưu'), b80('Thử lại', True)], e['err'], True)
    if state == 'noperm':
        return center('noperm', 'Bạn chưa có quyền ' + e['noun'], 'Nhờ lãnh đạo ca cấp quyền, hoặc đổi sang người dùng khác.',
                      [b80('Đổi người dùng'), b80('Báo lãnh đạo ca', True)], 'Quyền cần có: ' + e['perm'], True)
    if state == 'partial':
        return ('<section style="flex:1;display:flex;flex-direction:column;gap:12px;min-width:0">' + t_h1(e['h1'])
                + '<div style="padding:12px 16px;background:var(--warns);color:var(--warn);border-radius:12px;font-size:20px;font-weight:600">Một số mục chưa có dữ liệu. Phần còn lại vẫn dùng được.</div>'
                + tab_rows(e['rows'], True) + '</section>')
    snack = ('<div style="position:absolute;left:0;right:0;bottom:0;background:#16222F;color:#fff;border-radius:12px;padding:16px 24px;display:flex;gap:20px;align-items:center;font-size:22px">'
             '<span style="flex:1">' + esc(e['ok']) + '</span><button style="height:56px;padding:0 24px;border-radius:12px;border:2px solid #9CC4F0;background:none;color:#fff;font-size:20px;font-weight:700">Hoàn tác (10 giây)</button></div>')
    return ('<section style="flex:1;display:flex;flex-direction:column;gap:12px;min-width:0;position:relative">' + t_h1(e['h1'])
            + '<div role="status" style="padding:16px 20px;background:var(--oks);color:var(--ok);border-radius:12px;font-size:22px;font-weight:700;display:flex;gap:12px;align-items:center">'
            + chip('ok', 'Thành công') + esc(e['ok']) + '</div>' + tab_rows(done_rows(e), False, e['done'])
            + '<span style="font-size:20px;color:var(--ink2)">' + esc(e['next']) + '</span>' + snack + '</section>')


def tab_state_page(hint, state):
    e = TAB[hint]
    sync = ('off', 'Offline · dữ liệu giữ lại') if state == 'error' else ('ok', 'Đã đồng bộ 15:28')
    shell = tab_shell('<b class="mono" style="font-size:22px">VM-04</b><span>Ca 2 · 18/10/2026</span>', tab_body(hint, state), sync)
    return page('Trạng thái ' + STATE_VN[state] + ' – ' + e['code'], 1280, 800, lib.TAB_CSS + SK_CSS, shell)


# ------------------------- Kiosk -------------------------
def kcard(inner):
    return '<div style="background:#16222F;border-radius:16px;padding:32px 40px;display:flex;flex-direction:column;gap:12px">' + inner + '</div>'


def kmsg(t, s, col):
    return ('<div style="flex:1;background:#16222F;border-radius:16px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:20px;text-align:center;padding:48px">'
            '<b style="font-size:72px;line-height:80px;color:' + col + '">' + esc(t) + '</b><span style="font-size:36px;color:#C5D0DB">' + esc(s) + '</span></div>')


def kmetric(label, val, col, cap):
    return kcard('<span style="font-size:32px;color:#C5D0DB">' + esc(label) + '</span><b style="font-size:120px;line-height:128px;color:' + col + '">' + esc(val) + '</b><span style="font-size:28px;color:' + col + '">' + esc(cap) + '</span>')


def kiosk_body(state):
    head = ('<div style="display:flex;align-items:baseline;gap:24px"><b style="font-size:56px">Dây chuyền 1</b><span style="font-size:32px;color:#C5D0DB">Ca 2 · 18/10/2026</span><span style="flex:1"></span>'
            '<span style="font-size:32px;color:#C5D0DB">' + ('Cập nhật 15:21:40' if state == 'error' else 'Cập nhật 15:30:12') + '</span><b style="font-size:56px">15:30</b></div>')
    grid = '<div style="flex:1;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:32px">'
    if state == 'loading':
        body = grid + ''.join(kcard(sk(360, 32, True) + sk(300, 110, True) + sk(260, 28, True)) for _ in range(4)) + '</div>'
    elif state == 'empty':
        body = kmsg('Chưa có kế hoạch cho ca này', 'Màn hình sẽ tự cập nhật khi có lệnh sản xuất đầu tiên.', '#E8EEF4')
    elif state == 'error':
        body = ('<div role="alert" style="background:#3A1A18;border:3px solid #FF6B5E;border-radius:16px;padding:24px 40px;font-size:36px;color:#FF6B5E;font-weight:700">■ Mất kết nối. Số liệu cũ hơn 5 phút, không dùng để ra quyết định.</div>'
                + grid.replace('gap:32px', 'gap:32px;opacity:.55') + kmetric('Sản lượng so với kế hoạch', '78%', '#E8EEF4', '') + kmetric('OEE', '71,4%', '#E8EEF4', '')
                + kmetric('Máy đang chạy', '12/14', '#E8EEF4', '') + kmetric('Máy đang dừng', '2', '#E8EEF4', '') + '</div>')
    elif state == 'partial':
        body = (grid + kmetric('Sản lượng so với kế hoạch', '78%', '#F5C451', '▲ Chậm 22%') + kmetric('OEE', '—', '#8A97A5', '○ Chưa có dữ liệu')
                + kmetric('Máy đang chạy', '12/14', '#4CC38A', '● Bình thường') + kmetric('Tỷ lệ lỗi', '—', '#8A97A5', '○ Chưa có dữ liệu') + '</div>')
    elif state == 'success':
        body = kmsg('● Ca 2 đã chốt', 'Báo cáo ca đã được tạo. Màn hình chuyển sang ca 3 lúc 22:00.', '#4CC38A')
    else:
        body = kmsg('Màn hình chưa được cấp quyền', 'Liên hệ quản trị để cấp quyền cho thiết bị này. Quyền cần có: rp.wall.view', '#F5C451')
    return ('<div style="width:1920px;height:1080px;background:#0E1620;color:#E8EEF4;box-sizing:border-box;padding:48px 64px;display:flex;flex-direction:column;gap:32px">'
            + head + body + '<div style="font-size:28px;color:#C5D0DB">Trạng thái: ' + STATE_VN[state] + '</div></div>')


def main():
    cj_path = os.path.join(OUT, 'canvas.json')
    cj = json.load(open(cj_path, encoding='utf-8'))
    names = []
    y0 = 17400
    for r, hint in enumerate(WEB):
        for c, st in enumerate(STATES):
            n = 'S-' + WEB[hint]['code'] + '-' + st + '.dc.html'
            open(os.path.join(OUT, n), 'w', encoding='utf-8').write(web_state_page(hint, st))
            cj['boards'][n] = {'x': c * 1446, 'y': y0 + r * 900, 'w': 1366, 'h': 800, 'title': WEB[hint]['code'] + ' ' + WEB[hint]['name'] + ' – ' + STATE_VN[st]}
            names.append(n)
    ty = y0 + len(WEB) * 900 + 300
    for r, hint in enumerate(TAB):
        for c, st in enumerate(STATES):
            n = 'S-' + TAB[hint]['code'].replace('-', '') + '-' + st + '.dc.html'
            open(os.path.join(OUT, n), 'w', encoding='utf-8').write(tab_state_page(hint, st))
            cj['boards'][n] = {'x': c * 1360, 'y': ty + r * 900, 'w': 1280, 'h': 800, 'title': TAB[hint]['code'] + ' ' + TAB[hint]['name'] + ' – ' + STATE_VN[st]}
            names.append(n)
    ky = ty + len(TAB) * 900 + 300
    for c, st in enumerate(STATES):
        n = 'S-K1-' + st + '.dc.html'
        open(os.path.join(OUT, n), 'w', encoding='utf-8').write(page('Trạng thái ' + STATE_VN[st] + ' – K1', 1920, 1080, lib.TOKENS + SK_CSS, kiosk_body(st)))
        cj['boards'][n] = {'x': c * 2000, 'y': ky, 'w': 1920, 'h': 1080, 'title': 'K1 Kiosk – ' + STATE_VN[st]}
        names.append(n)
    for n in names:
        if n not in cj['order']:
            cj['order'].append(n)
    cj['notes']['s1'] = {'x': 0, 'y': y0 - 300, 'text': 'Sáu trạng thái – mẫu web W1 đến W6', 'kind': 'title1', 'maxW': 8676}
    cj['notes']['s2'] = {'x': 0, 'y': ty - 300, 'text': 'Sáu trạng thái – mẫu tablet T-A, T-C, T-F', 'kind': 'title1', 'maxW': 8160}
    cj['notes']['s3'] = {'x': 0, 'y': ky - 300, 'text': 'Sáu trạng thái – kiosk K1', 'kind': 'title1', 'maxW': 11920}
    json.dump(cj, open(cj_path, 'w', encoding='utf-8'), ensure_ascii=False)
    print(len(names))
    print(json.dumps(['project/' + n for n in names], ensure_ascii=False))


main()
