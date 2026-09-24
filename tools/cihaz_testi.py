#!/usr/bin/env python3
"""Cihaz üstü ölçümler (adb).

Birim testleri kuralları doğrular; bu betik uygulamanın gerçek telefonda nasıl
davrandığını ölçer: yerleşimin dp karşılığı, kare hızı, kare bütçesinin fazlara
dağılımı, dokunma hassasiyeti, ekran okuyucu etiketleri. Kullanımı ve eşikler:
docs/cihaz-testi.md

    python3 tools/cihaz_testi.py kurulu_yapi                  # sürüm + APK sha256
    python3 tools/cihaz_testi.py reyon --ekran 360x640        # dört modun yerleşimi
    python3 tools/cihaz_testi.py kare --sure 15
    python3 tools/cihaz_testi.py fazlar
    python3 tools/cihaz_testi.py erisim
    python3 tools/cihaz_testi.py alan
    python3 tools/cihaz_testi.py surukle --y 1500 --mesafeler 20,40,80,160

Gereksinim: adb (ANDROID_HOME/platform-tools ya da PATH), Pillow, numpy.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import statistics
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

import numpy as np
from PIL import Image

PAKET = "com.aripd.reyon"


# ---------------------------------------------------------------------------
# adb
# ---------------------------------------------------------------------------

def adb_yolu() -> str:
    for kok in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
                os.path.expanduser("~/Android/Sdk")):
        if kok:
            aday = os.path.join(kok, "platform-tools", "adb")
            if os.path.exists(aday):
                return aday
    bulunan = shutil.which("adb")
    if bulunan:
        return bulunan
    sys.exit("adb bulunamadı: ANDROID_HOME tanımlayın ya da adb'yi PATH'e ekleyin.")


ADB = adb_yolu()


def adb(*args: str) -> str:
    return subprocess.run([ADB, *args], capture_output=True, text=True).stdout


def kabuk(komut: str) -> str:
    return adb("shell", komut)


def cihaz_var() -> None:
    satirlar = [s for s in adb("devices").splitlines()[1:] if s.strip()]
    bagli = [s for s in satirlar if s.endswith("device")]
    if not bagli:
        sys.exit("Bağlı cihaz yok (adb devices boş). USB hata ayıklamayı açın.")
    if len(bagli) > 1:
        sys.exit(f"Birden çok cihaz bağlı; birini bırakın:\n" + "\n".join(bagli))


def kurulu_yapi(paket: str) -> str:
    """Ölçümün hangi yapıda alındığını tek satırda döndürür.

    Yanlış daldan kurulmuş bir yapıyla alınan ölçüm, doğru yapı sanıldığı
    sürece sahte bulgu üretir; bir kez yaşandı (G4). Sürüm adı tek başına
    ayırt etmez: sürüm yükseltildikten sonra her dalın yapısı aynı adı
    taşır. Ayırt eden, kurulu base.apk'nin özeti — yayındaki APK'nin
    SHA256'sıyla birebir aynı olmalı, çünkü pm install dosyayı olduğu gibi
    kopyalar.
    """
    surum = ""
    for satir in kabuk(f"dumpsys package {paket}").splitlines():
        s = satir.strip()
        if s.startswith("versionName="):
            surum = s.split("=", 1)[1].strip()
            break
    ozet = ""
    yollar = [s.split(":", 1)[1].strip()
              for s in kabuk(f"pm path {paket}").splitlines()
              if s.startswith("package:")]
    if yollar:
        ham = kabuk(f"sha256sum {yollar[0]}").split()
        if ham and len(ham[0]) == 64 and all(c in "0123456789abcdef" for c in ham[0]):
            ozet = ham[0]
    if not yollar:
        return f"{paket} kurulu değil"
    return (f"{paket} {surum or '(sürüm okunamadı)'} "
            f"sha256={ozet or '(okunamadı)'}")


def ekran_al(hedef: str) -> str:
    kabuk("screencap -p /sdcard/za_test.png")
    adb("pull", "/sdcard/za_test.png", hedef)
    return hedef


# ---------------------------------------------------------------------------
# Görüntü ölçümü
# ---------------------------------------------------------------------------

def sprite_x(yol: str, y0: int, y1: int, esik: int = 170, en_az_genislik: int = 22):
    """[y0,y1) bandındaki en geniş bitişik parlak sütun kümesinin merkezi (px).

    Oyuncu gemisi/aracı geniş ve bitişiktir; yıldız ve kıvılcım gibi küçük
    parlak noktalar ``en_az_genislik`` ile elenir. Bulunamazsa None.
    """
    im = np.asarray(Image.open(yol).convert("RGB")).astype(float)
    parlaklik = im[y0:y1].mean(axis=2)
    sutun = (parlaklik > esik).sum(axis=0).astype(float)
    acik = sutun > 0
    kumeler, i, n = [], 0, len(acik)
    while i < n:
        if acik[i]:
            j = i
            while j + 1 < n and acik[j + 1]:
                j += 1
            kumeler.append((i, j))
            i = j + 1
        else:
            i += 1
    kumeler = [k for k in kumeler if k[1] - k[0] + 1 >= en_az_genislik]
    if not kumeler:
        return None
    lo, hi = max(kumeler, key=lambda k: sutun[k[0]:k[1] + 1].sum())
    dilim, indeks = sutun[lo:hi + 1], np.arange(lo, hi + 1)
    return float((dilim * indeks).sum() / dilim.sum())


def alan_sinirlari(yol: str, satir: int | None = None):
    """Tuvalin yatay sınırlarını arka plan parlaklık sıçramasından bulur."""
    im = np.asarray(Image.open(yol).convert("RGB")).astype(float)
    satir = satir if satir is not None else im.shape[0] // 2
    parlaklik = im[satir].mean(axis=1)
    fark = np.abs(np.diff(parlaklik))
    kenar = [i for i in range(len(fark)) if fark[i] > 3]
    if len(kenar) < 2:
        return None
    return kenar[0] + 1, kenar[-1]


# ---------------------------------------------------------------------------
# Arayüz gezinme (uiautomator)
# ---------------------------------------------------------------------------

def tr_kucuk(s: str) -> str:
    """Türkçe duyarlı küçük harf: İ→i, I→ı."""
    return s.replace("\u0130", "i").replace("I", "\u0131").lower()


def arayuz(hepsi: bool = False) -> list[dict]:
    """Ekrandaki öğeler: etiket ve dokunma koordinatı.

    Varsayılan olarak yalnızca etiketli öğeler döner (gezinme bunları kullanır);
    [hepsi] ile etiketsiz düğümler de eklenir (erişilebilirlik taraması için).
    """
    for _ in range(3):
        if "dumped" in kabuk("uiautomator dump /sdcard/za_ui.xml"):
            break
        time.sleep(0.7)
    else:
        return []
    with tempfile.TemporaryDirectory() as gecici:
        yol = os.path.join(gecici, "ui.xml")
        adb("pull", "/sdcard/za_ui.xml", yol)
        try:
            kok = ET.parse(yol).getroot()
        except (ET.ParseError, FileNotFoundError):
            return []
    ogeler = []
    for d in kok.iter("node"):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", d.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        etiket = (d.get("text") or "").strip() or (d.get("content-desc") or "").strip()
        tiklanir = d.get("clickable") == "true"
        # Görünmeyen öğeler [0,0][0,0] sınırıyla gelir; dokunulursa ekranın
        # köşesine basılır ve gezinme sessizce yanlış yere gider. Erişilebilirlik
        # taraması için yine de sayılırlar: gizli sistem çubuğunun bölgesine
        # çizilen düğmeler bazı cihazlarda böyle gelir ve ekran okuyucu onlara
        # inemez (docs/cihaz-testi.md, Reyon Sipariş bulgu 3).
        if x2 - x1 < 2 or y2 - y1 < 2:
            if hepsi and tiklanir:
                ogeler.append({"t": etiket, "cx": 0, "cy": 0, "x1": 0, "y1": 0, "x2": 0, "y2": 0,
                               "tik": True, "sinirsiz": True})
            continue
        if etiket or (hepsi and tiklanir):
            ogeler.append({"t": etiket, "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2,
                           "x1": x1, "y1": y1, "x2": x2, "y2": y2, "tik": tiklanir})
    return ogeler


def dokun(oge: dict) -> None:
    kabuk(f"input tap {oge['cx']} {oge['cy']}")
    time.sleep(1.2)


def ekran_px() -> tuple[int, int]:
    """Ekranın o anki piksel ölçüsü; geçici (override) ölçü varsa o geçerlidir."""
    cikti = kabuk("wm size")
    gecici = re.search(r"Override size:\s*(\d+)x(\d+)", cikti)
    fiziksel = re.search(r"Physical size:\s*(\d+)x(\d+)", cikti)
    m = gecici or fiziksel
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def kaydir() -> None:
    # Koordinatlar ekrandan türetilir: `wm size` ile küçültülmüş ekranda sabit
    # pikseller ekranın dışına düşüyor ve kaydırma hiç olmuyordu.
    g, y = ekran_px()
    kabuk(f"input swipe {g // 2} {int(y * 0.75)} {g // 2} {int(y * 0.38)} 400")
    time.sleep(1.6)   # savrulma otursun; erken okuma kaymış koordinat verir


def uygulamayi_ac(paket: str) -> None:
    """Uygulamayı baştan açar. Açılış Görevler ekranı."""
    # Uzun koşumda ekran uyursa okumalar kilit ekranını görür.
    kabuk("input keyevent KEYCODE_WAKEUP")
    kabuk("wm dismiss-keyguard")
    time.sleep(0.5)
    kabuk(f"am force-stop {paket}")
    kabuk(f"am start -n {paket}/.MainActivity")
    time.sleep(2.5)


# ---------------------------------------------------------------------------
# Komutlar
# ---------------------------------------------------------------------------

def derleme_uyarisi(paket: str) -> None:
    """Kare ölçümü derlenmemiş yapıda anlamsız: adb ile kurulan APK JIT ile çalışır.

    Play'den kurulan uygulama başlangıç profiliyle önceden derlenir; adb ile kurulan
    `status=verify` kalır ve yeniden oluşturma kareleri iki kata yakın uzar
    (docs/cihaz-testi.md, 1.1.0 B). Ölçümden önce:
    `adb shell cmd package compile -m speed-profile -f com.aripd.reyon`
    """
    satir = next((s for s in kabuk("dumpsys package dexopt").split(paket, 1)[-1].splitlines() if "status=" in s), "")
    m = re.search(r"status=([\w-]+)", satir)
    durum = m.group(1) if m else "?"
    if durum not in ("speed-profile", "speed", "everything"):
        print(f"UYARI: {paket} derlenmemiş (status={durum}); kare süreleri Play kurulumunu temsil etmez.")
        print(f"       Önce: adb shell cmd package compile -m speed-profile -f {paket}\n")


def komut_kare(args) -> None:
    """Kare hızı ve takılma (jank) oranı; oyun OYNANIRKEN çağrılmalı."""
    derleme_uyarisi(args.paket)
    kabuk(f"dumpsys gfxinfo {args.paket} reset")
    kabuk(f"sleep {args.sure}")
    cikti = kabuk(f"dumpsys gfxinfo {args.paket}")
    ilgi = ("Total frames rendered", "Janky frames:", "50th percentile",
            "90th percentile", "99th percentile", "Number Missed Vsync",
            "50th gpu percentile", "90th gpu percentile")
    satirlar = [s.strip() for s in cikti.splitlines() if s.strip().startswith(ilgi)]
    if not satirlar:
        sys.exit(f"gfxinfo boş: {args.paket} ön planda ve çiziyor mu?")
    for s in satirlar:
        print(s)
    kare = next((s for s in satirlar if s.startswith("Total frames")), "")
    sayi = int(kare.split(":")[1]) if ":" in kare else 0
    if sayi == 0:
        print("\nUYARI: 0 kare çizildi — oyun duraklamış ya da bitmiş olabilir.")
    else:
        print(f"\nortalama ≈ {sayi / args.sure:.1f} kare/s ({args.sure} s pencere)")


def komut_fazlar(args) -> None:
    """Kare bütçesinin fazlara dağılımı: darboğaz CPU'da mı GPU'da mı?

    ``gfxinfo`` yalnızca toplamı verir; hangi aşamanın pahalı olduğunu
    ``framestats`` söyler. Sütun düzeni ROM'a göre değişir, bu yüzden
    başlık satırından ad-indeks eşlemesi çıkarılır (sabit sütun numarası
    varsaymak yanlış sonuç verir).
    """
    ham = kabuk(f"dumpsys gfxinfo {args.paket} framestats")
    satirlar = [l.strip() for l in ham.splitlines() if l.strip()]
    baslik = next((l for l in satirlar if l.startswith("Flags,")), None)
    if baslik is None:
        sys.exit("framestats boş: oyun ön planda ve çiziyor mu?")
    adlar = [c for c in baslik.split(",") if c]
    yer = {ad: i for i, ad in enumerate(adlar)}
    kareler = []
    for l in satirlar:
        if not l[0].isdigit():
            continue
        p = [x for x in l.split(",") if x != ""]
        if len(p) != len(adlar):
            continue
        v = [int(x) for x in p]
        if v[0] == 0:            # yalnızca normal kareler
            kareler.append(v)
    if not kareler:
        sys.exit("framestats'ta geçerli kare yok.")

    def faz(a: str, b: str):
        if a not in yer or b not in yer:
            return []
        return sorted((r[yer[b]] - r[yer[a]]) / 1e6 for r in kareler
                      if r[yer[a]] > 0 and r[yer[b]] > 0)

    tanim = [
        ("girdi→traversal", "HandleInputStart", "PerformTraversalsStart"),
        ("ölçüm/yerleşim", "PerformTraversalsStart", "DrawStart"),
        ("çizim kaydı (CPU)", "DrawStart", "SyncQueued"),
        ("sync", "SyncStart", "IssueDrawCommandsStart"),
        ("komut→swap", "IssueDrawCommandsStart", "SwapBuffers"),
        ("GPU", "IssueDrawCommandsStart", "GpuCompleted"),
        ("TOPLAM", "IntendedVsync", "FrameCompleted"),
    ]
    print(f"kare: {len(kareler)}")
    print(f'{"faz":20}{"ortanca":>10}{"90p":>10}{"azami":>10}')
    for ad, a, b in tanim:
        v = faz(a, b)
        if not v:
            continue
        print(f"{ad:20}{statistics.median(v):9.1f}ms{v[int(len(v) * 0.9)]:9.1f}ms{v[-1]:9.1f}ms")
    print("\nÇizim kaydı yüksekse maliyet çizim kodunda; GPU yüksekse dolgu/")
    print("aşırı çizimde. `adb shell setprop debug.hwui.overdraw show` ile")
    print("aşırı çizim renklerle görülür (mavi 1×, yeşil 2×, pembe 3×, kırmızı 4×+).")


def komut_alan(args) -> None:
    with tempfile.TemporaryDirectory() as gecici:
        yol = ekran_al(os.path.join(gecici, "a.png"))
        sinir = alan_sinirlari(yol, args.satir)
        if not sinir:
            sys.exit("Tuval sınırı bulunamadı; --satir ile oyun alanından bir satır seçin.")
        sol, sag = sinir
        print(f"tuval x: {sol} → {sag}  (genişlik {sag - sol} px)")
        print("Oyun alanı birimini piksele çevirmek için oyunun ölçeğiyle karşılaştırın;")
        print("gemi/araç en sola ve en sağa dayandığında iki konumun farkı ölçeği verir.")


def komut_surukle(args) -> None:
    """Sürükleme kalibrasyonu: parmak yolu → nesne hareketi ve ölü bölge.

    Her mesafe ``--tekrar`` kez denenir, medyan alınır: ekran sarsıntısı ve
    ölüm anındaki sıçramalar tek tek ölçümleri bozabilir.
    """
    mesafeler = [int(m) for m in args.mesafeler.split(",")]
    sonuc: dict[int, list[float]] = {m: [] for m in mesafeler}
    with tempfile.TemporaryDirectory() as gecici:
        for _ in range(args.tekrar):
            for dx in mesafeler:
                once = sprite_x(ekran_al(os.path.join(gecici, "o.png")), args.y0, args.y1)
                kabuk(f"input swipe {args.x} {args.y} {args.x + dx} {args.y} {args.sure_ms}")
                kabuk("sleep 0.6")
                sonra = sprite_x(ekran_al(os.path.join(gecici, "s.png")), args.y0, args.y1)
                if once is None or sonra is None:
                    print(f"dx={dx:>4}: nesne bulunamadı, atlandı")
                    continue
                sonuc[dx].append(sonra - once)
                print(f"dx={dx:>4}  önce={once:7.1f}  sonra={sonra:7.1f}  fark={sonra - once:7.1f}")

    print("\nparmak yolu | ölçülen hareket (medyan) | kayıp")
    for dx in mesafeler:
        if not sonuc[dx]:
            print(f"{dx:>10} | (ölçüm yok)")
            continue
        orta = statistics.median(sonuc[dx])
        print(f"{dx:>10} | {orta:>10.1f} px | {dx - orta:>6.1f} px")
    print("\nKayıp her parmak basışında bir kez ödenir (dokunma toleransı).")
    print("Hareketi sıfır çıkan en büyük mesafe = ölü bölge.")


def komut_erisim(args) -> None:
    """Erişilebilirlik: etkileşimli öğelerin ekran okuyucu etiketi var mı?

    Her dokunulabilir düğümün sınırları içinde bir etiket (text ya da
    content-desc) bulunmalı; yoksa TalkBack "düğme" der ama ne yaptığını
    söylemez. Etiket çoğu zaman çocuk düğümdedir, bu yüzden düğümün kendisine
    değil **sınırlarını kapsayan** etikete bakılır.

    Dokunma hedefi boyutu bilerek ölçülmez: Compose'da `Surface(onClick)` gibi
    bileşenlerde semantik düğüm, dokunma alanını değil içindeki metnin
    sınırlarını bildirebiliyor. Geçit'in 84 dp'lik yön tuşları bu yüzden 11 dp
    görünüyordu; şeridin dışına dokunmak çalıştığı için ölçüm yanlış alarmdı.
    Buton boyutu kodda tanımlı olduğundan kod incelemesiyle korunur.
    """
    ogeler = arayuz(hepsi=True)
    if not ogeler:
        sys.exit("Arayüz okunamadı; uygulama ön planda mı?")
    sinirsiz = [o for o in ogeler if o.get("sinirsiz")]
    ogeler = [o for o in ogeler if not o.get("sinirsiz")]
    tiklanabilir = [o for o in ogeler if o.get("tik")]
    etiketli = [o for o in ogeler if o["t"]]

    def kapsiyor(dis: dict, ic: dict) -> bool:
        return (ic["x1"] >= dis["x1"] - 2 and ic["y1"] >= dis["y1"] - 2 and
                ic["x2"] <= dis["x2"] + 2 and ic["y2"] <= dis["y2"] + 2)

    etiketsiz = [t for t in tiklanabilir
                 if not t["t"] and not any(kapsiyor(t, e) for e in etiketli)]
    print(f"dokunulabilir öğe: {len(tiklanabilir)}")
    print(f"etiketsiz: {len(etiketsiz)}")
    for o in etiketsiz:
        print(f"  [{o['x1']},{o['y1']}][{o['x2']},{o['y2']}]  "
              f"({(o['x2'] - o['x1']) / 2.625:.0f}×{(o['y2'] - o['y1']) / 2.625:.0f} dp)")
    if not etiketsiz:
        print("Her dokunulabilir öğenin bir etiketi var.")
    print(f"sınırı sıfır dokunulabilir düğüm: {len(sinirsiz)}")
    if sinirsiz:
        print("  Ekran okuyucu bunlara dokunarak inemez; gizli sistem çubuğunun bölgesine çizilen")
        print("  öğeler böyle gelir. TalkBack açıkken uygulama çubukları gizlemez, ama bu her ROM'da")
        print("  yetmiyor: SM-A515F'te alanın son 33 dp'si yine düşüyor (docs/cihaz-testi.md).")
        for o in sinirsiz:
            print(f"  {o['t'] or '(etiketsiz)'}")


# Reyon'un kısa ekran yerleşimi: docs/cihaz-testi.md, "v0.43.2" bölümü G1-G6.
# Etiketler arayüz dilinden geliyor; cihaz Türkçe ya da İngilizce olabilir diye
# ikisi de aranıyor. Başka bir dildeyse --brif/--tepsi/--artir ile verilebilir.
# 1.1.0'dan beri tur Görevler ekranından açılıyor: format (Market) seçilir,
# modun alıştırma satırına dokunulur.
REYON_ETIKET = {
    "diziliş": ["Diziliş", "Arrange"],
    "denetim": ["Denetim", "Audit"],
    "satış": ["Satış", "Sales"],
    "sipariş": ["Sipariş", "Ordering"],
    "market": ["Market"],
    "alıştırma": ["Alıştırma", "Practice"],
    "anladım": ["Anladım", "Got it"],
    "geri": ["Geri", "Back"],
    "brif": ["Planogram brifi", "Planogram brief"],
    "tepsi": ["Yerleştirilecek ürünler", "Products to place"],
    "artır": ["Artır", "More"],
    "raf": ["Reyon ", "Denetim rafı ", "Satış rafı ", "Sipariş rafı ",
            "Audit shelf ", "Sales shelf ", "Order shelf "],
    "plan": ["Plan ", "Planogram "],
    "kural": ["Satış kuralları", "Sales rules"],
}


def yogunluk() -> float:
    """Ekranın dp ölçeği: önce geçici (override) yoğunluk, yoksa fiziksel."""
    cikti = kabuk("wm density")
    gecici = re.search(r"Override density:\s*(\d+)", cikti)
    fiziksel = re.search(r"Physical density:\s*(\d+)", cikti)
    dpi = int((gecici or fiziksel).group(1)) if (gecici or fiziksel) else 160
    return dpi / 160.0


def dp_kutu(oge: dict, olcek: float) -> dict:
    return {"x": round(oge["x1"] / olcek, 1), "y": round(oge["y1"] / olcek, 1),
            "g": round((oge["x2"] - oge["x1"]) / olcek, 1),
            "b": round((oge["y2"] - oge["y1"]) / olcek, 1)}


def on_ekli(ogeler: list[dict], anahtar: str, ek: str = ": ") -> list[dict]:
    """Etiketi verilen ön eklerden biriyle başlayan öğeler."""
    onler = [a + ek for a in REYON_ETIKET[anahtar]]
    return [o for o in ogeler if any(o["t"].startswith(p) for p in onler)]


def reyon_gorevler() -> None:
    """Açık turdan Görevler ekranına döner.

    Reyon yarım kalan turu saklıyor; uygulama yine de her açılışta Görevler'le
    başlıyor. Bir tur açıksa üst çubuktaki geri oku Görevler'e götürür.
    """
    for _ in range(3):
        ogeler = arayuz()
        if any(o["t"] in REYON_ETIKET["market"] for o in ogeler):
            return
        geri = next((o for o in ogeler if o["t"] in REYON_ETIKET["geri"]), None)
        if not geri:
            return
        dokun(geri)


def reyon_turu_ac(tur: str) -> bool:
    """Görevler'de Market formatını seçip türün alıştırma satırından turu açar."""
    reyon_gorevler()
    for deneme in range(4):
        ogeler = arayuz()
        market = next((o for o in ogeler if o["t"] in REYON_ETIKET["market"]), None)
        if market:
            dokun(market)
            break
        kaydir()
    else:
        print(f"    {tur}: Market formatı bulunamadı")
        return False
    # Türün adı hem günün vakası düğmesinde hem alıştırma satırında geçiyor;
    # alıştırma satırı "Alıştırma" başlığının altında, en alttaki eşleşme o.
    for deneme in range(4):
        ogeler = arayuz()
        baslik = next((o for o in ogeler if o["t"] in REYON_ETIKET["alıştırma"]), None)
        adaylar = [o for o in ogeler if o["t"] in REYON_ETIKET[tur]
                   and (baslik is None or o["cy"] > baslik["cy"])]
        if baslik and adaylar:
            dokun(max(adaylar, key=lambda o: o["cy"]))
            break
        kaydir()
    else:
        print(f"    {tur}: alıştırma satırı bulunamadı ({'/'.join(REYON_ETIKET[tur])})")
        return False
    # Tur arka planda üretiliyor; raf gelene dek bekle. Modun ilk girişinde
    # "Nasıl çalışılır" kartı rafın üstüne açılır, kapatılır.
    for _ in range(20):
        ogeler = arayuz()
        kart = next((o for o in ogeler if o["t"] in REYON_ETIKET["anladım"]), None)
        if kart:
            dokun(kart)
            continue
        if on_ekli(ogeler, "raf", ek=""):
            return True
        time.sleep(1.5)
    print(f"    {tur}: raf tuvali gelmedi (üretim uzun sürdü ya da tur açılmadı)")
    return False


def reyon_olc(tur: str, olcek: float) -> dict:
    """Bir turu açıp panelin, rafın ve tepsinin kutularını dp olarak döndürür."""
    uygulamayi_ac(PAKET)
    if not reyon_turu_ac(tur):
        return {}
    ogeler = arayuz(hepsi=True)
    raf = on_ekli(ogeler, "raf", ek="")
    panel = {"diziliş": on_ekli(ogeler, "brif"),
             "denetim": on_ekli(ogeler, "plan", ek=""),
             "sipariş": on_ekli(ogeler, "artır")}.get(tur)
    if tur == "satış":
        # Kural adları content-desc taşımıyor; panelin başlığından aşağısı ölçülür.
        baslik = next((o for o in ogeler if o["t"] in REYON_ETIKET["kural"]), None)
        panel = [baslik] if baslik else []
    tepsi = on_ekli(ogeler, "tepsi")
    return {"raf": [dp_kutu(o, olcek) for o in raf],
            "panel": [dp_kutu(o, olcek) for o in (panel or [])],
            "tepsi": [dp_kutu(o, olcek) for o in tepsi]}


def komut_reyon(args) -> None:
    cihaz_var()
    if args.apk:
        print(f"Kuruluyor: {args.apk}")
        print("   ", adb("install", "-r", args.apk).strip() or "(çıktı yok)")
    print("Ölçülen yapı:", kurulu_yapi(PAKET))
    onceki = kabuk("wm size") + kabuk("wm density")
    print("Önceki ekran:", " ".join(onceki.split()))
    try:
        for dp_g, dp_y in [tuple(int(x) for x in args.ekran.split("x")), (0, 0)]:
            if dp_g:
                olcek = args.olcek
                kabuk(f"wm size {int(dp_g * olcek)}x{int(dp_y * olcek)}")
                kabuk(f"wm density {int(olcek * 160)}")
                time.sleep(1.5)
                print(f"\n=== {dp_g}x{dp_y} dp (ölçek {olcek}) ===")
            else:
                kabuk("wm size reset")
                kabuk("wm density reset")
                time.sleep(1.5)
                olcek = yogunluk()
                print(f"\n=== cihazın kendi ekranı (ölçek {olcek}) — G6 ===")
            for tur in ("diziliş", "denetim", "satış", "sipariş"):
                print(f"\n  {tur}")
                o = reyon_olc(tur, olcek)
                if not o:
                    continue
                for ad in ("raf", "panel", "tepsi"):
                    kutular = o[ad]
                    if not kutular:
                        print(f"    {ad:6s}: bulunamadı")
                        continue
                    boylar = [k["b"] for k in kutular]
                    gorunen = [b for b in boylar if b > 0]
                    print(f"    {ad:6s}: {len(kutular)} düğüm, boylar {boylar}")
                    if ad == "panel":
                        print(f"            görünen {len(gorunen)}/{len(kutular)}, "
                              f"ilk kutu y={kutular[0]['y']} dp")
                    if ad == "raf":
                        print(f"            genişlik {kutular[0]['g']} dp, "
                              f"yükseklik {kutular[0]['b']} dp")
    finally:
        kabuk("wm size reset")
        kabuk("wm density reset")
        print("\nEkran ayarları sıfırlandı.")


def main() -> None:
    ayristirici = argparse.ArgumentParser(description=__doc__,
                                          formatter_class=argparse.RawDescriptionHelpFormatter)
    ayristirici.add_argument("--paket", default=PAKET)
    alt = ayristirici.add_subparsers(dest="komut", required=True)

    k = alt.add_parser("kare", help="kare hızı ve takılma oranı")
    k.add_argument("--sure", type=int, default=15, help="ölçüm penceresi (s)")
    k.set_defaults(func=komut_kare)

    f = alt.add_parser("fazlar", help="kare bütçesinin fazlara dağılımı")
    f.set_defaults(func=komut_fazlar)

    a = alt.add_parser("alan", help="tuvalin piksel sınırları")
    a.add_argument("--satir", type=int, default=None)
    a.set_defaults(func=komut_alan)

    s = alt.add_parser("surukle", help="sürükleme hassasiyeti ve ölü bölge")
    s.add_argument("--x", type=int, default=400, help="sürüklemenin başlangıç x'i")
    s.add_argument("--y", type=int, default=1500, help="sürüklemenin y'si (oyun alanı içi)")
    s.add_argument("--y0", type=int, default=1935, help="nesne bandı üst y")
    s.add_argument("--y1", type=int, default=2035, help="nesne bandı alt y")
    s.add_argument("--mesafeler", default="20,40,80,160")
    s.add_argument("--tekrar", type=int, default=3)
    s.add_argument("--sure-ms", dest="sure_ms", type=int, default=300)
    s.set_defaults(func=komut_surukle)

    e = alt.add_parser("erisim", help="etkileşimli öğelerin ekran okuyucu etiketi")
    e.set_defaults(func=komut_erisim)

    y = alt.add_parser("kurulu_yapi", help="kurulu sürüm ve APK sha256")
    y.set_defaults(func=lambda args: print(kurulu_yapi(args.paket)))

    r = alt.add_parser("reyon", help="dört modun kısa ekran yerleşimi")
    r.add_argument("--apk", help="önce kurulacak APK (adb install -r)")
    r.add_argument("--ekran", default="360x640", help="dp cinsinden ekran (varsayılan 360x640)")
    r.add_argument("--olcek", type=float, default=2.0, help="dp ölçeği (varsayılan 2.0 = 320 dpi)")
    r.set_defaults(func=komut_reyon)

    args = ayristirici.parse_args()
    cihaz_var()
    args.func(args)


if __name__ == "__main__":
    main()
