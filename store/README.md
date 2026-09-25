# Mağaza dosyaları

Google Play listelemesi için metinler ve form cevapları. Kaynak dosyalar buradadır; Play Console'a elle kopyalanır.

| Dosya | İçerik | Sınır |
| --- | --- | --- |
| `play/<dil>/title.txt` | Uygulama adı | 30 karakter |
| `play/<dil>/short.txt` | Kısa açıklama | 80 karakter |
| `play/<dil>/full.txt` | Tam açıklama | 4000 karakter |
| `play/release-notes/<sürüm>.txt` | Sürüm notları (dil başına `<tr-TR>` / `<en-US>` … blokları) | dil başına 500 karakter |
| `data-safety.md` | Veri güvenliği formu cevapları ve hedef kitle | |
| `icerik-derecelendirme.md` | IARC anketi cevapları | |
| `graphics/icon-512.png` | Uygulama simgesi | 512×512, 32 bit PNG |
| `graphics/feature-1024.png` | Öne çıkan görsel | 1024×500, alfasız 24 bit PNG |
| `screenshots/<dil>/*.png` | Telefon ekran görüntüleri (`en` varsayılan, `tr` Türkçe giriş) | alfasız 24 bit PNG, 2–8 kare |
| `checklist.md` | Yayın öncesi kontrol listesi | |

Sınırları, dil kapsamını ve görsellerin biçimini `tools/check_store.py` denetler; CI her
itmede çağırır. Yeni ekran görüntüsü alınınca `python3 tools/store_screenshots.py` onu
Play'in istediği alfasız 24 bit PNG'ye çevirir (`screencap` 32 bit yazar).

## Kategori

**Uygulamalar → Eğitim.** Reyon hızlı tüketim (FMCG) ekipleri için bir raf simülatörüdür:
planogram kurma, plana uygunluk denetimi, satış düzeni ve stok kararı. Sonuçlar puan değil
rapor (uzmana göre yüzde, düzey, kişisel en iyi). Oyunlar kategorisindeki başarım ve liderlik
tablosu özellikleri kullanılmıyor, kayıp yok.

## Diller

Uygulamanın çevirisi olan her dil için bir listeleme klasörü var; listeleme eksik kalırsa
o dildeki kullanıcı uygulamayı kendi dilinde, mağaza sayfasını İngilizce görür. Klasör adları
uygulamadaki dil kodlarıyla (`AppLocale.TAGS`, `res/values-<dil>`) aynıdır; Play Console bölge
istediği için kopyalarken aşağıdaki karşılığı seçin.

| Klasör | Play yerel ayarı | Dil |
| --- | --- | --- |
| `play/en` | en-US | İngilizce (varsayılan) |
| `play/tr` | tr-TR | Türkçe |
| `play/de` | de-DE | Almanca |
| `play/fr` | fr-FR | Fransızca |
| `play/nl` | nl-NL | Hollandaca |
| `play/es` | es-ES | İspanyolca |
| `play/pt` | pt-BR | Portekizce (Brezilya) |
| `play/it` | it-IT | İtalyanca |
| `play/da` | da-DK | Danca |
| `play/sv` | sv-SE | İsveççe |
| `play/nb` | no-NO | Norveççe (bokmål) |
| `play/fi` | fi-FI | Fince |
| `play/ru` | ru-RU | Rusça |
| `play/ar` | ar | Arapça |

## Metinler nereden geliyor

`title.txt` ve `short.txt` elle yazıldı. `full.txt`'in gövdesi uygulamanın kendi
çevirilerinden derlendi: dört modun anlatımı (`reyon_kind_*` + `reyon_*_intro`), söz
çipleri (`chip_no_*`), gizlilik özeti (`about_privacy_summary`) ve lisans notu
(`about_app_license`). Böylece mağaza metni uygulamayla aynı şeyi söylüyor ve
14 dilde aynı özenle çevrilmiş oluyor. Dosyalar artık elle düzenlenebilir.

1.1.0'da (FMCG tasarımı) başlık ve kısa açıklama "planogram alıştırması" yerine raf
simülatörü oldu; tam açıklamaya Görevler paragrafı (günün vakası, mağaza formatları) ve
rapor paragrafı (düzey adları `band_*`, rapor terimleri `report_*`) eklendi, yıldız ve
hedef dili kalktı. Mod satırları yine uygulamanın güncel giriş metinlerinden geliyor.
