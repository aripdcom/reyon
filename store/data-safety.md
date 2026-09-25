# Play Console: Veri güvenliği formu cevapları

Uygulama hiçbir veri toplamaz, paylaşmaz ve ağa bağlanmaz. Form cevapları buna göre:

| Soru | Cevap | Not |
| --- | --- | --- |
| Uygulamanız zorunlu kullanıcı veri türlerinden herhangi birini topluyor ya da paylaşıyor mu? | **Hayır** | Analitik, çökme raporu, reklam, hesap yok |
| Toplanan veriler aktarımda şifreleniyor mu? | Uygulanamaz | Veri toplanmıyor, ağ yok |
| Kullanıcılar verilerinin silinmesini isteyebiliyor mu? | Uygulanamaz | Cihazdaki ilerleme, uygulama verisi silinince gider |
| Veri türleri | Hiçbiri seçilmez | |
| Üçüncü taraf SDK'lar | Yok | Yalnızca AndroidX ve Kotlin |
| Bağımsız güvenlik incelemesi | Hayır | |

Diğer beyanlar:

- **Reklam içeriyor mu?** Hayır.
- **İzinler:** Manifestte `uses-permission` yok. Paylaşım için `FileProvider` (izin değildir) ve sonuç kartının yalnızca kullanıcının seçtiği uygulamaya okuma yetkisiyle verilmesi; bu, Play tanımında "veri paylaşımı" sayılmaz (kullanıcının başlattığı işlem).
- **Hedef kitle:** 13 yaş ve üzeri seçilmesi önerilir. Uygulama her yaş için uygundur ama "çocuklara yönelik" seçimi Aile politikası yükümlülükleri getirir.
- **Gizlilik politikası URL'si:** https://reyon.aripd.com/privacy.html
- **Erişilebilirlik / sağlık / finans beyanları:** Uygulanamaz.

Kanıt: `app/src/main/AndroidManifest.xml` tek bir `uses-permission` taşımaz ve
bağımlılık listesi `app/build.gradle.kts` içinde görünür — AndroidX ve Kotlin dışında
kütüphane yok, motor modülünün (`engine/`) hiç bağımlılığı yok.
