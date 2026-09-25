#!/usr/bin/env python3
"""Play ekran görüntülerini alfasız 24 bit PNG'ye çevirir.

Play telefon ekran görüntülerini JPEG ya da alfasız 24 bit PNG olarak ister;
Android'in `screencap -p` çıktısı ise 32 bit RGBA. Bu araç store/screenshots/
altındaki RGBA dosyaların alfa kanalını atıp aynı yere 24 bit RGB olarak yazar.
Renkler değişmez: saydam ya da yarı saydam piksel varsa dosyaya dokunmaz, hata
verir; yazdıktan sonra dosyayı geri okuyup her pikseli karşılaştırır. sRGB gibi
yardımcı parçalar korunur, sBIT'in alfa baytı atılır.

Biçimi tools/check_store.py denetler. Yeni ekran görüntüleri alındıktan sonra
bir kez koşar; zaten 24 bit olan dosyayı olduğu gibi bırakır.

Kullanım: python3 tools/store_screenshots.py
"""
import glob
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_store_graphics import png_chunk, read_png, write_png  # noqa: E402

SCREENSHOTS = 'store/screenshots'
# Başlık ve görüntü verisi yeniden yazılır; kalan parçalar olduğu gibi taşınır.
REWRITTEN = {b'IHDR', b'IDAT', b'IEND'}


def chunks(data):
    pos = 8
    while pos < len(data):
        length = int.from_bytes(data[pos:pos + 4], 'big')
        yield data[pos + 4:pos + 8], data[pos + 8:pos + 8 + length]
        pos += 12 + length


def to_rgb(path):
    """Dosyayı 24 bite çevirir; zaten 24 bitse False döner."""
    data = open(path, 'rb').read()
    header = next(body for kind, body in chunks(data) if kind == b'IHDR')
    depth, ctype, interlace = header[8], header[9], header[12]
    if (depth, ctype) == (8, 2):
        return False
    if (depth, ctype, interlace) != (8, 6, 0):
        sys.exit(f'{path}: 8 bit taramasız RGBA değil '
                 f'(bit {depth}, renk türü {ctype}, tarama {interlace})')
    width, height, _, rows = read_png(path)
    if any(row[3::4] != b'\xff' * width for row in rows):
        sys.exit(f'{path}: saydam piksel var; alfayı atmak görüntüyü değiştirir')
    rgb = []
    for row in rows:
        out = bytearray(width * 3)
        out[0::3], out[1::3], out[2::3] = row[0::4], row[1::4], row[2::4]
        rgb.append(bytes(out))
    extra = b''.join(png_chunk(kind, body[:3] if kind == b'sBIT' else body)
                     for kind, body in chunks(data) if kind not in REWRITTEN)
    write_png(path, width, height, 3, rgb, extra)
    if read_png(path) != (width, height, 3, rgb):
        open(path, 'wb').write(data)
        sys.exit(f'{path}: geri okunan pikseller tutmadı; dosya eski haline döndü')
    return True


def main():
    paths = sorted(glob.glob(os.path.join(SCREENSHOTS, '*', '*.png')))
    if not paths:
        sys.exit(f'{SCREENSHOTS}/ altında PNG yok')
    for path in paths:
        before = os.path.getsize(path)
        if to_rgb(path):
            print(f'{path}: 32 → 24 bit, {before} → {os.path.getsize(path)} bayt')
        else:
            print(f'{path}: zaten 24 bit')
    return 0


if __name__ == '__main__':
    sys.exit(main())
