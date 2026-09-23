#!/usr/bin/env python3
"""Yayın paketlerinin doğrulaması.

`release.yml` imzalı APK ve AAB'yi `dist/` altına koyar ve bu betiği GitHub
Release oluşturulmadan önce orada koşar; etiketli koşumda bir hata sürümü
durdurur. İndirilen bir sürüm için elde de koşulabilir. Android SDK'sı
gerekmez, yalnız standart kitaplık.

`apksigner` imzayı *doğrular* (özetleri yeniden hesaplar); buradaki okuma imza
bloğunun varlığını ve şemasını görür, imzacının sertifika parmak izini basar.
İkisi birbirinin yerine geçmez: CI'da ikisi de koşar.

Denetimler:

  1. SHA256SUMS.txt'teki her dosya var ve özeti tutuyor
  2. APK imzalı: "APK Sig Block 42" içinde v2 şeması (0x7109871a); v2
     imzacısının sertifika SHA-256'sı basılır
  3. v1 kalıntısı yok (META-INF/*.RSA|DSA|EC|SF) — APK'lar yalnız v2 imzalı
  4. İkili manifest (AXML) çözümlenir: paket adı doğru, versionName dosya
     adındaki sürümle aynı
  5. Manifestte tek bir uses-permission yok (gizlilik sözü); varsa adları
     basılır. Kütüphaneler kendi manifestlerinden izin sokabilir: v1.0.0'a
     androidx.core'un DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'ı böyle girdi.
  6. dex on dört dil etiketini tam dizge olarak taşıyor (AppLocale.TAGS)
  7. resources.arsc her dilin kendi app_tagline çevirisini taşıyor
  8. AAB gerçekten bir bundle ve base/resources.pb her dilin çevirisini
     taşıyor — Play'in dil bölmesi kapalı olmasa uygulama içi dil seçicisi
     cihazda çalışmazdı

6-8'de dil kodunu çıplak aramak işe yaramıyor: iki harf ikili dosyada her yerde
geçer (v1.0.0'ın arsc'ında desteklenmeyen "ja" bile bulunuyordu). Bu yüzden dex'te
uzunluk önekli tam dizge, kaynak tablolarında her dilin kendi app_tagline metni
aranır. O metin her dilde farklı (AppLocaleTest) ve Hakkında ekranı ile paylaşım
kartı kullandığı için kaynak küçültme onu atmaz. AAB'de değerler dosya olarak
değil, resources.pb içinde derlenmiş durur.

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
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

PAKET = "com.aripd.reyon"
# AppLocale.TAGS ile aynı sıra; check_strings.py listeyi kaynakla karşılaştırır.
TAGS = ["en", "tr", "de", "fr", "nl", "es", "pt", "it", "da", "sv", "nb", "fi", "ru", "ar"]

SIG_MAGIC = b"APK Sig Block 42"
V2_ID = 0x7109871A
V3_ID = 0xF05368C0

errors = []
warnings = []


# ---------------------------------------------------------------------------
# Kaynak metinler: her dilin app_tagline'ı
# ---------------------------------------------------------------------------

def _aapt_unescape(s):
    """aapt2'nin kaçış çözümünün bu metinlere yeten kısmı (\\' \\" \\n \\t \\\\)."""
    out, i = [], 0
    while i < len(s):
        if s[i] == "\\" and i + 1 < len(s):
            out.append({"n": "\n", "t": "\t"}.get(s[i + 1], s[i + 1]))
            i += 2
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


def taglines():
    """{dil: app_tagline}; kaynak okunamazsa hata ve boş sözlük."""
    out = {}
    for t in TAGS:
        d = "values" if t == "en" else f"values-{t}"
        yol = os.path.join(RES, d, "strings.xml")
        try:
            kok = ET.parse(yol).getroot()
            metin = next(e.text for e in kok.findall("string") if e.get("name") == "app_tagline")
        except (OSError, ET.ParseError, StopIteration):
            errors.append(f"{d}/strings.xml'de app_tagline okunamadı; dil denetimi yapılamıyor")
            return {}
        out[t] = _aapt_unescape(metin)
    return out


def eksik_ceviriler(tablo, metinler):
    """Kaynak tablosunda (UTF-8 ya da UTF-16LE) bulunmayan dillerin listesi."""
    return [t for t, m in metinler.items()
            if m.encode("utf-8") not in tablo and m.encode("utf-16-le") not in tablo]


# ---------------------------------------------------------------------------
# İkili XML (AXML)
# ---------------------------------------------------------------------------

def _dizge_havuzu(buf, off):
    _, hsz, _, sayi, _, bayrak, bas, _ = struct.unpack_from("<HHIIIIII", buf, off)
    utf8 = bool(bayrak & 0x100)
    ofsetler = struct.unpack_from(f"<{sayi}I", buf, off + hsz)
    taban = off + bas
    havuz = []
    for o in ofsetler:
        p = taban + o
        if utf8:
            p += 2 if buf[p] & 0x80 else 1          # UTF-16 uzunluğu, atlanır
            n = buf[p]
            if n & 0x80:
                n = ((n & 0x7F) << 8) | buf[p + 1]
                p += 2
            else:
                p += 1
            havuz.append(buf[p:p + n].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", buf, p)[0]
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", buf, p)[0]
                p += 2
            havuz.append(buf[p:p + 2 * n].decode("utf-16-le", "replace"))
    return havuz, utf8


def axml(buf):
    """(havuz UTF-8 mi, [(öğe, {öznitelik: değer})]). Dizge dışı değer tamsayı döner."""
    havuz, utf8, ogeler = [], None, []
    off = 8
    while off + 8 <= len(buf):
        tip, _, boyut = struct.unpack_from("<HHI", buf, off)
        if boyut < 8:
            break
        if tip == 0x0001:
            havuz, utf8 = _dizge_havuzu(buf, off)
        elif tip == 0x0102:
            _, ad, abas, aboy, asayi = struct.unpack_from("<IIHHH", buf, off + 16)
            oz = {}
            a = off + 16 + abas
            for i in range(asayi):
                _, oad, ham, _, _, _, veri = struct.unpack_from("<IIIHBBI", buf, a + i * aboy)
                oz[havuz[oad]] = havuz[ham] if ham != 0xFFFFFFFF else veri
            ogeler.append((havuz[ad], oz))
        off += boyut
    return utf8, ogeler


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

def _uzunluk_onekli(b, o):
    n = struct.unpack_from("<I", b, o)[0]
    return b[o + 4:o + 4 + n], o + 4 + n


def _v2_sertifika(deger):
    """v2 bloğunun ilk imzacısının ilk sertifikası (DER)."""
    imzacilar, _ = _uzunluk_onekli(deger, 0)
    imzaci, _ = _uzunluk_onekli(imzacilar, 0)
    imzali, _ = _uzunluk_onekli(imzaci, 0)
    _, o = _uzunluk_onekli(imzali, 0)          # özetler
    sertifikalar, _ = _uzunluk_onekli(imzali, o)
    sertifika, _ = _uzunluk_onekli(sertifikalar, 0)
    return sertifika


def imza(yol, ad):
    """APK Signing Block'u okur; v2 sertifika parmak izini döndürür (yoksa None)."""
    with open(yol, "rb") as fh:
        ham = fh.read()
    yer = ham.rfind(SIG_MAGIC)
    if yer < 0:
        errors.append(f"{ad}: imza bloğu yok — APK imzasız")
        return None
    # Blok: [boyut][id-değer çiftleri][boyut][magic]. Sondaki boyut magic'ten
    # hemen önce; baştaki ile aynı olmalı.
    boyut_sonu = struct.unpack_from("<Q", ham, yer - 8)[0]
    blok_basi = yer + 16 - 8 - boyut_sonu
    if blok_basi < 0:
        errors.append(f"{ad}: imza bloğu boyutu dosyaya sığmıyor")
        return None
    if struct.unpack_from("<Q", ham, blok_basi)[0] != boyut_sonu:
        errors.append(f"{ad}: imza bloğu boyutu baş/son tutmuyor")
        return None

    bloklar = {}
    p, son = blok_basi + 8, yer - 8
    while p < son - 8:
        uzunluk = struct.unpack_from("<Q", ham, p)[0]
        if uzunluk < 4 or p + 8 + uzunluk > son:
            break
        bloklar[struct.unpack_from("<I", ham, p + 8)[0]] = ham[p + 12:p + 8 + uzunluk]
        p += 8 + uzunluk

    if V2_ID not in bloklar:
        errors.append(f"{ad}: v2 imza şeması (0x7109871a) yok "
                      f"(bulunan: {', '.join(hex(i) for i in bloklar) or 'yok'})")
        return None
    if V3_ID in bloklar:
        warnings.append(f"{ad}: v3 imza şeması da var (0xf05368c0)")
    try:
        h = hashlib.sha256(_v2_sertifika(bloklar[V2_ID])).hexdigest().upper()
    except (struct.error, IndexError):
        warnings.append(f"{ad}: v2 imzacısının sertifikası okunamadı")
        return None
    return ":".join(h[i:i + 2] for i in range(0, len(h), 2))


# ---------------------------------------------------------------------------
# 4-7. APK içeriği
# ---------------------------------------------------------------------------

def surum_dosya_adindan(ad):
    """reyon-v1.0.0.apk -> "1.0.0"; eşleşmezse None."""
    m = re.search(r"-v(\d+\.\d+\.\d+)\.apk$", ad)
    return m.group(1) if m else None


def _uleb128(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        out.append(b | (0x80 if n else 0))
        if not n:
            return bytes(out)


def apk_icerigi(yol, ad, metinler):
    with zipfile.ZipFile(yol) as z:
        adlar = z.namelist()

        v1 = [a for a in adlar if re.fullmatch(r"META-INF/.*\.(RSA|DSA|EC|SF)", a)]
        if v1:
            errors.append(f"{ad}: v1 imza kalıntısı var ({', '.join(v1)})")

        man = z.read("AndroidManifest.xml")
        if man[:4] != b"\x03\x00\x08\x00":
            errors.append(f"{ad}: AndroidManifest.xml ikili (AXML) değil")
            return
        utf8, ogeler = axml(man)
        kok = next((oz for oge, oz in ogeler if oge == "manifest"), {})
        paket, surum, kod = kok.get("package"), kok.get("versionName"), kok.get("versionCode")
        if paket != PAKET:
            errors.append(f"{ad}: paket adı {paket!r}, beklenen {PAKET}")
        beklenen = surum_dosya_adindan(ad)
        if not isinstance(surum, str):
            errors.append(f"{ad}: manifestte versionName yok")
        elif beklenen and surum != beklenen:
            errors.append(f"{ad}: versionName {surum}, dosya adı {beklenen} diyor")

        izinler = [oz.get("name", "?") for oge, oz in ogeler if oge == "uses-permission"]
        if izinler:
            errors.append(f"{ad}: manifestte uses-permission var — izin sözü bozuldu: "
                          f"{', '.join(map(str, izinler))}")

        dex = b"".join(z.read(a) for a in adlar if a.endswith(".dex"))
        if not dex:
            errors.append(f"{ad}: dex yok")
        # dex dizgesi: ULEB128 uzunluk + MUTF-8 bayt + NUL. Önek, "hidden\0" gibi
        # başka dizgelerin sonundaki "en\0"ı dil etiketi sanmayı önler.
        eksik = [t for t in TAGS if _uleb128(len(t)) + t.encode() + b"\x00" not in dex]
        if eksik:
            errors.append(f"{ad}: dex'te dil etiketi eksik ({', '.join(eksik)})")

        arsc = z.read("resources.arsc")
        if metinler:
            yok = eksik_ceviriler(arsc, metinler)
            if yok:
                errors.append(f"{ad}: resources.arsc'ta çeviri eksik ({', '.join(yok)})")

        print(f"{ad}: {os.path.getsize(yol):,} bayt · {paket} {surum} ({kod}) · "
              f"manifest {'UTF-8' if utf8 else 'UTF-16LE'} · dex {len(dex):,} B · "
              f"arsc {len(arsc):,} B · izin {len(izinler)}")


# ---------------------------------------------------------------------------
# 8. AAB
# ---------------------------------------------------------------------------

def aab_icerigi(yol, ad, metinler):
    with zipfile.ZipFile(yol) as z:
        adlar = z.namelist()
        if "BundleConfig.pb" not in adlar:
            errors.append(f"{ad}: BundleConfig.pb yok — bu bir AAB değil")
            return
        if "base/resources.pb" not in adlar:
            errors.append(f"{ad}: base/resources.pb yok")
            return
        pb = z.read("base/resources.pb")
        if metinler:
            yok = eksik_ceviriler(pb, metinler)
            if yok:
                errors.append(f"{ad}: AAB'de çeviri eksik ({', '.join(yok)})")
        print(f"{ad}: {os.path.getsize(yol):,} bayt · {len(adlar)} girdi · "
              f"resources.pb {len(pb):,} B")


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
    metinler = taglines()
    sha256sums(klasor)
    for ad in apkler:
        yol = os.path.join(klasor, ad)
        apk_icerigi(yol, ad, metinler)
        parmak = imza(yol, ad)
        if parmak:
            print(f"  imza v2 · sertifika SHA-256 {parmak}")
    for ad in aabler:
        aab_icerigi(os.path.join(klasor, ad), ad, metinler)

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
