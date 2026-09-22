# Reyon

Android için planogram alıştırması: raf dizilişini kurallardan çıkar, planı denet,
satış düzenini kur, stok kararını ver. Dört mod, 14 dil, çevrimdışı; reklam yok,
izleyici yok, tek bir izin bile yok.

[![CI](https://github.com/aripdcom/reyon/actions/workflows/ci.yml/badge.svg)](https://github.com/aripdcom/reyon/actions/workflows/ci.yml)

- **Site:** https://aripdcom.github.io/reyon
- **Gizlilik:** https://aripdcom.github.io/reyon/gizlilik.html
- **APK:** [en yeni sürüm](https://github.com/aripdcom/reyon/releases/latest/download/reyon.apk)

## Dört mod

| Mod | İş |
| --- | --- |
| **Diziliş** | Brifteki kurallardan rafın tek doğru dizilişini çıkar. Her kural tutunca ✓ olur; tahmin gerekmez, çıkarım yeter. |
| **Denetim** | Üstte planogram, altta gerçek raf. Sapmaları bul: yer değişimi, boş göz, yabancı ürün, yanlış marka ya da boy, taşma. |
| **Satış** | Rafı istediğin gibi diz; puan satış kurallarından gelir (göz hizası, ağırlar alta, tamamlayıcılar yan yana, temizlik gıdadan uzak, kategori ve marka blokları). Hedef, iyileştiricinin bulduğu en iyi diziliş. |
| **Sipariş** | Plan hazır, iş stokta. Her gün ürün başına kaç koli isteyeceğine karar ver: iade, bekleme maliyeti ve fire hesaba girer. Hedef, aynı tahminleri gören uzmanın kârı. |

Her modun günlük turu (cihaz tarihinden üretilir, sunucu yok) ve serbest turu var;
zorluk seçilir.

## Yapı

```
engine/    saf Kotlin/JVM motor — hiçbir bağımlılığı yok (üretici, çözücü, kurallar,
           denetim, satış puanı, sipariş simülasyonu) + testler + denge ölçümü
app/       Android uygulaması: Compose arayüz, 14 dil, paylaşım kartı, ayarlar
site/      proje sayfası ve 14 dilde gizlilik politikası (GitHub Pages)
store/     Play listeleme metinleri, görseller, form cevapları
tools/     metin/mağaza/site denetimleri, yayın paketi doğrulaması, gizlilik ve
           mağaza görseli üreticileri
docs/      cihaz koşum protokolü ve kütükler
```

## Derleme

```sh
./gradlew :engine:test          # motor birim testleri
./gradlew :engine:probe         # denge ölçümü (geçme/kalma vermez, rapor basar)
./gradlew :app:testDebugUnitTest  # Robolectric + Compose arayüz testleri
./gradlew :app:assembleDebug    # debug APK
```

Sürüm `-PappVersion=X.Y.Z` ile verilir; `release.yml` etiketten türetir.
`versionCode = major*10000 + minor*100 + patch`.

Denetimler CI'da Gradle'dan önce koşar:

```sh
python3 tools/check_strings.py   # 14 dilde anahtar ve biçim belirteci paritesi
python3 tools/check_store.py     # Play metin sınırları ve dil kapsamı
python3 tools/check_site.py      # gizlilik sayfası üreticisiyle aynı mı, bağlantılar tutarlı mı
```

Yayın paketleri CI'da değil, etiketten sonra elde doğrulanır — indirilen dosya
gerçekten beklenen paket mi:

```sh
python3 tools/apk_dogrula.py     # dist/: SHA256, paket/sürüm, izin yokluğu, 14 dil, v2 imza
```

## Gizlilik

Uygulama ağa hiç bağlanmaz ve manifestte tek bir `uses-permission` taşımaz.
İlerleme ve ayarlar yalnızca cihazda, uygulamanın özel alanında durur; uygulama
kaldırılınca gider. Ayrıntı: [gizlilik politikası](https://aripdcom.github.io/reyon/gizlilik.html).

## Lisans

Kaynak kod [GNU GPL v3](LICENSE) ile açıktır. "Reyon" adı ve logosu lisansa dahil
değildir; çatallarsanız kendi adınızla ve kendi simgenizle yayımlayın.

Reyon önce [ZA Games](https://github.com/aripdcom/zagames) içinde bir mod olarak
geliştirildi; ayrı bir uygulama olarak buraya taşındı. Kayıtlar taşınmaz: Android
sanal alanı iki uygulamanın verisini ayırır, Reyon boş başlar.
