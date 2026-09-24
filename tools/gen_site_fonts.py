#!/usr/bin/env python3
"""Sitenin yazı tiplerini uygulamanın IBM Plex dosyalarından üretir.

Hangi dosyanın hangi karakterleri taşıdığı site/assets/site.css'teki
@font-face kurallarında yazılı (unicode-range); betik oradan okur, her kural
için res/font'taki TTF'yi o aralığa indirip WOFF2 yazar. Tarayıcı bir dosyayı
ancak sayfada o aralıktan bir karakter varsa indirir: Latin sayfalar Kiril ve
Arapça dosyaları hiç çekmez.

Ad: Plex, OFL 1.1 altında "Plex" ayrılmış adıyla (Reserved Font Name)
dağıtılıyor; alt küme değiştirilmiş sürüm sayıldığı için bu ad taşınamaz.
Dosyalar "Reyon Sans", "Reyon Sans Arabic" ve "Reyon Mono" adını alır; telif
satırı ve lisans kayıtları olduğu gibi kalır, lisans metni yanına yazılır.

fontTools ve brotli ister (pip install fonttools brotli); CI'da koşmaz,
üretilen dosyalar depoda durur. tools/check_site.py sayfalardaki her
karakterin bu aralıklarda olduğunu denetler.

Kullanım: python3 tools/gen_site_fonts.py
"""
import os
import re
import sys

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSS = os.path.join(ROOT, "site", "assets", "site.css")
OUT = os.path.join(ROOT, "site", "assets", "fonts")
FONTS = os.path.join(ROOT, "app", "src", "main", "res", "font")
OFL = os.path.join(ROOT, "app", "src", "main", "res", "raw", "license_ofl.txt")

SOURCES = {
    ("Reyon Sans", "400"): "ibm_plex_sans_regular.ttf",
    ("Reyon Sans", "600"): "ibm_plex_sans_semibold.ttf",
    ("Reyon Sans Arabic", "400"): "ibm_plex_sans_arabic_regular.ttf",
    ("Reyon Sans Arabic", "600"): "ibm_plex_sans_arabic_semibold.ttf",
    ("Reyon Mono", "600"): "ibm_plex_mono_semibold.ttf",
}
# Ad tablosunda değişen kayıtlar: aile, tam ad, PostScript adı, tekil kimlik,
# tipografik aile. Telif (0), ticari marka (7) ve lisans (13, 14) kalır.
RENAMED_IDS = {1, 3, 4, 6, 16, 18, 20, 21, 25}
RENAMES = (
    ("IBM Plex Sans Arabic", "Reyon Sans Arabic"), ("IBMPlexSansArabic", "ReyonSansArabic"),
    ("IBM Plex Sans", "Reyon Sans"), ("IBMPlexSans", "ReyonSans"),
    ("IBM Plex Mono", "Reyon Mono"), ("IBMPlexMono", "ReyonMono"),
)
README = """Bu klasördeki yazı tipleri IBM Plex'ten (Copyright © 2017 IBM Corp., Reserved
Font Name "Plex") türetilmiş alt kümelerdir: tools/gen_site_fonts.py, sitenin
kullandığı karakter aralıklarını bırakıp WOFF2'ye çevirir. SIL Open Font
License 1.1'in ayrılmış ad kuralı gereği "Reyon Sans", "Reyon Sans Arabic" ve
"Reyon Mono" adını taşırlar. Lisans: OFL.txt (aynı lisans, değiştirilmeden).

These fonts are subsets of IBM Plex, renamed under the Reserved Font Name
clause of the SIL Open Font License 1.1. License: OFL.txt.
"""


def font_faces(css):
    """site.css'teki @font-face kuralları: (aile, ağırlık, dosya, kod noktaları)."""
    faces = []
    for block in re.findall(r"@font-face\s*\{(.*?)\}", css, re.S):
        family = re.search(r'font-family:\s*"([^"]+)"', block).group(1)
        weight = re.search(r"font-weight:\s*(\d+)", block).group(1)
        url = re.search(r'url\("fonts/([^"]+)"\)', block).group(1)
        points = set()
        for part in re.search(r"unicode-range:\s*([^;]+);", block).group(1).split(","):
            lo, _, hi = part.strip()[2:].partition("-")
            points.update(range(int(lo, 16), int(hi or lo, 16) + 1))
        faces.append((family, weight, url, points))
    return faces


def rename(font):
    table = font["name"]
    for record in table.names:
        if record.nameID not in RENAMED_IDS:
            continue
        text = record.toUnicode()
        for old, new in RENAMES:
            text = text.replace(old, new)
        record.string = text
    left = [r.nameID for r in table.names if r.nameID in RENAMED_IDS and "Plex" in r.toUnicode()]
    if left:
        sys.exit(f"ad tablosunda Plex kaldı: {sorted(set(left))}")


def main():
    os.makedirs(OUT, exist_ok=True)
    css = open(CSS, encoding="utf-8").read()
    total = 0
    for family, weight, name, points in font_faces(css):
        src = os.path.join(FONTS, SOURCES[(family, weight)])
        font = TTFont(src)
        options = subset.Options()
        options.flavor = "woff2"
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.name_languages = ["*"]
        options.notdef_outline = True
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=points & set(font.getBestCmap()))
        subsetter.subset(font)
        rename(font)
        out = os.path.join(OUT, name)
        font.flavor = "woff2"
        font.save(out)
        size = os.path.getsize(out)
        total += size
        print(f"{name}: {len(font.getBestCmap())} karakter, {size / 1024:.1f} KB")
    with open(os.path.join(OUT, "OFL.txt"), "w", encoding="utf-8") as f:
        f.write(open(OFL, encoding="utf-8").read())
    with open(os.path.join(OUT, "README.txt"), "w", encoding="utf-8") as f:
        f.write(README)
    print(f"toplam {total / 1024:.1f} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
