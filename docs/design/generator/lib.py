"""Bộ dựng màn hình PXVB theo mẫu W1-W6, K1, T-A..T-F (đầu ra: .dc.html)."""
from html import escape

FONTS = ('<link rel="preconnect" href="https://fonts.googleapis.com">'
         '<link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700&family=IBM+Plex+Mono:wght@500&display=swap" rel="stylesheet">')
TOKENS = (':root{--brand:#174E86;--brand-soft:#E6EEF8;--ink:#16202B;--ink2:#3F4B5A;--ink3:#5E6A7A;--bg:#F3F5F8;--sf2:#F8FAFC;'
          '--line:#D9DFE6;--line2:#B9C2CD;--ok:#1E7A46;--oks:#E2F3E9;--warn:#8A5A00;--warns:#FFF0C7;--bad:#B3261E;--bads:#FBE3E0;'
          '--off:#4A5666;--offs:#E6E9ED}'
          "body{margin:0;font-family:'Be Vietnam Pro',system-ui,sans-serif;color:var(--ink);font-variant-numeric:tabular-nums}"
          'button,input,select,textarea{font:inherit}button{cursor:pointer}'
          ".mono{font-family:'IBM Plex Mono',monospace}")
WEB_CSS = TOKENS + (
    'body{font-size:14px;line-height:20px}'
    'button:focus-visible,input:focus-visible,select:focus-visible,textarea:focus-visible,a:focus-visible{outline:3px solid var(--brand);outline-offset:2px}'
    '.chip{display:inline-flex;align-items:center;gap:6px;padding:1px 8px;border-radius:999px;font-size:12px;font-weight:600;white-space:nowrap;line-height:18px}'
    '.chip::before{content:"";width:8px;height:8px;background:currentColor;flex:none}'
    '.c-ok{background:var(--oks);color:var(--ok)}.c-ok::before{border-radius:50%}'
    '.c-bad{background:var(--bads);color:var(--bad)}'
    '.c-warn{background:var(--warns);color:var(--warn)}.c-warn::before{clip-path:polygon(50% 0,100% 100%,0 100%);height:8px;width:9px}'
    '.c-off{background:var(--offs);color:var(--off)}.c-off::before{background:none;border:2px solid currentColor;border-radius:50%;box-sizing:border-box}'
    '.c-info{background:var(--brand-soft);color:var(--brand)}.c-info::before{display:none}'
    '.btn{height:36px;padding:0 14px;border-radius:6px;border:1px solid var(--line2);background:#fff;font-weight:600;color:var(--ink)}'
    '.btn.p{background:var(--brand);border-color:var(--brand);color:#fff}'
    '.btn.d{color:var(--bad);border-color:var(--bad)}'
    '.btn.x{background:var(--offs);border-color:var(--offs);color:var(--off);cursor:not-allowed}'
    '.nav a{display:block;padding:8px 12px;border-radius:6px;color:var(--ink2);text-decoration:none}'
    '.nav a.on{background:var(--brand-soft);color:var(--brand);font-weight:600}'
    'table{border-collapse:collapse;width:100%}'
    'th{height:36px;padding:0 12px;background:var(--sf2);border-bottom:1px solid var(--line);font-size:12px;font-weight:600;color:var(--ink2);text-align:left}'
    'td{height:40px;padding:0 12px;border-bottom:1px solid var(--line);font-size:13px}'
    '.n{text-align:right}tr.sel td{background:var(--brand-soft)}'
    '.card{background:#fff;border:1px solid var(--line);border-radius:8px}'
    '.in{height:36px;box-sizing:border-box;padding:0 12px;border:1px solid var(--line2);border-radius:6px;background:#fff;color:var(--ink);width:100%}'
    '.in.ro{background:var(--sf2)}'
    'label{display:block;font-size:12px;font-weight:600;color:var(--ink2);margin-bottom:4px}'
    '.kpi{flex:1;background:#fff;border:1px solid var(--line);border-radius:8px;padding:12px 16px}'
    '.kpi small{color:var(--ink2);font-size:12px}.kpi b{display:block;font-size:28px;line-height:34px}'
    '.bar{height:6px;border-radius:3px;background:var(--offs);overflow:hidden}.bar i{display:block;height:100%;background:var(--brand)}'
    '.chk{display:flex;align-items:center;gap:12px;min-height:52px;padding:8px 14px;border:1px solid var(--line);border-radius:8px;background:#fff}'
    '.chk .tx{flex:1}.chk small{display:block;color:var(--ink3);font-size:12px}'
    '.gh{padding:8px;font-size:12px;font-weight:600;color:var(--ink2);background:var(--sf2);border-right:1px solid var(--line);border-bottom:1px solid var(--line)}'
    '.gm{padding:8px 12px;font-weight:600;border-right:1px solid var(--line);border-bottom:1px solid var(--line);background:#fff;display:flex;align-items:center}'
    '.gc{border-right:1px solid var(--line);border-bottom:1px solid var(--line);background:#fff;padding:4px;display:flex;align-items:center;min-height:46px}'
    '.gc .chip{white-space:normal;border-radius:10px}.gc.sel{outline:3px solid var(--brand);outline-offset:-3px;background:var(--brand-soft)}'
)
TAB_CSS = TOKENS + (
    'button:focus-visible{outline:4px solid #F2B01E;outline-offset:2px}'
    '.chip{display:inline-flex;align-items:center;gap:8px;padding:4px 14px;border-radius:999px;font-size:18px;font-weight:600;white-space:nowrap}'
    '.chip::before{content:"";width:12px;height:12px;background:currentColor;flex:none}'
    '.c-ok{background:var(--oks);color:var(--ok)}.c-ok::before{border-radius:50%}'
    '.c-bad{background:var(--bads);color:var(--bad)}'
    '.c-warn{background:var(--warns);color:var(--warn)}.c-warn::before{clip-path:polygon(50% 0,100% 100%,0 100%);height:11px;width:14px}'
    '.c-off{background:var(--offs);color:var(--off)}.c-off::before{background:none;border:3px solid currentColor;border-radius:50%;box-sizing:border-box}'
    '.c-info{background:var(--brand-soft);color:var(--brand)}.c-info::before{display:none}'
    '.row{display:flex;align-items:center;gap:16px;min-height:80px;padding:8px 20px;border:2px solid var(--line);border-radius:12px;background:#fff;font-size:22px;text-align:left;width:100%;color:var(--ink);box-sizing:border-box}'
    '.row.sel{border-color:var(--brand);background:var(--brand-soft)}.row small{display:block;font-size:18px;color:var(--ink2)}'
    '.btn{height:64px;padding:0 28px;border-radius:12px;border:2px solid var(--line2);background:#fff;font-size:20px;font-weight:600;color:var(--ink)}'
    '.btn.p{background:var(--brand);border-color:var(--brand);color:#fff;height:80px;font-size:24px;font-weight:700}'
    '.btn.x{background:var(--offs);border-color:var(--offs);color:var(--off);cursor:not-allowed}'
    '.key{height:72px;border-radius:12px;border:2px solid var(--line2);background:#fff;font-size:30px;font-weight:600;color:var(--ink)}'
    '.opt{min-height:72px;padding:0 16px;border-radius:12px;border:2px solid var(--line2);background:#fff;font-size:22px;font-weight:600;color:var(--ink)}'
    '.opt.on{border-color:var(--brand);background:var(--brand-soft);color:var(--brand)}'
)

ROW_STYLE = {'bad': ' style="border-color:var(--bad);background:var(--bads)"', 'warn': ' style="border-color:#F2B01E;background:var(--warns)"'}
NOTE_STYLE = {'bad': 'var(--bads);color:var(--bad)', 'warn': 'var(--warns);color:var(--warn)', 'ok': 'var(--oks);color:var(--ok)', 'off': 'var(--offs);color:var(--off)'}


KIOSK_SHAPE = {'#4CC38A': '● ', '#F5C451': '▲ ', '#FF6B5E': '■ '}


def esc(s):
    return escape(str(s), quote=False)


def attr(cls, cond=True):
    return ' class="' + cls + '"' if cond else ''


def chip(kind, text):
    return '<span class="chip c-' + kind + '">' + esc(text) + '</span>'


def cell(v):
    v = str(v)
    for k in ('ok', 'bad', 'warn', 'off', 'info'):
        if v.startswith(k + ':'):
            return chip(k, v[len(k) + 1:])
    if v.startswith('m:'):
        return '<span class="mono">' + esc(v[2:]) + '</span>'
    if v.startswith('b:'):
        return '<b>' + esc(v[2:]) + '</b>'
    return esc(v)


def page(title, w, h, css, body):
    return ('<!doctype html>\n<html lang="vi">\n<head>\n<meta charset="utf-8">\n'
            '<title>' + esc(title) + '</title>\n<script src="./support.js"></script>\n</head>\n<body>\n<x-dc>\n'
            '<helmet>\n' + FONTS + '\n<style>\n' + css + '\n</style>\n</helmet>\n' + body + '\n</x-dc>\n'
            '<script type="text/x-dc" data-dc-script data-props=\'{"$preview":{"width":' + str(w) + ',"height":' + str(h) + '}}\'>\n'
            'class Component extends DCLogic {\n  renderVals() { return {}; }\n}\n</script>\n</body>\n</html>\n')


def btn(t):
    if t[:1] == '*':
        return '<button class="btn p">' + esc(t[1:]) + '</button>'
    if t[:1] == '!':
        return '<button class="btn d">' + esc(t[1:]) + '</button>'
    if t[:1] == '-':
        return '<button disabled class="btn x">' + esc(t[1:]) + '</button>'
    return '<button class="btn">' + esc(t) + '</button>'


NAV = ['Tổng quan', 'Kế hoạch sản xuất', 'Sản xuất', 'Nguyên vật liệu', 'Chất lượng', 'Tem', 'Bảo trì', 'Phụ tùng', 'Báo cáo', 'Danh mục và quản trị']


def web_shell(active, crumb, title, body, status=None, updated='Cập nhật lúc 15:30'):
    nav = ''.join('<a href="#"' + attr('on', n == active) + '>' + esc(n) + '</a>' for n in NAV)
    st = chip(*status) if status else ''
    return (
        '<div style="width:1366px;height:800px;display:flex;background:var(--bg);overflow:hidden">'
        '<nav class="nav" style="width:216px;background:#fff;border-right:1px solid var(--line);padding:16px 12px;box-sizing:border-box;display:flex;flex-direction:column;gap:2px;flex:none">'
        '<div style="font-weight:700;font-size:18px;padding:4px 12px 16px">PXVB</div>' + nav + '</nav>'
        '<div style="flex:1;display:flex;flex-direction:column;min-width:0">'
        '<header style="height:56px;background:#fff;border-bottom:1px solid var(--line);display:flex;align-items:center;gap:12px;padding:0 24px;flex:none">'
        '<span style="color:var(--ink3)">' + esc(crumb) + ' /</span><b>' + esc(title) + '</b>' + st + '<span style="flex:1"></span>'
        '<span style="color:var(--ink2)">' + esc(updated) + '</span></header>' + body + '</div></div>')


def table(cols, rows, sel=None):
    th = ''.join('<th' + attr('n', a == 'n') + '>' + esc(c) + '</th>' for c, a in cols)
    out = []
    for i, r in enumerate(rows):
        tds = ''.join('<td' + attr('n', cols[j][1] == 'n') + '>' + cell(v) + '</td>' for j, v in enumerate(r))
        out.append('<tr' + attr('sel', i == sel) + '>' + tds + '</tr>')
    return '<table><thead><tr>' + th + '</tr></thead><tbody>' + ''.join(out) + '</tbody></table>'


def note(kind, text):
    return '<p style="margin:0;padding:10px 12px;background:' + NOTE_STYLE[kind] + ';border-radius:6px;font-size:13px">' + esc(text) + '</p>'


def aside(a, w=360):
    if not a:
        return ''
    ch = chip(*a['chip']) if a.get('chip') else ''
    head = '<div style="display:flex;align-items:center;gap:8px"><b style="font-size:16px">' + esc(a['title']) + '</b>' + ch + '</div>'
    lines = ''.join('<div style="display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid var(--line);padding:6px 0"><span style="color:var(--ink2)">'
                    + esc(k) + '</span><span>' + cell(v) + '</span></div>' for k, v in a.get('lines', []))
    nt = note(*a['note']) if a.get('note') else ''
    acts = ''.join(btn(t) for t in a.get('actions', []))
    return ('<aside class="card" style="width:' + str(w) + 'px;padding:16px;box-sizing:border-box;display:flex;flex-direction:column;gap:10px;flex:none;overflow:hidden">'
            + head + lines + nt + '<span style="flex:1"></span><div style="display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end">' + acts + '</div></aside>')


def toolbar(c):
    s = c.get('search')
    left = ('<input aria-label="Tìm" placeholder="' + esc(s) + '" style="width:300px;height:36px;box-sizing:border-box;padding:0 12px;border:1px solid var(--line2);border-radius:6px;font:inherit">') if s else ''
    return ('<div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">' + left + ''.join(btn(t) for t in c.get('tools', []))
            + '<span style="flex:1"></span>' + ''.join(btn(t) for t in c.get('tools_r', [])) + '</div>')


def w_list(c):
    foot = note(*c['foot']) if c.get('foot') else ''
    body = ('<main style="flex:1;padding:16px 24px;display:flex;flex-direction:column;gap:12px;min-width:0;min-height:0">' + toolbar(c)
            + '<div style="flex:1;display:flex;gap:16px;min-height:0"><div class="card" style="flex:1;overflow:hidden;min-width:0;align-self:flex-start">'
            + table(c['cols'], c['rows'], c.get('sel')) + '</div>' + aside(c.get('side')) + '</div>' + foot + '</main>')
    return web_shell(c['nav'], c['crumb'], c['title'], body, c.get('status'))


def w_form(c):
    fields = ''
    for i, a in enumerate(c['fields']):
        ro = a[2] if len(a) > 2 else False
        mono = a[3] if len(a) > 3 else False
        cls = 'in' + (' ro' if ro else '') + (' mono' if mono else '')
        fid = 'f' + str(i)
        fields += ('<div><label for="' + fid + '">' + esc(a[0]) + '</label><input id="' + fid + '" class="' + cls + '" value="' + esc(a[1]) + '"' + (' readonly' if ro else '') + '></div>')
    tb = ''
    if c.get('cols'):
        tb = ('<b style="font-size:16px">' + esc(c['table_title']) + '</b><div class="card" style="overflow:hidden">' + table(c['cols'], c['rows']) + '</div>')
    hist = ''.join('<div style="font-size:12px;color:var(--ink2)">' + esc(h) + '</div>' for h in c.get('history', []))
    nt = note(*c['note']) if c.get('note') else ''
    body = ('<div style="flex:1;display:flex;min-height:0"><main style="flex:1;padding:16px 24px;display:flex;flex-direction:column;gap:12px;min-width:0">'
            '<div class="card" style="padding:20px;flex:1;display:flex;flex-direction:column;gap:14px;box-sizing:border-box;min-height:0">'
            '<div style="display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px">' + fields + '</div>' + tb + nt
            + '<span style="flex:1"></span><div style="display:flex;gap:8px;justify-content:flex-end">' + ''.join(btn(t) for t in c['actions']) + '</div></div></main>'
            '<aside style="width:300px;background:#fff;border-left:1px solid var(--line);padding:20px;box-sizing:border-box;display:flex;flex-direction:column;gap:8px;flex:none">'
            '<b style="font-size:16px">' + esc(c.get('side_title', 'Lịch sử phiên bản')) + '</b>' + hist + '</aside></div>')
    return web_shell(c['nav'], c['crumb'], c['title'], body, c.get('status'))


def w_grid(c):
    n = len(c['cols'])
    heads = '<div class="gh">' + esc(c.get('corner', '')) + '</div>' + ''.join('<div class="gh">' + esc(h) + '</div>' for h in c['cols'])
    sr, sc = c.get('sel', (-1, -1))
    mono = c.get('mono_rows', True)
    cells = []
    for ri, (rn, row) in enumerate(zip(c['rows'], c['cells'])):
        cells.append('<div class="gm' + (' mono' if mono else '') + '">' + esc(rn) + '</div>')
        for ci, v in enumerate(row):
            cells.append('<div class="gc' + (' sel' if (ri, ci) == (sr, sc) else '') + '">' + (cell(v) if v else '') + '</div>')
    leg = ''.join(chip(*x) for x in c.get('legend', []))
    body = ('<div style="flex:1;display:flex;min-height:0"><main style="flex:1;padding:16px 24px;display:flex;flex-direction:column;gap:12px;min-width:0">' + toolbar(c)
            + '<div style="display:flex;gap:10px;flex-wrap:wrap">' + leg + '</div>'
            '<div style="display:grid;grid-template-columns:' + str(c.get('first', 110)) + 'px repeat(' + str(n) + ',minmax(0,1fr));border-top:1px solid var(--line);border-left:1px solid var(--line);border-radius:6px;overflow:hidden;align-content:start;background:#fff">'
            + heads + ''.join(cells) + '</div></main><div style="padding:16px 24px 16px 0">' + aside(c.get('side'), 320) + '</div></div>')
    return web_shell(c['nav'], c['crumb'], c['title'], body, c.get('status'))


def w_flow(c):
    items = ''
    for k, lab, t, s in c['items']:
        sub = '<small>' + esc(s) + '</small>' if s else ''
        items += '<div class="chk"' + ROW_STYLE.get(k, '') + '>' + chip(k, lab) + '<span class="tx"><b>' + esc(t) + '</b>' + sub + '</span></div>'
    ta = ('<textarea aria-label="Ghi chú" rows="2" style="border:1px solid var(--line2);border-radius:6px;padding:8px 12px">' + esc(c['text']) + '</textarea>') if c.get('text') else ''
    sig = ''.join('<div style="display:flex;gap:8px;align-items:center">' + chip(k, lab) + esc(n) + '</div>' for k, lab, n in c['sign'])
    body = ('<div style="flex:1;display:flex;min-height:0"><main style="flex:1;padding:16px 24px;display:flex;flex-direction:column;gap:10px;min-width:0">'
            '<h1 style="margin:0;font-size:22px;line-height:28px">' + esc(c['h1']) + '</h1>' + items + ta + '<span style="flex:1"></span>'
            '<div style="display:flex;gap:8px;justify-content:flex-end;align-items:center"><span style="flex:1;color:var(--bad);font-size:13px">' + esc(c.get('reason', '')) + '</span>'
            + ''.join(btn(t) for t in c['actions']) + '</div></main>'
            '<aside style="width:320px;background:#fff;border-left:1px solid var(--line);padding:20px;box-sizing:border-box;display:flex;flex-direction:column;gap:12px;flex:none">'
            '<b style="font-size:16px">' + esc(c.get('sign_title', 'Chữ ký xác nhận')) + '</b>' + sig
            + '<span style="font-size:12px;color:var(--ink3)">' + esc(c.get('side_note', '')) + '</span></aside></div>')
    return web_shell(c['nav'], c['crumb'], c['title'], body, c.get('status'))


def w_dash(c):
    kp = ''.join('<div class="kpi"><small>' + esc(l) + '</small><b>' + esc(v) + '</b>' + chip(k, t) + '</div>' for l, v, k, t in c['kpis'])
    cards = ''
    for t, k, lab, s, p in c['cards']:
        extra = 'border-color:var(--bad);background:var(--bads);' if k == 'bad' else ''
        bar = '<div class="bar"><i style="width:' + str(p) + '%"></i></div>' if p is not None else ''
        cards += ('<div class="card" style="padding:10px 12px;display:flex;flex-direction:column;gap:6px;' + extra + '">'
                  '<div style="display:flex;justify-content:space-between;align-items:center;gap:8px"><b>' + esc(t) + '</b>' + chip(k, lab) + '</div>'
                  '<span style="color:var(--ink2);font-size:12px">' + esc(s) + '</span>' + bar + '</div>')
    lst = ''.join('<div style="border:1px solid var(--line);border-radius:6px;padding:8px 10px"><div style="display:flex;justify-content:space-between;gap:8px"><b>' + esc(t) + '</b>' + chip(k, lab)
                  + '</div><span style="font-size:12px;color:var(--ink2)">' + esc(s) + '</span></div>' for t, k, lab, s in c['list'])
    stale = ('<div role="status" style="background:var(--warns);color:var(--warn);padding:10px 24px;border-bottom:1px solid #F2B01E;font-weight:600">' + esc(c['stale']) + '</div>') if c.get('stale') else ''
    body = (stale + '<main style="flex:1;padding:16px 24px;display:flex;flex-direction:column;gap:12px;min-height:0"><div style="display:flex;gap:12px">' + kp + '</div>'
            '<div style="flex:1;display:flex;gap:12px;min-height:0"><section style="flex:1;display:flex;flex-direction:column;gap:8px;min-width:0"><b style="font-size:16px">' + esc(c['cards_title']) + '</b>'
            '<div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px">' + cards + '</div></section>'
            '<aside class="card" style="width:340px;padding:16px;box-sizing:border-box;display:flex;flex-direction:column;gap:10px;flex:none;overflow:hidden"><b style="font-size:16px">'
            + esc(c['list_title']) + '</b>' + lst + '</aside></div></main>')
    return web_shell(c['nav'], c['crumb'], c['title'], body, c.get('status'))


def w_report(c):
    secs = ''
    for h, tb in c['sections']:
        inner = table(tb[0], tb[1]) if isinstance(tb, tuple) else '<p style="margin:0;padding:8px 12px;color:var(--ink2)">' + esc(tb) + '</p>'
        secs += '<b style="font-size:14px">' + esc(h) + '</b><div style="border:1px solid var(--line);overflow:hidden">' + inner + '</div>'
    sg = ''.join('<div style="flex:1;border-top:1px solid var(--ink3);padding-top:6px;font-size:12px;color:var(--ink2);text-align:center">' + esc(s) + '</div>' for s in c['signers'])
    ms = note('warn', c['missing']) if c.get('missing') else ''
    body = ('<div style="flex:1;display:flex;min-height:0"><main style="flex:1;background:#E6E9ED;padding:16px;display:flex;justify-content:center;overflow:hidden">'
            '<div style="width:760px;background:#fff;border:1px solid var(--line2);padding:28px 32px;box-sizing:border-box;display:flex;flex-direction:column;gap:10px;overflow:hidden">'
            '<div style="text-align:center"><b style="font-size:18px">' + esc(c['paper_title']) + '</b><br><span style="color:var(--ink2)">' + esc(c['paper_sub']) + '</span></div>' + secs
            + '<span style="flex:1"></span><div style="display:flex;gap:24px;margin-top:16px">' + sg + '</div></div></main>'
            '<aside style="width:320px;background:#fff;border-left:1px solid var(--line);padding:20px;box-sizing:border-box;display:flex;flex-direction:column;gap:12px;flex:none">'
            '<b style="font-size:16px">Bản báo cáo</b><div><label for="rv">Revision</label><select id="rv" class="in"><option>' + esc(c['rev']) + '</option></select></div>'
            '<div><label for="pd">Kỳ báo cáo</label><input id="pd" class="in" value="' + esc(c['period']) + '"></div>' + ms + '<span style="flex:1"></span>'
            '<div style="display:flex;gap:8px;justify-content:flex-end">' + ''.join(btn(t) for t in c['actions']) + '</div></aside></div>')
    return web_shell(c['nav'], c['crumb'], c['title'], body, c.get('status'))


def w_kiosk(c):
    cards = ''.join(
        '<div style="background:#16222F;border-radius:16px;padding:32px 40px;display:flex;flex-direction:column;gap:12px"><span style="font-size:32px;color:#C5D0DB">' + esc(l) + '</span>'
        '<b style="font-size:120px;line-height:128px;color:' + col + '">' + esc(v) + '</b><span style="font-size:28px;color:' + col + '">' + KIOSK_SHAPE.get(col, '') + esc(s) + '</span></div>'
        for l, v, col, s in c['cards'])
    return ('<div style="width:1920px;height:1080px;background:#0E1620;color:#E8EEF4;box-sizing:border-box;padding:48px 64px;display:flex;flex-direction:column;gap:32px">'
            '<div style="display:flex;align-items:baseline;gap:24px"><b style="font-size:56px">' + esc(c['title']) + '</b><span style="font-size:32px;color:#C5D0DB">' + esc(c['sub']) + '</span><span style="flex:1"></span>'
            '<span style="font-size:32px;color:#C5D0DB">Cập nhật 15:30:12</span><b style="font-size:56px">15:30</b></div>'
            '<div style="flex:1;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:32px">' + cards + '</div>'
            '<div style="font-size:28px;color:#C5D0DB">' + esc(c['foot']) + '</div></div>')


def tab_shell(left, body, sync=('ok', 'Đã đồng bộ 15:28'), clock='15:30'):
    return ('<div style="width:1280px;height:800px;box-sizing:border-box;background:var(--bg);display:flex;flex-direction:column">'
            '<div style="height:64px;background:#fff;border-bottom:2px solid var(--line);display:flex;align-items:center;padding:0 24px;gap:28px;font-size:20px;flex:none">'
            + left + '<span style="flex:1"></span>' + chip(*sync) + '<b style="font-size:22px">' + esc(clock) + '</b></div>'
            '<div style="flex:1;display:flex;gap:24px;padding:24px;min-height:0">' + body + '</div></div>')


def tside(c, w=360):
    ex = ''.join('<button class="btn">' + esc(t) + '</button>' for t in c.get('second', []))
    if c['primary'][:1] == '-':
        p = '<button class="btn p x" disabled>' + esc(c['primary'][1:]) + '</button>'
    else:
        p = '<button class="btn p">' + esc(c['primary']) + '</button>'
    return ('<aside style="width:' + str(w) + 'px;background:#fff;border:2px solid var(--line);border-radius:12px;padding:24px;display:flex;flex-direction:column;gap:16px;box-sizing:border-box">'
            '<h2 style="margin:0;font-size:24px;line-height:30px">' + esc(c['title']) + '</h2><p style="margin:0;font-size:20px;line-height:28px;color:var(--ink2)">' + esc(c['text']) + '</p>'
            '<span style="flex:1"></span>' + p + ex + '</aside>')


def rows_html(items):
    out = ''
    for it in items:
        lab, sub, k, ch = it[:4]
        sel = len(it) > 4 and it[4]
        sm = '<small>' + esc(sub) + '</small>' if sub else ''
        out += '<button class="row' + (' sel' if sel else '') + '"><span style="flex:1"><b>' + esc(lab) + '</b>' + sm + '</span>' + chip(k, ch) + '</button>'
    return out


def t_page(c, body):
    return page(c['title'], 1280, 800, TAB_CSS, tab_shell(c['left'], body))


def t_h1(t):
    return '<h1 style="margin:0 0 4px;font-size:36px;line-height:44px">' + esc(t) + '</h1>'


def t_list(c):
    body = ('<section style="flex:1;display:flex;flex-direction:column;gap:12px;min-width:0">' + t_h1(c['h1']) + rows_html(c['items']) + '</section>' + tside(c['side']))
    return t_page(c, body)


def t_num(c):
    keys = ''.join('<button class="key">' + k + '</button>' for k in ['7', '8', '9', '4', '5', '6', '1', '2', '3', ',', '0', 'Xóa'])
    body = ('<section style="width:520px;display:flex;flex-direction:column;gap:12px">' + t_h1(c['h1']) + rows_html(c['items']) + '</section>'
            '<section style="flex:1;background:#fff;border:2px solid var(--line);border-radius:12px;padding:24px;display:flex;flex-direction:column;gap:14px;box-sizing:border-box">'
            '<h2 style="margin:0;font-size:24px;line-height:30px">' + esc(c['entry']) + '</h2>'
            '<div style="display:flex;align-items:baseline;gap:12px;border:2px solid var(--brand);border-radius:12px;padding:8px 20px"><span class="mono" style="font-size:60px;line-height:68px;flex:1;text-align:right">'
            + esc(c['value']) + '</span><span style="font-size:28px;color:var(--ink2)">' + esc(c['unit']) + '</span></div>'
            '<div style="display:flex;gap:12px;align-items:center">' + chip(c['hint'][0], c['hint'][1]) + '<span style="font-size:20px;color:var(--ink2)">' + esc(c['hint'][2]) + '</span></div>'
            '<div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;flex:1">' + keys + '</div>'
            '<button class="btn p">' + esc(c['primary']) + '</button></section>')
    return t_page(c, body)


def t_pick(c):
    groups = ''
    for g, opts, sel in c['groups']:
        bs = ''.join('<button class="opt' + (' on' if i == sel else '') + '">' + esc(o) + '</button>' for i, o in enumerate(opts))
        groups += ('<div><h2 style="margin:0 0 8px;font-size:24px;line-height:30px">' + esc(g) + '</h2>'
                   '<div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px">' + bs + '</div></div>')
    body = ('<section style="flex:1;display:flex;flex-direction:column;gap:16px;min-width:0">' + t_h1(c['h1']) + groups + '</section>' + tside(c['side'], 380))
    return t_page(c, body)


def t_form(c):
    rows = ''
    for lab, kind, extra in c['items']:
        if kind == 'num':
            ctl = ('<span class="mono" style="font-size:32px;border:2px solid var(--line2);border-radius:12px;padding:6px 20px;min-width:160px;text-align:right">' + esc(extra[0])
                   + '</span><span style="font-size:20px;color:var(--ink2)">' + esc(extra[1]) + '</span>')
        else:
            bad = ' style="width:190px;border-color:var(--bad);background:var(--bads);color:var(--bad)"' if extra == 2 else ' style="width:190px"'
            ctl = ('<button class="opt' + (' on' if extra == 1 else '') + '" style="width:150px">Đạt</button>'
                   '<button class="opt"' + bad + '>Không đạt</button>')
        rows += '<div class="row" style="min-height:84px"><span style="flex:1"><b>' + esc(lab) + '</b></span>' + ctl + '</div>'
    body = ('<section style="flex:1;display:flex;flex-direction:column;gap:10px;min-width:0">' + t_h1(c['h1']) + rows + '</section>' + tside(c['side'], 340))
    return t_page(c, body)


def t_steps(c):
    st = ''
    for i, (k, t, s) in enumerate(c['steps'], 1):
        bd = 'border-color:#F2B01E;' if k == 'warn' else ('border-color:var(--bad);' if k == 'bad' else '')
        st += ('<div class="row" style="' + bd + 'min-height:84px"><span style="width:44px;height:44px;border-radius:50%;background:var(--offs);display:flex;align-items:center;justify-content:center;font-weight:700;font-size:22px;flex:none">'
               + str(i) + '</span><span style="flex:1"><b>' + esc(t) + '</b><small>' + esc(s) + '</small></span>' + chip(k, c['labels'][k]) + '</div>')
    body = ('<section style="flex:1;display:flex;flex-direction:column;gap:12px;min-width:0">' + t_h1(c['h1']) + st + '</section>' + tside(c['side'], 400))
    return t_page(c, body)
