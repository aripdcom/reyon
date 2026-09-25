# Yayın öncesi kontrol listesi

Son güncelleme: 1.1.1 (25 Eylül 2026). Depodaki ve CI'daki işler bitti; kalanlar Play
Console'da, hesap sahibinin elinde. Play'e ilk yüklenecek sürüm 1.1.1.

## Depoda hazır

- [x] `store/play/<14 dil>/{title,short,full}.txt` — sınırlar `tools/check_store.py` ile denetleniyor
- [x] `store/play/release-notes/1.0.0.txt`, `1.1.0.txt` ve `1.1.1.txt` — 14 dil, dil başına
      500 karakterin altında
- [x] `store/graphics/icon-512.png` (512×512, 32 bit) ve `feature-1024.png` (1024×500) —
      1.1.0 kimliğinde (petrol zemin, yeni işaret); `tools/gen_store_graphics.py` simgeden üretir
- [x] Ekran görüntüleri: `store/screenshots/{tr,en}/`, 1080×1920, altı kare (Görevler, dört mod,
      bir koyu tema karesi); 1.1.0 arayüzünden (docs/cihaz-testi.md, "1.1.0 · FMCG tasarımı cihaz koşumu").
      Alfasız 24 bit PNG, Play'in istediği biçim (`tools/store_screenshots.py` çevirir)
- [x] `store/data-safety.md`, `store/icerik-derecelendirme.md`
- [x] Gizlilik politikası: her dil kendi sayfasında (`site/privacy.html` İngilizce,
      `site/<dil>/privacy.html`)
- [x] Site 14 dilde: https://reyon.aripd.com (kök İngilizce, `/tr/` … `/ar/`); `tools/gen_site.py`
      üretir, `tools/check_site.py` denetler

## Yapıldı

- [x] Keystore ve iki secret (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`); elde
      yedeklenen keystore CI'ın kullandığıyla aynı
- [x] Pages: kaynak "GitHub Actions", özel alan adı `reyon.aripd.com`
- [x] `v1.1.0` Release: `release.yml` imzalı APK + AAB, kaynak arşivleri ve `SHA256SUMS.txt`
- [x] Yayın paketi denetimi (`tools/apk_dogrula.py`): Release'ten önce CI'da, sonra indirilen
      dosyalarda "hata yok". Sertifika SHA-256
      `B2:2F:C3:F8:6D:46:EB:0A:BF:13:E2:C8:DA:06:63:19:F5:26:D4:97:1A:13:55:97:5A:D7:5C:38:CC:89:37:92`,
      v1.0.x ile aynı: 1.1.0 eski sürümün üzerine güncelleme olarak kurulur
- [x] Hedef API 36 (Android 16), en düşük 26 (Android 8.0) — Play'in yeni uygulamalar için
      istediği hedef API karşılanıyor
- [x] 16 KB sayfa boyutu: tek yerel kütüphane `libandroidx.graphics.path.so` (Compose'un
      bağımlılığı); dört ABI'de de sıkıştırılmamış, zip'te ve ELF LOAD kesimlerinde 16 KB hizalı
- [x] Cihazda koşum: SM-A515F, `docs/cihaz-testi.md` protokolü, TalkBack dahil

## Play Console'da yapılacaklar

Sırayla; her adımın cevabı depoda yazılı.

1. [x] **Hesap türü: kuruluş (Organization).** Kişisel hesaplara getirilen kapalı test şartı
       (12 test kullanıcısı, 14 gün) bu hesap için geçerli değil; sürüm doğrudan üretime
       çıkabilir.
2. [ ] **Uygulamayı oluştur.** Varsayılan dil İngilizce (en-US), tür: uygulama, ücretsiz.
       Kategori **Uygulamalar → Eğitim**; iletişim `reyon@aripd.com`, web sitesi
       `https://reyon.aripd.com`.
3. [x] **Play App Signing — uygulama imza anahtarı `reyon`** (25 Eylül 2026). GitHub'daki APK
       ile Play sürümünün birbirinin üzerine kurulabilmesi için Play'in imza anahtarı bizim
       `reyon` anahtarımız olmalı. Play uygulamayı oluştururken kendi anahtarını üretmişti
       (SHA-256 `06:8A:54:5F:…:72:F9`); **Change key → Export and upload a key from Java
       keystore** ile `reyon` yüklendi, uygulama imza anahtarı artık `B2:2F:C3:F8:…:37:92`.
       Sayfa menüde görünmüyor (Test and release → App integrity, Protected with Play'e
       yönlendiriyor); doğrudan adres Console'daki uygulama adresinin sonuna `/keymanagement`
       eklenerek açılır. Anahtar, açık teste ya da üretime bir sürüm çıkana kadar
       değiştirilebilir, sonra sabitlenir. Komut (`pepk.jar` ve `encryption_public_key.pem`
       o sayfadan iner):
       `java -jar pepk.jar --keystore=reyon-release.jks --alias=reyon --output=reyon-signing-key.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem`
       (anahtar parolası keystore parolasıyla aynı). Yükleme anahtarı da `reyon`: AAB aynı
       anahtarla imzalı, ayrı yükleme anahtarı yok.
4. [ ] **Mağaza girişi.** Başlık, kısa ve tam açıklama `store/play/<dil>/`'den; varsayılan
       dil `en`, diğer 13 dil çeviri olarak (yerel ayar karşılıkları `store/README.md`'de).
       Ortak görseller (Common visual assets): simge ve öne çıkan görsel `store/graphics/`'ten.
       Telefon ekran görüntüleri `store/screenshots/en/` sırayla 1–6 (Türkçe giriş için `tr/`).
5. [ ] **Uygulama içeriği.**
       - Gizlilik politikası: `https://reyon.aripd.com/privacy.html`
       - Uygulamaya erişim: bütün işlevler giriş gerektirmeden açık
       - Reklam: yok · Reklam kimliği: kullanılmıyor
       - İçerik derecelendirmesi (IARC): `store/icerik-derecelendirme.md` (beklenen PEGI 3)
       - Hedef kitle: 16–17 ve 18+ (Play Console'da seçildi; `store/data-safety.md`)
       - Veri güvenliği: `store/data-safety.md` (veri toplanmıyor, paylaşılmıyor)
       - Sağlık, finans, kamu kurumu beyanları: uygulanamaz
6. [ ] **Sürüm.** `reyon-v1.1.1-play.aab` (APK değil). Paket Play'e bir kez yüklendi; aynı
       sürüm kodu (10101) ikinci kez yüklenemez, sürüm oluştururken **Add from library** ile
       eklenir. Play'e ilk yükleme olduğu için sürüm notu olarak
       `store/play/release-notes/1.1.0.txt` önerilir (14 dil): yeni kullanıcı için asıl
       yenilikler orada; 1.1.1'in notu (dil seçici, bağlantılar) GitHub Release'te kalır.
       Önerilen yol: önce dahili teste yükle, 7. maddedeki raporu gör, aynı sürümü üretime
       yükselt. Ülkeler: bütün ülkeler ya da seçilenler.
7. [ ] **Lansman öncesi rapor.** Yalnızca test kanalına (dahili test yeter) yüklenen sürüm için
       Play'in kendi cihazlarında koşar; doğrudan üretime yüklenen sürümde çıkmaz. Çökme ve
       erişilebilirlik uyarılarına bakılır.
8. [ ] **Büyük ekran (isteğe bağlı).** Android 16, hedef API 36'daki uygulamalarda 600 dp ve
       üstü ekranlarda dikey kilidi yok sayar; bir tablette ya da katlanabilirde yatay görünüm
       bir kez denenir.
9. [ ] **Üretime çıkınca.** Siteye Google Play bağlantısı eklenir (`tools/gen_site.py`).

## Her yeni sürümde

1. `store/play/release-notes/<sürüm>.txt` (14 dil) ve `python3 tools/gen_site.py`: sitenin
   "Yenilikler" bölümü en yeni ara sürümün (ör. 1.1.0 + 1.1.1) notlarını gösterir;
   `check_site.py` yeniden üretilmeyen siteyi durdurur
2. main'e birleştir, `release.yml`'i `v<sürüm>` etiketiyle çalıştır
3. Release'teki `-play.aab`'ı Play'e yükle; `versionCode` sürümden türer, elle artırılmaz

## Ekran görüntüsü önerisi

Görevler ekranı ve dört modun her birinden bir kare: Görevler (günün vakası +
alıştırma), Diziliş (brif + raf etiketli raf), Denetim (planogram/mağaza rafı ikilisi),
Satış (raf verimi ve kurallar), Sipariş (haftalık rapor: grafik ve bulgular). Açık tema
varsayılan; bir kare koyu temadan olabilir. Metin eklemeye gerek yok; arayüz zaten
kullanıcının dilinde.
