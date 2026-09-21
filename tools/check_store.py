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

print()
print(f"{len(langs)} dil: {' '.join(langs)}")
for e in errors:
    print(f"HATA   {e}")
for w in warnings:
    print(f"UYARI  {w}")
print(f"\n{'✓ hata yok' if not errors else f'✗ {len(errors)} hata'}"
      f"{f', {len(warnings)} uyarı' if warnings else ''}")
sys.exit(1 if errors else 0)
