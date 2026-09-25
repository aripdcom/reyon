#!/usr/bin/env python3
"""Site denetimi. Depo kökünden koşar, CI her itmede çağırır.

Site tools/gen_site.py'nin çıktısı; uygulamanın 14 dilinin her biri kendi
ana sayfasında ve kendi gizlilik sayfasında (privacy.html; İngilizcesi Play'deki
adres). Denetimler:

  1. site/ altındaki her üretilen dosya üreticinin çıktısıyla birebir aynı;
     üreticinin bilmediği dosya yok (elle yazılan CSS, JS ve yazı tipleri hariç).
     Metin uygulamadan ve store/play'den geliyor: onlar değişip site yeniden
     üretilmezse burada düşer
  2. AppLocale.TAGS'teki her dilin ana sayfası ve gizlilik sayfası var,
     lang/dir doğru, her sayfa kendi türünün bütün dillerine hreflang ile
     bağlı; gizlilik sayfasında o dilin politikası ve iletişim adresi var
  3. Uygulamadaki bağlantılar (Links.SITE, Links.PRIVACY) sitenin alan adıyla
     aynı: yarım kalmış alan adı taşıması ölü bağlantı bırakır
  4. Sitede önceki depodan kalma marka izi yok
  5. İç bağlantılar ve çapalar bir dosyaya/öğeye varıyor; sayfalar dışarıdan
     kaynak yüklemiyor (yazı tipi, betik, stil, görsel hepsi sitede)
  6. Sayfalardaki her karakter site.css'teki yazı tiplerinin aralığında;
     değilse tarayıcı başka bir yazı tipine düşer (uyarı)
"""
import html
import os
import re
import sys
import unicodedata

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_site  # noqa: E402

SITE = gen_site.SITE
CSS = os.path.join(SITE, "assets", "site.css")
HAND_WRITTEN = {"assets/site.css", "assets/site.js"}
FONT_DIR = "assets/fonts/"

errors = []
warnings = []


def host(url):
    return re.sub(r"^https?://", "", url).split("/")[0]


def read(path):
    return open(os.path.join(SITE, path), encoding="utf-8").read()


def site_files():
    out = set()
    for folder, _, files in os.walk(SITE):
        for name in files:
            out.add(os.path.relpath(os.path.join(folder, name), SITE).replace(os.sep, "/"))
    return out


def check_generated(files):
    """1. Üretici çıktısı ile depo."""
    present = site_files()
    stale = []
    for path, content in files.items():
        full = os.path.join(SITE, path)
        if not os.path.exists(full):
            errors.append(f"site/{path} yok; python3 tools/gen_site.py ile üret")
            continue
        stored = open(full, "rb").read()
        fresh = content if isinstance(content, bytes) else content.encode("utf-8")
        if stored != fresh:
            stale.append(path)
    if stale:
        errors.append(f"site üreticiyle ayrışmış ({', '.join(stale[:6])}"
                      f"{' …' if len(stale) > 6 else ''}); python3 tools/gen_site.py ile yeniden üret")
    unknown = sorted(p for p in present - set(files) - HAND_WRITTEN if not p.startswith(FONT_DIR))
    if unknown:
        errors.append(f"üreticinin bilmediği dosya: {', '.join(unknown)}")
    for path in sorted(HAND_WRITTEN):
        if path not in present:
            errors.append(f"site/{path} yok")
    print(f"üretilen: {len(files)} dosya, elle yazılan: {len(HAND_WRITTEN)}, "
          f"yazı tipi: {len([p for p in present if p.startswith(FONT_DIR)])}")


def check_languages(tags, pages):
    """2. Dil kapsamı, lang/dir, hreflang; her dilin gizlilik sayfası."""
    for page_name in ("index.html", gen_site.PRIVACY):
        for tag in tags:
            path = page_name if tag == "en" else f"{tag}/{page_name}"
            page = pages.get(path)
            if page is None:
                errors.append(f"{tag} dilinin sayfası üretilmiyor ({path})")
                continue
            m = re.search(r"<html([^>]*)>", page)
            attrs = m.group(1) if m else ""
            if f'lang="{tag}"' not in attrs:
                errors.append(f"{path}: <html lang=\"{tag}\"> değil")
            if (tag in gen_site.RTL) != ('dir="rtl"' in attrs):
                errors.append(f"{path}: yazı yönü yanlış")
            alternates = dict(re.findall(r'<link rel="alternate" hreflang="([^"]+)" href="([^"]+)"', page))
            missing = (set(tags) | {"x-default"}) - set(alternates)
            if missing:
                errors.append(f"{path}: hreflang eksik ({', '.join(sorted(missing))})")
            wrong = [x for x, url in alternates.items() if not url.endswith("/" + page_name.replace("index.html", ""))]
            if wrong:
                errors.append(f"{path}: hreflang başka türden sayfaya bakıyor ({', '.join(sorted(wrong))})")
            if page_name == gen_site.PRIVACY:
                policy = gen_site.gen_privacy.POLICY[tag]
                if gen_site.MAIL not in page:
                    errors.append(f"{path}: iletişim adresi yok")
                if f"<h1>{policy['title']}</h1>" not in page.replace("\u00ad", ""):
                    errors.append(f"{path}: {tag} politikasının başlığı yok")
    print(f"diller: {len(tags)} ana sayfa, {len(tags)} gizlilik sayfası")


def check_domain(pages):
    """3. Uygulamadaki bağlantılar ve sitedeki kendi adreslerimiz aynı alan adında."""
    links = gen_site.links()
    site, privacy = links["SITE"], links["PRIVACY"]
    if host(site) != host(privacy):
        errors.append(f"Links: SITE ({host(site)}) ile PRIVACY ({host(privacy)}) aynı alan adında değil")
    if privacy != f"{site.rstrip('/')}/{gen_site.PRIVACY}":
        errors.append(f"Links.PRIVACY ({privacy}) sitedeki {gen_site.PRIVACY}'e bakmıyor")
    for path, page in pages.items():
        # Kendi barındırmamıza bakan bağlantılar: aripd.com alan adları ve
        # proje sayfası. GitHub depo bağlantıları buna girmez.
        for url in re.findall(r'https?://(?:[a-z0-9.-]*aripd\.com|aripdcom\.github\.io)', page):
            if host(url) != host(site):
                errors.append(f"{path}: {url} Links.SITE ({site}) ile uyuşmuyor")
    print(f"alan adı: {host(site)}")


def check_brand(pages):
    """4. Önceki depodan kalma marka izi (Reyon ZA Games'ten ayrıldı)."""
    for path, page in pages.items():
        for pattern, what in ((r"ZA Games", "ZA Games"), (r"\bZA\b(?![\w./-])", "tek başına \"ZA\""),
                              (r"zagames", "zagames")):
            hits = re.findall(pattern, page)
            if hits:
                errors.append(f"{path}: {what} {len(hits)} kez geçiyor")


def ids(page):
    return set(re.findall(r'\sid="([^"]+)"', page))


def check_links(pages):
    """5. İç bağlantılar, çapalar ve dış kaynak yüklemesi."""
    checked = 0
    for path, page in pages.items():
        base = os.path.dirname(path)
        for tag, body in re.findall(r"<(\w+)\b([^>]*)>", page):
            attrs = dict(re.findall(r'\s([\w:-]+)="([^"]*)"', body))
            url = attrs.get("href") or attrs.get("src")
            if not url:
                continue
            external = re.match(r"[a-z]+:|//", url)
            if external:
                # Yalnızca bağlantı (a) ve dil/kanonik işaretleri dışarı bakabilir;
                # stil, betik, yazı tipi, simge ve görsel sitenin kendisinden gelir.
                loads = tag in ("script", "img", "source", "iframe", "video", "audio") or (
                    tag == "link" and attrs.get("rel") not in ("canonical", "alternate"))
                if loads and url.startswith(("http:", "https:", "//")):
                    errors.append(f"{path}: dışarıdan kaynak yükleniyor: {url}")
                continue
            target, _, frag = url.partition("#")
            target = target.split("?")[0]
            if target == "":
                target_path = path
            elif target.startswith("/"):
                target_path = target[1:]
            else:
                target_path = os.path.normpath(os.path.join(base, target)).replace(os.sep, "/")
                if target.endswith("/"):
                    target_path += "/"
            if target_path in ("", ".", "./") or target_path.endswith("/"):
                target_path = target_path.rstrip("./") + ("/" if target_path.rstrip("./") else "") + "index.html"
            full = os.path.join(SITE, target_path)
            if not os.path.isfile(full):
                errors.append(f"{path}: kırık bağlantı {url}")
                continue
            if frag and target_path.endswith(".html"):
                target_page = pages.get(target_path) or read(target_path)
                if frag not in ids(target_page):
                    errors.append(f"{path}: {url} çapası yok")
            checked += 1
    css = open(CSS, encoding="utf-8").read()
    for url in re.findall(r'url\("([^"]+)"\)', css):
        if re.match(r"[a-z]+:", url):
            errors.append(f"site.css: dışarıdan kaynak: {url}")
        elif not os.path.isfile(os.path.join(SITE, "assets", url)):
            errors.append(f"site.css: {url} yok")
    print(f"bağlantılar: {checked} iç bağlantı ve çapa")


def font_ranges():
    """site.css'teki @font-face aralıklarının birleşimi."""
    css = open(CSS, encoding="utf-8").read()
    points = set()
    for block in re.findall(r"@font-face\s*\{(.*?)\}", css, re.S):
        rng = re.search(r"unicode-range:\s*([^;]+);", block)
        if not rng:
            continue
        for part in rng.group(1).split(","):
            lo, _, hi = part.strip()[2:].partition("-")
            points.update(range(int(lo, 16), int(hi or lo, 16) + 1))
    return points


def visible_text(page):
    page = re.sub(r"<(script|style)\b.*?</\1>", " ", page, flags=re.S)
    attrs = " ".join(re.findall(r'\s(?:aria-label|alt|title|content)="([^"]*)"', page))
    text = re.sub(r"<[^>]+>", " ", page)
    return html.unescape(text + " " + attrs)


def check_fonts(pages):
    """6. Yazı tipi kapsamı."""
    covered = font_ranges()
    for path, page in pages.items():
        if not path.endswith(".html"):
            continue
        missing = sorted({ch for ch in visible_text(page)
                          if ord(ch) not in covered and not ch.isspace()
                          and unicodedata.category(ch) not in ("Cc", "Cf")})
        if missing:
            warnings.append(f"{path}: yazı tipinde olmayan karakter: "
                            + " ".join(f"{c} (U+{ord(c):04X})" for c in missing[:12]))
    print(f"yazı tipi: {len(covered)} kod noktası")


def main():
    tags = gen_site.TAGS
    files = gen_site.build()
    check_generated(files)
    pages = {p: c for p, c in files.items() if p.endswith(".html")}
    check_languages(tags, pages)
    check_domain(pages)
    check_brand(pages)
    check_links(pages)
    check_fonts(pages)

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
