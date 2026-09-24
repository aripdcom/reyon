#!/usr/bin/env python3
"""Play Console'un istediği iki görseli üretir: simge ve öne çıkan görsel.

Kaynak, uygulamanın kendi simgesidir: `res/drawable/ic_launcher_foreground.xml`
içindeki "mark" grubu (üç raf çizgisi, ambalajlar, biri dikkat sarısında),
`res/values/colors.xml` içindeki arka plan rengiyle. Değerler oradan okunur, elle
kopyalanmaz — simge değişirse görseller de değişir. Yazı, uygulamanın kendi IBM
Plex dosyasından (`res/font/ibm_plex_sans_semibold.ttf`) çizilir.

Çıktılar (store/graphics/):
  icon-512.png      512×512, 32 bit. Play kendi maskesini uygular
  feature-1024.png  1024×500. Metin yok: 14 dilin hepsinde aynı görsel geçerli

SVG'ler de yanına yazılır; PNG'yi Chromium'un başsız kipi çizer (depoda
raster araç yok). Chromium yolu REYON_CHROME ile verilebilir.

Kullanım: python3 tools/gen_store_graphics.py
"""
import os
import re
import shutil
import subprocess
import sys
import zlib

OUT = 'store/graphics'
FOREGROUND = 'app/src/main/res/drawable/ic_launcher_foreground.xml'
COLORS = 'app/src/main/res/values/colors.xml'
FONT = 'app/src/main/res/font/ibm_plex_sans_semibold.ttf'

# İşaret 36 birimlik kutuda yazılır; simgedeki "mark" grubu onu tuvale taşır.
MARK_BOX = 36.0
ATTENTION = '#FFD23F'

# Uyarlanır simgenin 108 birimlik tuvalinde görünen alan ortadaki 72 birim;
# mağaza simgesi o alanın tamamını kaplar.
CANVAS = 108.0
VISIBLE = 72.0

# headless_shell öncelikli: tam pencere kipindeki Chromium tuvali istenen
# yüksekliğe getirmeyip altını kırpıyor. Kırpma sessizdir, o yüzden çizim
# ayrıca işaret rengiyle denetlenir (aşağıda).
CHROME_CANDIDATES = [
    os.environ.get('REYON_CHROME'),
    '/opt/pw-browsers/chromium_headless_shell-1194/chrome-linux/headless_shell',
    shutil.which('headless_shell'),
    '/opt/pw-browsers/chromium-1194/chrome-linux/chrome',
    shutil.which('chromium'),
    shutil.which('chromium-browser'),
    shutil.which('google-chrome'),
]

# Sayfanın zemini: çizimde hiç kullanılmayan bir renk. Çıktıda görünürse
# çizim tuvali kaplamamış demektir.
MARKER = (255, 0, 255)


def attr(tag, name, default=None):
    m = re.search(r'android:' + name + r'="([^"]*)"', tag)
    return m.group(1) if m else default


def icon_source():
    """Simge çiziminden (arka plan, işaret grubunun dönüşümü, SVG yolları).

    VectorDrawable'ın pathData'sı SVG yol diliyle aynı; yollar olduğu gibi
    taşınır. Grubun dışında şekil olmamalı ve yolların başlangıç noktaları
    36 birimlik kutunun içinde kalmalı: öne çıkan görsel işaretin yerini bu
    kutudan hesaplıyor.
    """
    src = open(FOREGROUND, encoding='utf-8').read()
    group = re.search(r'<group\b([^>]*android:name="mark"[^>]*)>(.*?)</group>', src, re.S)
    if not group:
        sys.exit(f'{FOREGROUND}: "mark" grubu yok')
    outside = src[:group.start()] + src[group.end():]
    if '<path' in outside:
        sys.exit(f'{FOREGROUND}: "mark" grubunun dışında şekil var; üretici onu çizmez')
    head = group.group(1)
    transform = (float(attr(head, 'translateX', '0')), float(attr(head, 'translateY', '0')),
                 float(attr(head, 'scaleX', '1')), float(attr(head, 'scaleY', '1')))
    paths = []
    for tag in re.findall(r'<path\b([^>]*)/>', group.group(2), re.S):
        data = attr(tag, 'pathData')
        for x, y in re.findall(r'M([\d.]+),([\d.]+)', data):
            if not (0 <= float(x) <= MARK_BOX and 0 <= float(y) <= MARK_BOX):
                sys.exit(f'{FOREGROUND}: ({x},{y}) {MARK_BOX:g} birimlik kutunun dışında')
        fill, stroke = attr(tag, 'fillColor'), attr(tag, 'strokeColor')
        style = f'fill="{fill}"' if fill else 'fill="none"'
        if stroke:
            style += (f' stroke="{stroke}" stroke-width="{attr(tag, "strokeWidth", "1")}"'
                      f' stroke-linecap="{attr(tag, "strokeLineCap", "butt")}"')
        paths.append(f'<path d="{data}" {style}/>')
    if not paths:
        sys.exit(f'{FOREGROUND}: "mark" grubunda yol yok')
    background = re.search(r'name="ic_launcher_background">(#[0-9A-Fa-f]+)<',
                           open(COLORS, encoding='utf-8').read()).group(1)
    return background, transform, paths


def mark_group(paths, x, y, size):
    """İşareti (x, y) köşesine, size piksel kenarlı kutuya yerleştiren <g>."""
    scale = size / MARK_BOX
    body = '\n'.join('    ' + p for p in paths)
    return f'  <g transform="translate({x:.2f},{y:.2f}) scale({scale:.5f})">\n{body}\n  </g>'


def icon_svg(background, transform, paths):
    """512×512: uyarlanır simgenin görünen 72 birimi (18–90) tüm kareyi kaplar."""
    side = 512
    unit = side / VISIBLE
    tx, ty, sx, _ = transform
    x = (tx - (CANVAS - VISIBLE) / 2) * unit
    y = (ty - (CANVAS - VISIBLE) / 2) * unit
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{side}" height="{side}">\n'
            f'  <rect width="{side}" height="{side}" fill="{background}"/>\n'
            f'{mark_group(paths, x, y, MARK_BOX * sx * unit)}\n'
            f'</svg>\n')


def feature_svg(background, transform, paths, font_url):
    """1024×500 öne çıkan görsel. Uygulamanın adından başka metin yok: tek
    görsel 14 dilin hepsinde geçerli olsun diye.

    Yazı uygulamanın IBM Plex dosyasıyla çizilir; genişliği `textLength` ile
    çivili, yazı tipi yüklenmese bile yerleşim kaymaz. Altındaki sarı çizgi
    paylaşım kartındaki gibi dikkat rengi.
    """
    w, h = 1024, 500
    mark = 250.0
    x, y = 120.0, (h - mark) / 2
    # 278 px: "Reyon"un IBM Plex Sans SemiBold 96 px'teki doğal genişliği
    # (ilerleme genişlikleri toplamı 2897/1000 em). Yazı tipi yüklenince harfler
    # esnemez; yüklenmezse yedek yazı aynı genişliğe oturur.
    text_x, text_w = 430, 278
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}">
  <defs>
    <style>@font-face{{font-family:"Reyon Plex";src:url("{font_url}")}}</style>
    <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
      <path d="M40,0 V40 H0" fill="none" stroke="#ffffff" stroke-opacity="0.06" stroke-width="1"/>
    </pattern>
  </defs>
  <rect width="{w}" height="{h}" fill="{background}"/>
  <rect width="{w}" height="{h}" fill="url(#grid)"/>
{mark_group(paths, x, y, mark)}
  <text x="{text_x}" y="262" textLength="{text_w}" lengthAdjust="spacingAndGlyphs"
        font-family="Reyon Plex, DejaVu Sans, Liberation Sans, sans-serif"
        font-size="96" font-weight="600" fill="#FFFFFF">Reyon</text>
  <rect x="{text_x}" y="296" width="120" height="8" fill="{ATTENTION}"/>
</svg>
'''


PAGE = """<!doctype html><meta charset="utf-8">
<style>html,body{{margin:0;padding:0;overflow:hidden;background:rgb{marker}}}
svg{{display:block}}</style>
{svg}"""


def render(chrome, svg, png_path, width, height, workdir):
    page = os.path.join(workdir, 'page.html')
    open(page, 'w', encoding='utf-8').write(PAGE.format(svg=svg, marker=MARKER))
    subprocess.run(
        [chrome, '--headless', '--no-sandbox', '--disable-gpu', '--hide-scrollbars',
         '--force-device-scale-factor=1',
         f'--screenshot={png_path}', f'--window-size={width},{height}', page],
        check=True, capture_output=True)
    os.remove(page)


def read_png(path):
    """(genişlik, yükseklik, piksel başına bayt, satırlar).

    Depoda raster kütüphanesi yok; çıktıyı doğrulamak için bu kadarı yeter.
    """
    data = open(path, 'rb').read()
    pos, idat, width, height, ctype = 8, b'', 0, 0, 0
    while pos < len(data):
        length = int.from_bytes(data[pos:pos + 4], 'big')
        kind = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        if kind == b'IHDR':
            width = int.from_bytes(body[0:4], 'big')
            height = int.from_bytes(body[4:8], 'big')
            ctype = body[9]
        elif kind == b'IDAT':
            idat += body
        pos += 12 + length
    raw = zlib.decompress(idat)
    bpp = {0: 1, 2: 3, 4: 2, 6: 4}[ctype]
    stride = width * bpp
    rows, prev, i = [], bytearray(stride), 0
    for _ in range(height):
        filt = raw[i]
        line = bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if filt == 1:
                line[x] = (line[x] + a) & 255
            elif filt == 2:
                line[x] = (line[x] + b) & 255
            elif filt == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif filt == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(bytes(line))
        prev = line
    return width, height, bpp, rows


def verify(path, width, height):
    """Boyut doğru mu ve çizim tuvalin tamamını kaplamış mı."""
    got_w, got_h, bpp, rows = read_png(path)
    if (got_w, got_h) != (width, height):
        sys.exit(f'{path}: {got_w}×{got_h} çıktı, {width}×{height} olmalıydı')
    for y, row in enumerate(rows):
        for x in range(0, got_w):
            if tuple(row[x * bpp:x * bpp + 3]) == MARKER:
                sys.exit(f'{path}: ({x},{y}) çizilmemiş — tuval kırpılmış')


def to_rgba(path):
    """PNG'yi 32 bit RGBA olarak yeniden yazar.

    Play uygulama simgesini 32 bit PNG olarak ister; Chromium çizim tamamen
    donuk olduğu için 24 bit RGB yazıyor. Öne çıkan görsel için tersi geçerli
    (alfa istenmiyor), o yüzden dönüşüm yalnızca simgeye uygulanır.
    """
    width, height, bpp, rows = read_png(path)
    if bpp == 4:
        return
    rgba = [bytes(b for x in range(width)
                  for b in (*row[x * bpp:x * bpp + 3], 255)) for row in rows]
    stride = width * 4
    # Satırlar birbirine çok benzediği için "Up" süzgeci düz baytlardan çok
    # daha iyi sıkışır; ilk satırın üstü yok, süzgeçsiz yazılır.
    raw = bytearray(b'\x00' + rgba[0])
    for y in range(1, height):
        raw += b'\x02' + bytes((rgba[y][i] - rgba[y - 1][i]) & 255 for i in range(stride))

    def chunk(kind, body):
        return (len(body).to_bytes(4, 'big') + kind + body
                + zlib.crc32(kind + body).to_bytes(4, 'big'))

    header = (width.to_bytes(4, 'big') + height.to_bytes(4, 'big')
              + bytes((8, 6, 0, 0, 0)))
    open(path, 'wb').write(
        b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header)
        + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b''))


def main():
    chrome = next((c for c in CHROME_CANDIDATES if c and os.path.exists(c)), None)
    if not chrome:
        sys.exit('Chromium bulunamadı; yolu REYON_CHROME ile ver')
    os.makedirs(OUT, exist_ok=True)
    background, transform, paths = icon_source()
    # Göreli yol: SVG depoda makineden bağımsız kalsın; sayfa da aynı klasörden açılıyor.
    font_url = os.path.relpath(FONT, OUT)
    print(f'simge kaynağı: {len(paths)} yol, işaret {MARK_BOX * transform[2]:.0f} birim, '
          f'zemin {background}')
    for name, svg, (w, h), rgba in (
        ('icon-512', icon_svg(background, transform, paths), (512, 512), True),
        ('feature-1024', feature_svg(background, transform, paths, font_url), (1024, 500), False),
    ):
        svg_path = os.path.join(OUT, f'{name}.svg')
        png_path = os.path.join(OUT, f'{name}.png')
        open(svg_path, 'w', encoding='utf-8').write(svg)
        render(chrome, svg, os.path.abspath(png_path), w, h, OUT)
        verify(png_path, w, h)
        if rgba:
            to_rgba(png_path)
        depth = read_png(png_path)[2] * 8
        print(f'{png_path}: {w}×{h}, {depth} bit, {os.path.getsize(png_path)} bayt')
    return 0


if __name__ == '__main__':
    sys.exit(main())
