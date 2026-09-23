# Reyon

Android için FMCG raf simülatörü: planogramı kur, rafı denetle, raf verimini artır,
haftalık stoğu yönet. Dört mod, 14 dil, çevrimdışı; reklam yok, izleyici yok, tek bir
izin bile yok.

[![CI](https://github.com/aripdcom/reyon/actions/workflows/ci.yml/badge.svg)](https://github.com/aripdcom/reyon/actions/workflows/ci.yml)

- **Site:** https://aripdcom.github.io/reyon
- **Gizlilik:** https://aripdcom.github.io/reyon/gizlilik.html
- **APK:** [en yeni sürüm](https://github.com/aripdcom/reyon/releases/latest/download/reyon.apk)

## Dört mod

| Mod | İş |
| --- | --- |
| **Diziliş** | Brifteki kurallardan planogramı kur. Kurallar tek bir dizilişe götürür; tahmin gerekmez, çıkarım yeter. |
| **Denetim** | Üstte planogram, altta mağaza rafı. Sapmaları işaretle: yer değişimi, boş göz, yabancı ürün, yanlış marka ya da boy, taşma. |
| **Satış** | Rafı satış kurallarına göre diz (göz hizası, ağırlar alta, tamamlayıcılar yan yana, temizlik gıdadan uzak, kategori ve marka blokları). Raf verimi, iyileştiricinin bulduğu uzman dizilişiyle karşılaştırılır. |
| **Sipariş** | Planogram hazır, iş stokta. Bir hafta boyunca her gün ürün başına koli siparişi ver: iade, bekleme maliyeti ve fire hesaba girer. Haftalık rapor kârı, hizmet düzeyini ve stok devrini aynı tahminleri gören uzmanla karşılaştırır. |

Uygulama Görevler ekranında açılır: her gün dört modda günün vakası (cihaz tarihinden
üretilir, sunucu yok; format günden güne Market, Süpermarket, Hipermarket arasında
döner) ve seçilen mağaza formatında alıştırma. Sonuçlar puan değil rapor: uzmana göre
yüzde, düzey (Uzman düzeyi, İyi, Gelişmeli, Zayıf) ve kişisel en iyi.

## Yapı

```
engine/    saf Kotlin/JVM motor — hiçbir bağımlılığı yok (üretici, çözücü, kurallar,
           denetim, satış puanı, sipariş simülasyonu) + testler + denge ölçümü
app/       Android uygulaması: Compose arayüz (açık/koyu tema, IBM Plex), 14 dil,
           paylaşım kartı, ayarlar
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

Yayın paketlerini `release.yml`, GitHub Release oluşturmadan önce
`tools/apk_dogrula.py` ile denetler; denetim geçmezse etiketli sürüm yayımlanmaz.
İndirilen bir sürüm için elde de koşulur:

```sh
python3 tools/apk_dogrula.py     # dist/: SHA256, paket/sürüm, izin yokluğu, 14 dil, v2 imza ve parmak izi
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
