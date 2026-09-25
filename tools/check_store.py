#!/usr/bin/env python3
"""Play listeleme metinlerini denetler: karakter sınırları, dil kapsamı, gizli karakterler.

Denetimler:
  * `store/play/<dil>/` altındaki üç dosya var mı ve sınırların içinde mi
  * dil klasörleri AppLocale.TAGS ile birebir mi (uygulamada olan dil mağazada da
    olmalı; mağazada olan dil uygulamada da olmalı)
  * her dilin Play yerel ayar karşılığı biliniyor mu (Play Console'a kopyalarken
    hangi ayarın seçileceği store/README.md'deki tabloda yazar)
  * sürüm notlarındaki dil blokları 500 karakteri geçmiyor ve etiketleri tanıdık
  * metinlerde görünmez karakter yok (yumuşak tire, sıfır genişlikli boşluk, BOM) —
    Play bunları olduğu gibi yayımlar, listelemede bozuk kelime olarak görünür
  * görseller Play'in istediği biçimde: simge 512×512 32 bit PNG, öne çıkan görsel
    1024×500 ve ekran görüntüleri alfasız 24 bit PNG (`screencap` 32 bit yazar,
    tools/store_screenshots.py çevirir)
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAY = os.path.join(ROOT, "store", "play")
LIMITS = {"title.txt": 30, "short.txt": 80, "full.txt": 4000}
NOTE_LIMIT = 500

# Depodaki dil kodu → Play Console'un yerel ayar kodu. Play bölge istediği için
# (tr-TR, pt-BR…) eşleme elle tutulur; Arapça'da Play bölgesiz "ar" kullanır.
PLAY_LOCALES = {
    "en": "en-US", "tr": "tr-TR", "de": "de-DE", "fr": "fr-FR", "nl": "nl-NL",
    "es": "es-ES", "pt": "pt-BR", "it": "it-IT", "da": "da-DK", "sv": "sv-SE",
    "nb": "no-NO", "fi": "fi-FI", "ru": "ru-RU", "ar": "ar",
}
INVISIBLE = {0x00AD: "yumuşak tire", 0x200B: "sıfır genişlikli boşluk", 0xFEFF: "BOM"}

# Play'in görsel şartları. Ekran görüntüsünde kenarlar 320–3840 px, uzun kenar kısanın
# en çok iki katı; telefon için 2–8 kare.
STORE = os.path.join(ROOT, "store")
MB = 1024 * 1024
RGB, RGBA = 2, 6
GRAPHICS = {"icon-512.png": ((512, 512), RGBA, 1 * MB),
            "feature-1024.png": ((1024, 500), RGB, 15 * MB)}
SHOT_BYTES, SHOT_SIDES, SHOT_COUNT = 8 * MB, (320, 3840), (2, 8)

errors, warnings = [], []


def app_languages():
    """AppLocale.TAGS listesi: uygulamanın gerçekten çevirisi olan dilleri."""
    path = os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "aripd", "reyon",
                        "platform", "AppLocale.kt")
    source = open(path, encoding="utf-8").read()
    block = re.search(r"val TAGS: List<String> = listOf\((.*?)\)", source, re.S)
    if not block:
        errors.append("AppLocale.TAGS okunamadı")
        return []
    return re.findall(r'"([a-z-]+)"', block.group(1))


def check_png(path, color_type, max_bytes):
    """PNG başlığını denetler; (genişlik, yükseklik) ya da None döner."""
    name = os.path.relpath(path, STORE)
    if not os.path.exists(path):
        errors.append(f"{name} yok")
        return None
    head = open(path, "rb").read(33)
    if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        errors.append(f"{name}: PNG değil")
        return None
    width, height = int.from_bytes(head[16:20], "big"), int.from_bytes(head[20:24], "big")
    depth, ctype = head[24], head[25]
    if (depth, ctype) != (8, color_type):
        want = "32 bit (alfalı)" if color_type == RGBA else "alfasız 24 bit"
        fix = ("tools/gen_store_graphics.py" if name.startswith("graphics")
               else "tools/store_screenshots.py")
        errors.append(f"{name}: bit derinliği {depth}, renk türü {ctype}; Play {want} PNG "
                      f"istiyor (python3 {fix})")
    if os.path.getsize(path) > max_bytes:
        errors.append(f"{name}: {os.path.getsize(path)} bayt, sınır {max_bytes}")
    return width, height


langs = sorted(d for d in os.listdir(PLAY)
               if os.path.isdir(os.path.join(PLAY, d)) and d != "release-notes")
for lang in langs:
    for name, limit in LIMITS.items():
        path = os.path.join(PLAY, lang, name)
        if not os.path.exists(path):
            errors.append(f"{lang}/{name} eksik")
            continue
        text = open(path, encoding="utf-8").read().strip()
        if not text:
            errors.append(f"{lang}/{name} boş")
            continue
        if len(text) > limit:
            errors.append(f"{lang}/{name}: {len(text)} karakter, sınır {limit}")
        for ch in set(text):
            if ord(ch) in INVISIBLE:
                errors.append(f"{lang}/{name}: görünmez karakter "
                              f"U+{ord(ch):04X} ({INVISIBLE[ord(ch)]})")
        print(f"{lang}/{name}: {len(text)}/{limit} ok")

app = app_languages()
for lang in app:
    if lang not in langs:
        errors.append(f"uygulamada {lang} var, store/play/{lang}/ yok "
                      "(o dilde çalışan uygulama İngilizce listeleme görür)")
for lang in langs:
    if lang not in app:
        errors.append(f"store/play/{lang}/ var, AppLocale.TAGS'te {lang} yok")
    if lang not in PLAY_LOCALES:
        errors.append(f"{lang} için Play yerel ayar karşılığı yok (tools/check_store.py)")

known_tags = set(PLAY_LOCALES.values())
for path in sorted(glob.glob(os.path.join(PLAY, "release-notes", "*.txt"))):
    name = os.path.basename(path)
    text = open(path, encoding="utf-8").read()
    tags = re.findall(r"<([a-zA-Z-]+)>", text)
    if not tags:
        errors.append(f"{name}: dil bloğu yok")
    for tag in tags:
        if f"</{tag}>" not in text:
            errors.append(f"{name}: <{tag}> kapanmamış")
            continue
        if tag not in known_tags:
            errors.append(f"{name}: tanınmayan dil etiketi <{tag}>")
        block = text.split(f"<{tag}>", 1)[1].split(f"</{tag}>", 1)[0].strip()
        if len(block) > NOTE_LIMIT:
            errors.append(f"{name} {tag}: {len(block)} karakter, sınır {NOTE_LIMIT}")
        print(f"{name} {tag}: {len(block)}/{NOTE_LIMIT} ok")

for name, (size, ctype, max_bytes) in GRAPHICS.items():
    got = check_png(os.path.join(STORE, "graphics", name), ctype, max_bytes)
    if got and got != size:
        errors.append(f"graphics/{name}: {got[0]}×{got[1]}, {size[0]}×{size[1]} olmalı")
    print(f"graphics/{name}: {size[0]}×{size[1]}, {'32' if ctype == RGBA else '24'} bit")

shots_root = os.path.join(STORE, "screenshots")
shot_langs = sorted(d for d in os.listdir(shots_root)
                    if os.path.isdir(os.path.join(shots_root, d)))
if "en" not in shot_langs:
    errors.append("store/screenshots/en/ yok (varsayılan listelemenin ekran görüntüleri)")
for lang in shot_langs:
    if lang not in langs:
        errors.append(f"store/screenshots/{lang}/ var, store/play/{lang}/ yok")
    shots = sorted(os.listdir(os.path.join(shots_root, lang)))
    if not SHOT_COUNT[0] <= len(shots) <= SHOT_COUNT[1]:
        errors.append(f"screenshots/{lang}: {len(shots)} kare, Play "
                      f"{SHOT_COUNT[0]}–{SHOT_COUNT[1]} kabul ediyor")
    sizes = set()
    for shot in shots:
        got = check_png(os.path.join(shots_root, lang, shot), RGB, SHOT_BYTES)
        if not got:
            continue
        short_side, long_side = sorted(got)
        if short_side < SHOT_SIDES[0] or long_side > SHOT_SIDES[1] or long_side > 2 * short_side:
            errors.append(f"screenshots/{lang}/{shot}: {got[0]}×{got[1]}; kenarlar "
                          f"{SHOT_SIDES[0]}–{SHOT_SIDES[1]} px, uzun kenar kısanın "
                          "en çok iki katı")
        sizes.add(f"{got[0]}×{got[1]}")
    print(f"screenshots/{lang}: {len(shots)} kare, {', '.join(sorted(sizes))}, 24 bit")

print()
print(f"{len(langs)} dil: {' '.join(langs)}")
for e in errors:
    print(f"HATA   {e}")
for w in warnings:
    print(f"UYARI  {w}")
print(f"\n{'✓ hata yok' if not errors else f'✗ {len(errors)} hata'}"
      f"{f', {len(warnings)} uyarı' if warnings else ''}")
sys.exit(1 if errors else 0)
