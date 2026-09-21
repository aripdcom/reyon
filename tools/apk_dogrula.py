#!/usr/bin/env python3
"""Yayın paketlerinin doğrulaması. Etiketten sonra, Play'e yüklemeden önce.

`release.yml` imzalı APK ve AAB üretip `dist/` altına koyar. Bu betik o
klasörü, Android SDK'sı olmayan bir makinede de, yalnız standart kitaplıkla
denetler: indirilen dosyanın gerçekten beklenen paket olduğunu, imzalandığını
ve on dört dili taşıdığını gösterir.

`apksigner` imzayı *doğrular* (özetleri yeniden hesaplar); buradaki okuma
imza bloğunun varlığını ve şemasını görür. İkisi birbirinin yerine geçmez:
CI'da apksigner koşar, bu betik elde olan dosyaya bakar.

Denetimler:

  1. SHA256SUMS.txt'teki her dosya var ve özeti tutuyor
  2. APK imzalı: "APK Sig Block 42" içinde v2 şeması (0x7109871a)
  3. v1 kalıntısı yok (META-INF/*.RSA|DSA|EC|SF) — APK'lar yalnız v2 imzalı
  4. İkili manifest paket adını ve dosya adındaki sürümü taşıyor
  5. Manifestte tek bir uses-permission yok (gizlilik sözü)
  6. dex on dört dil etiketini taşıyor (AppLocale.TAGS)
  7. resources.arsc on üç çeviriyi taşıyor
  8. AAB gerçekten bir bundle ve on dört dilin kaynağı içinde — Play'in dil
     bölmesi kapalı olmasa uygulama içi dil seçicisi cihazda çalışmazdı

Kullanımı (depo kökünden):

    python3 tools/apk_dogrula.py            # dist/
    python3 tools/apk_dogrula.py <klasör>
"""
import hashlib
import os
import re
import struct
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PAKET = "com.aripd.reyon"
# AppLocale.TAGS ile aynı sıra; check_strings.py listeyi kaynakla karşılaştırır.
TAGS = ["en", "tr", "de", "fr", "nl", "es", "pt", "it", "da", "sv", "nb", "fi", "ru", "ar"]

SIG_MAGIC = b"APK Sig Block 42"
V2_ID = 0x7109871A
V3_ID = 0xF05368C0

errors = []
warnings = []


def u16(s):
    return s.encode("utf-16-le")


# ---------------------------------------------------------------------------
# 1. SHA256SUMS
# ---------------------------------------------------------------------------

def sha256sums(klasor):
    yol = os.path.join(klasor, "SHA256SUMS.txt")
    if not os.path.exists(yol):
        errors.append("SHA256SUMS.txt yok")
        return
    with open(yol) as fh:
        satirlar = [s for s in fh.read().splitlines() if s.strip()]
    if not satirlar:
        errors.append("SHA256SUMS.txt boş")
        return
    for satir in satirlar:
        beklenen, ad = satir.split(None, 1)
        # sha256sum ikili kipte adın önüne '*' koyar.
        ad = ad.strip().lstrip("*")
        hedef = os.path.join(klasor, ad)
        if not os.path.exists(hedef):
            errors.append(f"SHA256SUMS'ta listelenen {ad} klasörde yok")
            continue
        with open(hedef, "rb") as fh:
            okunan = hashlib.sha256(fh.read()).hexdigest()
        if okunan != beklenen:
            errors.append(f"{ad}: sha256 uyuşmadı (dosya bozuk ya da değiştirilmiş)")
    print(f"SHA256SUMS: {len(satirlar)} dosya")


# ---------------------------------------------------------------------------
# 2-3. İmza
# ---------------------------------------------------------------------------

def imza(yol, ad):
    """APK Signing Block'u okur; bulunan şema kimliklerini döndürür."""
    with open(yol, "rb") as fh:
        ham = fh.read()
    yer = ham.rfind(SIG_MAGIC)
    if yer < 0:
        errors.append(f"{ad}: imza bloğu yok — APK imzasız")
        return []
    # Blok: [boyut][id-değer çiftleri][boyut][magic]. Sondaki boyut magic'ten
    # hemen önce; baştaki ile aynı olmalı.
    boyut_sonu = struct.unpack_from("<Q", ham, yer - 8)[0]
    blok_basi = yer + 16 - 8 - boyut_sonu
    if blok_basi < 0:
        errors.append(f"{ad}: imza bloğu boyutu dosyaya sığmıyor")
        return []
    boyut_basi = struct.unpack_from("<Q", ham, blok_basi)[0]
    if boyut_basi != boyut_sonu:
        errors.append(f"{ad}: imza bloğu boyutu baş/son tutmuyor")
        return []

    idler = []
    p, son = blok_basi + 8, yer - 8
    while p < son - 8:
        uzunluk = struct.unpack_from("<Q", ham, p)[0]
        if uzunluk < 4 or p + 8 + uzunluk > son:
            break
        idler.append(struct.unpack_from("<I", ham, p + 8)[0])
        p += 8 + uzunluk

    if V2_ID not in idler:
        errors.append(f"{ad}: v2 imza şeması (0x7109871a) yok "
                      f"(bulunan: {', '.join(hex(i) for i in idler) or 'yok'})")
    if V3_ID in idler:
        warnings.append(f"{ad}: v3 imza şeması da var (0xf05368c0)")
    return idler


# ---------------------------------------------------------------------------
# 4-7. APK içeriği
# ---------------------------------------------------------------------------

def surum_dosya_adindan(ad):
    """reyon-v1.0.0.apk -> "1.0.0"; eşleşmezse None."""
    m = re.search(r"-v(\d+\.\d+\.\d+)\.apk$", ad)
    return m.group(1) if m else None


def apk_icerigi(yol, ad):
    with zipfile.ZipFile(yol) as z:
        adlar = z.namelist()

        v1 = [a for a in adlar if re.fullmatch(r"META-INF/.*\.(RSA|DSA|EC|SF)", a)]
        if v1:
            errors.append(f"{ad}: v1 imza kalıntısı var ({', '.join(v1)})")

        man = z.read("AndroidManifest.xml")
        if man[:4] != b"\x03\x00\x08\x00":
            errors.append(f"{ad}: AndroidManifest.xml ikili (AXML) değil")

        # aapt2 dizge havuzunu UTF-16LE ya da UTF-8 yazabilir; kodlama
        # başarısızlık sebebi değil, hangisi olduğu bilgidir.
        if u16(PAKET) in man:
            kodlama = "UTF-16LE"
        elif PAKET.encode() in man:
            kodlama = "UTF-8"
        else:
            kodlama = None
            errors.append(f"{ad}: manifestte paket adı {PAKET} yok")

        beklenen = surum_dosya_adindan(ad)
        if kodlama == "UTF-16LE":
            m = re.search(rb"(?:\d\x00){1,3}(?:\.\x00(?:\d\x00){1,2}){2}", man)
            surum = m.group().decode("utf-16-le") if m else None
        elif kodlama == "UTF-8":
            m = re.search(rb"\d{1,3}(?:\.\d{1,2}){2}", man)
            surum = m.group().decode() if m else None
        else:
            surum = None
        if surum is None:
            errors.append(f"{ad}: manifestte versionName bulunamadı")
        elif beklenen and surum != beklenen:
            errors.append(f"{ad}: versionName {surum}, dosya adı {beklenen} diyor")

        if u16("uses-permission") in man or b"uses-permission" in man:
            errors.append(f"{ad}: manifestte uses-permission var — izin sözü bozuldu")

        dex = b"".join(z.read(a) for a in adlar if a.endswith(".dex"))
        if not dex:
            errors.append(f"{ad}: dex yok")
        eksik = [t for t in TAGS if t.encode() + b"\x00" not in dex]
        if eksik:
            errors.append(f"{ad}: dex'te dil etiketi eksik ({', '.join(eksik)})")

        arsc = z.read("resources.arsc")
        yok = [t for t in TAGS if t != "en" and t.encode() not in arsc]
        if yok:
            errors.append(f"{ad}: resources.arsc'ta çeviri eksik ({', '.join(yok)})")

        print(f"{ad}: {os.path.getsize(yol):,} bayt · paket {kodlama or '?'} · "
              f"sürüm {surum or '?'} · dex {len(dex):,} B · arsc {len(arsc):,} B")


# ---------------------------------------------------------------------------
# 8. AAB
# ---------------------------------------------------------------------------

def aab_icerigi(yol, ad):
    with zipfile.ZipFile(yol) as z:
        adlar = z.namelist()
        if "BundleConfig.pb" not in adlar:
            errors.append(f"{ad}: BundleConfig.pb yok — bu bir AAB değil")
            return
        # Dil bölmesi kapalı (app/build.gradle.kts): on üç çevirinin kaynağı
        # bundle'ın içinde durmalı, yoksa uygulama içi dil seçicisi cihazda
        # boşa düşer.
        eksik = [t for t in TAGS if t != "en"
                 and not any(f"/values-{t}/" in a or a.endswith(f"/values-{t}.pb") for a in adlar)]
        if eksik:
            errors.append(f"{ad}: AAB'de dil kaynağı eksik ({', '.join(eksik)})")
        print(f"{ad}: {os.path.getsize(yol):,} bayt · {len(adlar)} girdi")


# ---------------------------------------------------------------------------

def main():
    klasor = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "dist")
    if not os.path.isdir(klasor):
        print(f"Klasör yok: {klasor}")
        print("Önce yayın dosyalarını indirin (release.yml artefaktı ya da GitHub Release).")
        return 2

    apkler = sorted(a for a in os.listdir(klasor) if a.endswith(".apk"))
    aabler = sorted(a for a in os.listdir(klasor) if a.endswith(".aab"))
    if not apkler:
        print(f"{klasor} içinde APK yok")
        return 2

    print(f"klasör: {klasor}")
    print()
    sha256sums(klasor)
    for ad in apkler:
        yol = os.path.join(klasor, ad)
        apk_icerigi(yol, ad)
        imza(yol, ad)
    for ad in aabler:
        aab_icerigi(os.path.join(klasor, ad), ad)

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
