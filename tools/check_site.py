#!/usr/bin/env python3
"""Site denetimi. Depo kökünden koşar, CI her itmede çağırır.

Gizlilik sayfası Play'in istediği tek adres ve 14 dilin tamamını taşıyor;
uygulamadaki bağlantıların oraya düşmesi denetime bağlı. Denetimler:

  1. site/gizlilik.html, tools/gen_privacy.py çıktısıyla birebir aynı
     (üretilen dosya elle düzenlenince sessizce ayrışırdı)
  2. AppLocale.TAGS içindeki her dilin kendi bölümü ve iletişim adresi var
  3. Uygulamadaki bağlantılar (Links.SITE, Links.PRIVACY) sitenin alan adıyla
     aynı: yarım kalmış alan adı taşıması ölü bağlantı bırakır
  4. Sitede önceki depodan kalma marka izi yok

site/index.html şimdilik Türkçe ve İngilizce; kalan diller bilinen eksik,
uyarı verir.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_privacy  # noqa: E402

ROOT = gen_privacy.ROOT
LINKS = os.path.join(ROOT, "app", "src", "main", "kotlin", "com", "aripd",
                     "reyon", "platform", "Links.kt")
INDEX = os.path.join(ROOT, "site", "index.html")

errors = []
warnings = []


def host(url):
    return re.sub(r"^https?://", "", url).split("/")[0]


def main():
    tags = gen_privacy.app_tags()

    # 1. Üretilen sayfa ile depodaki dosya
    fresh = gen_privacy.page(tags)
    stored = open(gen_privacy.OUT, encoding="utf-8").read()
    if fresh != stored:
        errors.append("site/gizlilik.html üreticiyle ayrışmış; "
                      "python3 tools/gen_privacy.py ile yeniden üret")

    # 2. Dil kapsamı ve iletişim
    for tag in tags:
        block = re.search(rf'<section id="{tag}" lang="{tag}".*?</section>', stored, re.S)
        if not block:
            errors.append(f"gizlilik.html: {tag} bölümü yok")
        elif gen_privacy.MAIL not in block.group(0):
            errors.append(f"gizlilik.html: {tag} bölümünde iletişim adresi yok")
    print(f"gizlilik.html: {len(tags)} dil")

    # 3. Uygulamadaki bağlantılar sitenin alan adıyla aynı mı
    src = open(LINKS, encoding="utf-8").read()
    site = re.search(r'const val SITE = "([^"]+)"', src).group(1)
    privacy = re.search(r'const val PRIVACY = "([^"]+)"', src).group(1)
    if host(site) != host(privacy):
        errors.append(f"Links: SITE ({host(site)}) ile PRIVACY ({host(privacy)}) "
                      "aynı alan adında değil")
    for path, label in ((gen_privacy.OUT, "gizlilik.html"), (INDEX, "index.html")):
        page = open(path, encoding="utf-8").read()
        # Kendi barındırmamıza bakan bağlantılar: aripd.com alan adları ve
        # proje sayfası. GitHub depo bağlantıları buna girmez.
        own = re.findall(r'https?://(?:[a-z0-9.-]*aripd\.com|aripdcom\.github\.io)', page)
        for url in own:
            if host(url) != host(site):
                errors.append(f"{label}: {url} Links.SITE ({site}) ile uyuşmuyor")
    print(f"alan adı: {host(site)}")

    # 4. Önceki depodan kalma marka izi (Reyon ZA Games'ten ayrıldı)
    for path, label in ((gen_privacy.OUT, "gizlilik.html"), (INDEX, "index.html")):
        page = open(path, encoding="utf-8").read()
        for pattern, what in ((r"ZA Games", "ZA Games"), (r"\bZA\b(?![\w./-])", "tek başına \"ZA\""),
                              (r"zagames", "zagames")):
            hits = re.findall(pattern, page)
            if hits:
                errors.append(f"{label}: {what} {len(hits)} kez geçiyor")

    # index.html'in dil kapsamı
    index = open(INDEX, encoding="utf-8").read()
    index_langs = set(re.findall(r'lang="([a-z-]+)"', index))
    if len(index_langs) < len(tags):
        warnings.append(f"index.html {len(index_langs)} dilde, uygulama {len(tags)} dilde "
                        f"({', '.join(sorted(set(tags) - index_langs))} eksik)")

    print()
    for w in warnings:
        print(f"UYARI  {w}")
    for e in errors:
        print(f"HATA   {e}")
    print()
    if errors:
        print(f"✗ {len(errors)} hata, {len(warnings)} uyarı")
        return 1
    print(f"✓ hata yok, {len(warnings)} uyarı")
    return 0


if __name__ == "__main__":
    sys.exit(main())
