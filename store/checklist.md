# Yayın öncesi kontrol listesi

Son güncelleme: 2 Ekim 2026. Reyon Google Play'de yayında: 1.1.2 (10102),
https://play.google.com/store/apps/details?id=com.aripd.reyon. Aşağıdaki liste ilk yayının
kaydı; sonraki sürümlerde yapılacaklar "Her yeni sürümde" bölümünde.

## Depoda hazır

- [x] `store/play/<14 dil>/{title,short,full}.txt` — sınırlar `tools/check_store.py` ile denetleniyor
- [x] `store/play/release-notes/1.0.0.txt`, `1.1.0.txt`, `1.1.1.txt` ve `1.1.2.txt` — 14 dil, dil başına
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
2. [x] **Uygulamayı oluştur.** Varsayılan dil İngilizce (en-US), tür: uygulama, ücretsiz.
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
       anahtarla imzalı, ayrı yükleme anahtarı yok. Protected with Play'deki **Automatic
       protection** kapatıldı: açık kalsa Play dağıttığı pakete kurulum kaynağı denetimi
       ekler, paket kaynak koddakiyle aynı olmaz.
4. [x] **Mağaza girişi.** Başlık, kısa ve tam açıklama `store/play/<dil>/`'den; varsayılan
       dil `en`, diğer 13 dil çeviri olarak (yerel ayar karşılıkları `store/README.md`'de).
       Ortak görseller (Common visual assets): simge ve öne çıkan görsel `store/graphics/`'ten.
       Telefon ekran görüntüleri `store/screenshots/en/` sırayla 1–6 (Türkçe giriş için `tr/`).
5. [x] **Uygulama içeriği.**
       - Gizlilik politikası: `https://reyon.aripd.com/privacy.html`
       - Uygulamaya erişim: bütün işlevler giriş gerektirmeden açık
       - Reklam: yok · Reklam kimliği: kullanılmıyor
       - İçerik derecelendirmesi (IARC): `store/icerik-derecelendirme.md` (beklenen PEGI 3)
       - Hedef kitle: 16–17 ve 18+ (Play Console'da seçildi; `store/data-safety.md`)
       - Veri güvenliği: `store/data-safety.md` (veri toplanmıyor, paylaşılmıyor)
       - Sağlık, finans, kamu kurumu beyanları: uygulanamaz
6. [x] **Sürüm** (26 Eylül 2026'da incelemeye gönderildi, 2 Ekim 2026 itibarıyla yayında).
       `reyon-v1.1.2-play.aab` (APK değil). İlk yüklenen `reyon-v1.1.1-play.aab`
       (10101) Google'ın ürettiği anahtarla işlenmişti; anahtar `reyon`'a çevrilince Play onu
       "not available for releases" yaptı, silinemiyor ve aynı sürüm kodu yeniden
       yüklenemiyor. Bu yüzden Play'deki ilk sürüm 1.1.2 (10102); uygulama 1.1.1 ile aynı.
       Play'e ilk yükleme olduğu için sürüm notu olarak
       `store/play/release-notes/1.1.0.txt` önerilir (14 dil): yeni kullanıcı için asıl
       yenilikler orada; 1.1.1 ve 1.1.2'nin notları GitHub Release'te ve sitede kalır.
       Önerilen yol: dahili teste yükle ve kendi telefonunda dene, aynı sürümü kapalı teste
       yükselt (7. maddedeki rapor için), rapor temizse üretime yükselt. Ülkeler: bütün
       ülkeler ya da seçilenler.
       Yapılan: üretimde 177 ülke/bölge; paket kapalı teste yüklenmiş 10102 (1.1.2), üretim
       sürümüne **Add from library** ile eklendi — aynı AAB'yi yeniden yüklemek "Version code
       10102 has already been used" verir. Sürüm notu `release-notes/1.1.0.txt` (14 dil, dosya
       bütünüyle yapıştırıldı). 26 Eylül 2026'da incelemeye gönderildi. Sürüm üretime
       çıktığı için uygulama imza anahtarı (`reyon`) artık sabit.
7. [ ] **Lansman öncesi rapor.** Yalnız kapalı ya da açık test kanalına yayımlanan sürüm için
       Play'in kendi cihazlarında koşar; dahili testte ve doğrudan üretime yüklenen sürümde
       çıkmaz. Kuruluş hesabında kapalı test için 12 test kullanıcısı / 14 gün şartı yok.
       Çökme ve erişilebilirlik uyarılarına bakılır.
       Durum: kapalı test kanalında 1.1.2 incelemeye gönderildi ama test kullanıcısı eklenmedi
       ("Select testers" açık). Kendini ya da birkaç kişiyi ekleyince rapor oluşur; üretimi
       engellemez.
8. [ ] **Büyük ekran (isteğe bağlı).** Android 16, hedef API 36'daki uygulamalarda 600 dp ve
       üstü ekranlarda dikey kilidi yok sayar; bir tablette ya da katlanabilirde yatay görünüm
       bir kez denenir.
9. [x] **Üretime çıkınca.** Sitede Google Play bağlantısı (`tools/gen_site.py`, `PLAY`; paket
       adı `applicationId`'den okunur): üst çubukta ve iki indirme bölümünde birincil düğme,
       GitHub'daki APK ikincil. README'de de var.

## Her yeni sürümde

1. `store/play/release-notes/<sürüm>.txt` (14 dil) ve `python3 tools/gen_site.py`: sitenin
   "Yenilikler" bölümü en yeni ara sürümün (ör. 1.1.0 + 1.1.1) notlarını gösterir;
   `check_site.py` yeniden üretilmeyen siteyi durdurur
2. main'e birleştir, `release.yml`'i `v<sürüm>` etiketiyle çalıştır
3. Release'teki `-play.aab`'ı Play'e yükle (**Production → Create new release → Upload**);
   sürüm notu o sürümün `release-notes/<sürüm>.txt` dosyası, bütünüyle yapıştırılır.
   `versionCode` sürümden türer, elle artırılmaz; aynı kod Play'e ikinci kez yüklenemez

## Ekran görüntüsü önerisi

Görevler ekranı ve dört modun her birinden bir kare: Görevler (günün vakası +
alıştırma), Diziliş (brif + raf etiketli raf), Denetim (planogram/mağaza rafı ikilisi),
Satış (raf verimi ve kurallar), Sipariş (haftalık rapor: grafik ve bulgular). Açık tema
varsayılan; bir kare koyu temadan olabilir. Metin eklemeye gerek yok; arayüz zaten
kullanıcının dilinde.
