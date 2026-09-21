# Yayın öncesi kontrol listesi

## Depoda hazır

- [x] `store/play/<14 dil>/{title,short,full}.txt` — sınırlar `tools/check_store.py` ile denetleniyor
- [x] `store/play/release-notes/1.0.0.txt` — 14 dil, dil başına 500 karakterin altında
- [x] `store/graphics/icon-512.png` (512×512, 32 bit) ve `feature-1024.png` (1024×500)
- [x] `store/data-safety.md`, `store/icerik-derecelendirme.md`
- [x] Gizlilik politikası: `site/gizlilik.html`, 14 dil

## Yayından önce yapılacaklar

- [ ] `reyon-release.jks` üret (`keytool`, alias `reyon`) ve iki secret'ı ekle:
      `ANDROID_KEYSTORE_BASE64` (base64 kodlu keystore), `ANDROID_KEYSTORE_PASSWORD`
- [ ] Depo ayarları → Pages → kaynak "GitHub Actions"; `site/` yayına girsin
- [ ] `v1.0.0` etiketi it; `release.yml` imzalı APK + AAB üretsin
- [ ] APK doğrulaması: SHA256, manifest `versionName`/paket adı, dex'te 14 dilin metni, imza parmak izi
- [ ] Cihazda koşum (`tools/cihaz_testi.py`), `docs/cihaz-testi.md` protokolü
- [ ] Play Console: uygulamayı oluştur, kategori **Uygulamalar → Eğitim**
- [ ] Play App Signing'i aç; yüklenen AAB `-play.aab` olan
- [ ] Ekran görüntüleri: telefon için en az 2 (1080×1920), her modun bir karesi önerilir
- [ ] Veri güvenliği formu ve IARC anketi (`store/` altındaki cevaplarla)
- [ ] Gizlilik politikası URL'si: `https://aripdcom.github.io/reyon/gizlilik.html`

## Ekran görüntüsü önerisi

Dört modun her birinden bir kare: Diziliş (brif + raf), Denetim (plan/raf ikilisi),
Satış (puan paneli), Sipariş (hafta grafiği). Metin eklemeye gerek yok; arayüz
zaten kullanıcının dilinde.
