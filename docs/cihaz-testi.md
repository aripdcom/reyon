# Cihaz test protokolü

Motor birim testleri **kuralların doğru** olduğunu gösterir; uygulamanın
**kullanılabilir** olduğunu göstermez. Bir motor, testleri tam geçerken cihazda
ulaşılmaz derecede zor, kontrolü tepkisiz, yerleşimi taşmış ya da kare hızı düşük
olabilir — çünkü birim testleri parmağı, ekranı ve gerçek işlemciyi bilmez.

Bu belge, sürüm çıkmadan önce geçilmesi gereken aşamaları tanımlar.

> **Kural:** Arayüz yerleşimi, kontrol ya da denge değiştiğinde A–F aşamaları
> koşulur ve sonuçları [Sonuç kütüğü](#sonuç-kütüğü) bölümüne işlenir. Motor
> testleri yeşil diye bu adımlar atlanmaz.

### Protokolün CI'daki karşılığı

Aşamalar farklı otomatikleşiyor; ayrımı bilerek koruyoruz:

| Aşama | Nerede koşar |
| --- | --- |
| **A** cihaz koşumu | Sürüm öncesi, gerçek cihazda ([`store/checklist.md`](../store/checklist.md)) |
| **B** kare hızı | Sürüm öncesi, gerçek cihazda — emülatörün kare süreleri gerçeği temsil etmez |
| **C** giriş kalibrasyonu | Sürüm öncesi, gerçek cihazda — gerçek dokunma ve ekran ölçeği gerekir |
| **D** denge | **CI**: değişmez testleri `:engine:test`, ölçüm koşumu `:engine:probe` |
| **E** erişilebilirlik | Kontrast **CI**'da (`ThemeContrastTest`); etiketler sürüm öncesi cihazda |
| **F** diller | Metin bütünlüğü ve dil listeleri **CI**'da (`tools/check_strings.py`, `AppLocaleTest`); taşma, kırpma, sağdan sola yerleşim sürüm öncesi cihazda |

CI'daki `probe` adımı geçme/kalma vermez; amacı **ölçüm koşumunun çürümesini
engellemek**. Asıl koruma, ölçülen doğruların değişmez testine çevrilmesidir:

| Değişmez | Nerede |
| --- | --- |
| Her bulmaca tahminsiz çözülür, brif kısa kalır, üretim bütçede | `ReyonGeneratorTest` |
| Denetim sapmaları ayrık ve görünür; plan ile raf yalnızca sapma gözlerinde ayrışır | `ReyonAuditTest` |
| Satış hedefi tabanı geçer, geçerli tam doluluktur; iyileştirici bütçede | `ReyonSalesTest` |
| Sipariş: gerçekleşen talep tahmin aralığında; uzman siparişlerinin tekrar oynanışı hedefi birebir verir; gün kuralları elle izlenen haftayla eşleşir | `ReyonOrderTest` |
| Blok adları en dar gerçek gözde kırpılmaz (35 ad, TR ve EN; 360 dp telefonda Zor planı, tek yüz) | `ReyonBlockLabelTest` |
| Denetimde plan ve raf 360×640'ta da aynı genişlikte ve ekran içinde; plan büyütme açılıp kapanır | `ReyonAuditLayoutTest` |
| Satış panelinin tavanı tepsiye iki sıra bırakır; tavan ile tepsi payı 72–308 dp arasında birebir tümler | `ReyonLayoutTest` |
| Kısa ekranda brif ve kural satırları okunur, taşan satır dokununca açılır | `ReyonShortScreenTest` |
| Uygulama açılışta doğrudan Reyon'a girer; dişli ayarları açar | `ReyonAppTest` |
| Metin kontrastı WCAG AA eşiğini tutar | `ThemeContrastTest` |

Yeni bir ölçüm bulgusu düzeltildiğinde, mümkünse **paylı bir değişmez** olarak
buraya eklenir: ölçülen değerin kendisi değil, altına düşülmemesi gereken sınır
iddia edilir.

## Gereksinimler

- USB hata ayıklaması açık bir Android cihaz ve `adb`
- Sürüm (ya da `assembleDebug`) derlemesi kurulu: `com.aripd.reyon`
- `tools/cihaz_testi.py` — kurulu yapının parmak izini basar, ekranı istenen
  dp boyutuna ayarlar, ekran görüntüsü alır ve erişilebilirlik ağacını döker

```bash
python3 tools/cihaz_testi.py kurulu_yapi        # sürüm + APK sha256
python3 tools/cihaz_testi.py reyon              # Reyon koşumu
```

> **Ölçüm bir kere yanlış yapıldı ve pahalıya mal oldu:** düzeltme koda girmişti
> ama cihazdaki APK eskiydi; sonuç "düzelmedi" diye okundu. O yüzden her koşum
> `kurulu_yapi` ile başlar ve kütüğe APK'nın sha256'sı yazılır. Gradle'ın
> "up-to-date" deyip eski APK'yı bırakması da aynı parmak iziyle yakalandı.

## A · Cihaz koşumu

Açılış, dört modun her birinde bir tur, geri dönüş, yeniden açılış. Aranan:
çökme yok (`logcat AndroidRuntime:E` boş), tur kaldığı yerden sürüyor, rekor
yazılıyor.

## B · Kare hızı

`dumpsys gfxinfo com.aripd.reyon` ile 12 saniyelik pencerede kare ölçümü.
Reyon sürekli animasyon çizmiyor; bakılan şey dokunma sonrası gecikme ve
liste kaydırmasının düzgünlüğü.

## C · Giriş kalibrasyonu

Dokunma hedefleri 48 dp'nin altına düşmemeli; ürün blokları, raf gözleri ve
kural satırları parmakla ıskalanmadan seçilebilmeli. Uzun basış (tepsiye geri
gönderme) yanlışlıkla tetiklenmemeli.

## D · Denge ölçümü

`./gradlew :engine:probe` raporu: üretim iş sayaçları, brif uzunluğu, satış
hedefinin tabana oranı, sipariş uzmanının kârı. Geçme/kalma vermez; sayılar
sürümler arasında karşılaştırılır.

## E · Erişilebilirlik

TalkBack açıkken: her düğmenin okunan bir adı var mı, raf gözleri konumlarıyla
anlatılıyor mu, sonuç kartı okunuyor mu. Kontrast CI'da denetleniyor
(`ThemeContrastTest`), burada bakılan şey ağaçtaki etiketler ve dokunarak
keşfin ekranın altına inebilmesi.

## F · Diller (cihaz)

14 dilin her birinde: metin taşması, kırpma, iki satıra bölünen düğme etiketi,
Arapça'da sağdan sola yerleşim ve rakamların Latin kalması. En dar durum
360×640 dp; ekran `wm size 1080x1920` + `wm density 480` ile oraya getirilir.

## Sonuç kütüğü

Ölçüm cihazı: SM-A515F (Galaxy A51), Android 13, 1080×2400 @420 dpi, 60 Hz.
Aşağıdaki kütükler Reyon ZA Games içindeyken alındı; ölçülen kod bugünkü
`engine/` ve `app/src/main/kotlin/com/aripd/reyon/ui/` ile aynıdır.

### Reyon · 2026-09-09

**D — adillik ve zorluk** (`./gradlew :games:reyon:probe`, 40 tohum/zorluk).
Diziliş için A–C cihazda henüz koşulmadı (v0.25.0 ile birlikte);
Sipariş'in cihaz koşumu aşağıda.

Üretici tek çözümü (`ReyonSolver.count == 1`) ve tahminsizliği (oyuncunun
gördüğü bilgiyle çalışan `ReyonDeducer` sonuna kadar gidiyor) üretim anında
garantiliyor; ipucu kümesi bu iki koşul korunarak en küçüğe indiriliyor.
Ölçülen, zorluk merdiveninin ne anlama geldiği:

| Zorluk | Raf | Ürün | Brif (sınır) | Verili | Yalnız tekil+ikili | +örtü | +kapasite | Tüm teknikler |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Kolay | 3×4 | 6,8 | 3,7 (6) | 1,4 | %0 | %98 | %100 | %100 |
| Orta | 4×5 | 9,6 | 7,5 (9) | 0,6 | %0 | %70 | %80 | %100 |
| Zor | 4×6 | 11,6 | 10,8 (12) | 0,0 | %0 | %63 | %88 | %100 |

Okuma: hiçbir bulmaca yalnız tekil ve ikili ipuçlarıyla bitmiyor; **örtü**
(her göz tam bir ürünle dolar: son ürün kalan boşluğa oturur) her seviyede
gerekli ve bu bilerek böyle — rafın tam dolması bulmacanın temel fikri.
Kolay'ın tamamı örtü ve kapasiteyle bitiyor; Orta'nın beşte biri, Zor'un
yaklaşık yedide biri **çoklu** teknik (marka dikey bloğunu birlikte
düşünme) istiyor. Merdiven gerçek: zorluk yalnız boyuttan değil, gereken
akıl yürütmeden geliyor.

İpucu karışımı ayarlandı: ilk ölçümde "üstünde" ipucu brifin %40'ını
kaplıyordu; ağırlıklar düşürülünce Orta'da %19'a indi ve planogram ilkeleri
(göz hizası, kategori bloğu, marka dikey, boy akışı, kategoriler ayrı, ağır
alt) brifin dörtte birinden fazlasına çıktı. Zor'da en sık ipucu artık
"yan yana" (%29).

Üretim bütçesi: deneme ort 2–7 (en kötü 26), çözücü düğümü ort 74–2626
(en kötü 56 bin), süre ort 2–16 ms (en kötü 67 ms, yalnız rapor).
`ReyonGeneratorTest.generationStaysWithinWorkBudget` sınırları 80 deneme ve
400 bin düğüm (gözlenenin 3–7 katı).

**D — Denetim modu** (`auditReport`, 40 tohum/zorluk, v0.26.0). Denetimde
adillik sorusu "sapma gerçekten görünür mü ve görünenden başka fark var mı"
diye sorulur; üretici her denetimde bunu doğrular (`ReyonAuditGenerator.verify`:
sapma maskeleri ayrık, plan ile raf yalnızca bu gözlerde ayrışır, her sapmanın
en az bir ayrışan gözü var). Ölçülen, tür karışımı ve incelik:

| Zorluk | Sapma | Tür karışımı | İnce sapma (marka/boy) | Sapma başına ayrışan göz |
| --- | --- | --- | --- | --- |
| Kolay | 2 | boş göz %44, yer değişimi %29, yabancı %28 | %0 | 1,98 |
| Orta | 3 | yer değişimi %23, yabancı %21, boş göz %19, marka %19, taşma %18 | %19 | 2,07 |
| Zor | 5 | boş göz %20, yer değişimi %18, yabancı %17, marka %16, boy %16, taşma %14 | %32 | 2,12 |

Okuma: Kolay yalnızca bariz sapmalarla (boş göz, yer değişimi, yabancı ürün)
oynanıyor; Orta marka ve taşmayı, Zor boyu ekliyor ve sapmaların üçte biri
"ince" oluyor (yalnızca renk şeridi ya da boy noktası değişir). Zorluk
merdiveni sapma sayısından çok sapmanın inceliğinden geliyor. Sapma başına
ayrışan göz sayısı 2 civarında: her sapma en az bir, çoğunlukla iki gözde
görünür.

**D — Satış modu** (`salesReport`, 40 tohum/zorluk, v0.27.0). Satışta adillik
sorusu "hedef dürüst mü" diye sorulur: hedef, tavlamalı yerel aramanın
(eşit genişlik takası, raf içi komşu takası, raf takası, ürün↔eşit genişlikli
koşu takası; 4 yeniden başlatma) bulduğu en iyi puan. Ölçülen: hedefin
başlangıç planını ne kadar geçtiği, puanın kurallara dağılımı ve iyileştiricinin
farklı tohumla aynı hedefi bulup bulmadığı.

| Zorluk | Taban ort | Hedef ort | Kazanç | Konum | Tamamlayıcı | Çakışma | Kategori | Marka | Yeniden koşum sapması | Süre |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Kolay | 89 | 96 | %8 (0–32) | %65 | %6 | %−1 | %14 | %16 | %0,0 | 18 ms |
| Orta | 132 | 151 | %15 (3–29) | %63 | %7 | %−2 | %12 | %20 | %0,0 | 36 ms |
| Zor | 172 | 189 | %11 (1–23) | %61 | %6 | %−1 | %13 | %20 | %0,0 | 70 ms |

Okuma: iyileştirici farklı tohumla 10 turun 10'unda **aynı** hedefi buluyor;
bu boyutta arama uzayı küçük, hedef büyük olasılıkla gerçek en iyi. Yani
"hedefi geç" nadir ama mümkün bir olay değil, hedef bir tavan. Puanın
%61–65'i konumdan (talep × yüz × raf çarpanı), üçte biri komşuluk
kurallarından geliyor: önce doğru rafa koymak, sonra komşuluğu düzeltmek
diye okunur. Çakışma payı hedefte bile sıfır değil: raf genişlikleri bazen
temizlik ile gıdayı aynı rafa zorluyor. Üretim süresi masaüstünde en çok
70 ms; telefonda arka planda hesaplanır, dönen gösterge var.

**E — küçük ekran** (v0.27.1, masabaşı geometri + Robolectric). Cihaz
koşumundan önce yerleşim hesabı iki sorun gösterdi. (1) Blok adı puntosu blok
yüksekliğinden türetilip 8 sp tabanına dayanıyordu; 360 dp genişlikte Zor
rafında blok 33 dp, Denetim planında 26 dp yüksekliğinde kalıyor, "Bulaşık
deterjanı" gibi adlar tek yüzlü gözde (yazı alanı ~41 dp) üç noktayla
kırpılıyordu. (2) Denetimde iki tuval sabit en-boy oranıyla diziliyordu;
640 dp yükseklikte Orta rafı sığmayınca raf tuvali daralıp sola yaslanıyor,
bulunanlar listesi sıfır yükseklik alıyordu. Düzeltme: ad, bloğa
sığdırılıyor (en büyük puntodan başlayarak tek satır, sonra boşluktan iki
satır, en küçük puntoda %72'ye kadar yatay daraltma, üç nokta en son;
`BlockLabeler.fit`); Denetim yerleşimi yükseklik bütçesinden hesaplanıyor
(raf satırı göz genişliğinin 0,62–0,90 katı, plan satırı rafın 0,85'i; taban
katsayıda bile sığmazsa iki tuval birlikte daraltılıp ortalanıyor;
`auditLayout`) ve plana dokununca büyütülmüş plan açılıyor. 360 dp'de Zor
planında en dar durum yazı alanı 45 dp, ad bölgesi 19 dp, 8 sp; 35 adın hepsi
(TR ve EN) bu alanda kırpılmadan sığıyor (`ReyonBlockLabelTest`, gerçek yazı
ölçümüyle). Cihazda doğrulama Sipariş koşumunda yapıldı: blok adları 360 dp'de
kırpılmıyor, ama menü kartının kendisi ekrandan taşıyor (aşağıda, bulgu 1).

**D — Sipariş modu** (`orderReport`, 40 tohum/zorluk, v0.28.0). Siparişte
adillik sorusu "hedef dürüst mü ve ulaşılabilir mi" diye sorulur. Hedef,
oyuncunun gördüğü bilgiyle (tahmin ortası ve aralık) çalışan uzman
politikanın aynı haftadaki kârı; ölçüm onu üç referansla karşılaştırır:
hiç sipariş vermeyen (yalnız başlangıç stoğunu satan), tahmin ortasını
emniyetsiz karşılayan naif politika ve gerçekleşen talebi bilen kâhin
(oyuncu bilmez; üst sınır).

| Zorluk | Gün | Ürün | Sipariş yok | Naif | Uzman | Kâhin | Uzman/kâhin | Kayıp satış | Hizmet | Devir (kâhin) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Kolay | 5 | 6,7 | 101 | 319 | 319 | 321 | %99 | %4,1 | %95 | 8,1 (7,1) |
| Orta | 6 | 9,7 | 152 | 616 | 613 | 629 | %98 | %6,9 | %92 | 11,2 (9,5) |
| Zor | 7 | 11,6 | 185 | 901 | 889 | 921 | %96 | %7,9 | %92 | 13,1 (11,6) |

Okuma: uzman, tam bilgili kâhinin %96–99'una geliyor; yani tahmin
belirsizliğinin bedeli küçük ve hedef bir tavan değil, ulaşılabilir bir
çıta. Naif politika (yarının tahmin ortasını karşıla, emniyet ve sığma
düşünme) uzmanla başa baş (±%1): parametre taraması (emniyet payı 0–1,5 ×
aralık, sipariş eşiği, sığma payı) kâr yüzeyinin tepede düz olduğunu
gösterdi; uzmanın ayırt edici yanı daha yüksek devir (aynı kâra daha az
stokla). Kârın bileşimi uzmanda: bekleme marjın ~%14'ü, iade %1'in altı,
fire sıfıra yakın (bozulan ürünler yalnızca fazla siparişi cezalandırır;
bu yüzden raf ömürleri 2–4 güne çekildi). Kayıp satış %4–8: tahmin
aralığı ±%25–35 iken bir kısım talep kaçınılmaz olarak karşılanamıyor,
hizmet düzeyi %92–95. Üretim süresi ölçülemeyecek kadar kısa (uzman
simülasyonu dahil 1 ms altı). Cihaz koşumu (A–C) aşağıda.

### Reyon · Sipariş cihazda · 2026-09-10

A–C aşamaları SM-A515F'te, sürüm derlemesiyle ve **Serbest** modda koşuldu.
Sürücü, `uiautomator` dökümünden okuyup dokunan yerel bir betik: siparişleri
oyuncunun gördüğü bilgiyle (stok, tahmin aralığı, koli boyu) veren naif
politika — "yarının tahmin ortasını karşıla, rafa sığdır".

**A — koşum.** Orta zorlukta iki hafta baştan sona oynandı. İlk koşumda politika
yalnızca ekranda duran satırları gördüğü için listenin üstündeki ürünler hiç
sipariş edilmedi: 357/675 kâr (**%52**), hizmet düzeyi %45. İkincisinde liste
her günün başında başa sarıldı ve on ürünün hepsi karşılandı: **731/797 kâr
(%91)**, ★★☆, stok devri 16,5 (uzman 13,1), hizmet %83 (uzman 93). Yani hedef
cihazda da ulaşılabilir bir çıta; ölçüm koşumunun "naif politika uzmanla başa
baş" sonucu parmakla da doğrulanıyor ve kaybın tamamı **taranmayan raftan**
geliyor, karardan değil.

Gün kapanışı kartı, hafta sonucu kartı, rekor yazımı ("Yeni rekor!"), geri
tuşuyla hub'a çıkış, arka plana alıp geri dönme ve **süreç ölümü**
(`am force-stop`) sınandı: hafta, gün ve bekleyen koliler üçünde de aynen geri
yüklendi. `logcat` `AndroidRuntime:E` boş, çökme yok (kalan tek uyarı
`OnBackInvokedCallback` etkin değil bilgisi; öngörülü geri kullanılmıyor).
Sipariş'in günlük modunda deneme hakkı yoktur — günün en iyi kârı kaydedilir —
yani ölçüm hak tüketmez.

**A — bulgu 1: 360 dp'de menü kartı ekrandan taşıyor (engelleyici).**
`wm size 1080x1920` + `wm density 480` (sw360dp, uygulamaya 562 dp yükseklik)
ile Reyon menü kartının altı ekranın dışında kalıyor: zorluk açıklaması, mod
bilgisi, **"Haftaya başla"** ve "Menüye dön" ne dökümde görünüyor ne
dokunulabiliyor. Kart kaydırılamadığı için mod **hiç başlatılamıyor**. Dört
türün hepsinde aynı (`OverlayCard`'ın yükseklik bütçesi yok, içerik
kaydırılmıyor); Sipariş en kötüsü, brifi 360 dp'de dokuz satır. Oyun ekranının
kendisi 360 dp'de sağlam: raf tuvali, gün başlığı (iki satıra sarıyor), sipariş
listesi ve alt eylem satırı sığıyor — bir buçuk satır görünüp liste kayıyor.

**A — bulgu 2: "Denetim" çipi 360 dp'de kırpılıyor.** Dört tür çipi satırı eşit
paylaştığı için 360 dp'de ikinci çip **"Deneti"** diye kesiliyor, üç nokta da
konmuyor (`KindChips`, `compact = true`). 411 dp'de dördü de tam sığıyor.

**A — bulgu 3: alt eylem satırı erişilebilirlik ağacında sınırsız.** "İpucu" ve
"Günü kapat" düğümleri ağaçta var ama sınırları `[0,0][0,0]`. Sebep: uygulama
sistem çubuklarını gizliyor (`hide(systemBars())`) ve 2400 px'in tamamına
çiziyor, oysa pencerenin bildirilen uygulama sınırı **2186 px**'te bitiyor (üç
düğmeli gezinme şeridi). 2186'nın altına düşen her şeyin sınırı sıfırlanıyor:
kural metni tam orada kırpılıyor, eylem satırı bütünüyle altta kalıyor. Sonuç,
TalkBack'in dokunarak keşfi bu iki düğmeye inemez ve
`tools/cihaz_testi.py erisim` onları **sessizce atlar** — sıfır sınırlı
düğümleri elediği için bu ekran "10 dokunulabilir öğe, etiketsiz 0" diye temiz
görünüyor. Gözle ve parmakla düğmeler çalışıyor (y = 2306'ya dokunmak günü
kapatıyor); sorun ölçümün ve ekran okuyucunun onları görmemesi.

**B — kare hızı** (sürüm derlemesi).

| Pencere | Kare | Okuma |
| --- | --- | --- |
| boşta 15 s, dokunmadan | **0** | olay güdümlü; beklenen sonuç |
| liste kaydırma, 10 × 700 ms sürükleme | 468 / 8,0 s | pencere ortalaması 58,5 kare/s |
| adımlayıcıya 16 durum değiştiren dokunuş | 76 / 1,5 s | dokunuş başına ~4,8 kare |

Kaydırma penceresinde `framestats`: kesintisiz çizim aralığı ortanca **16,6 ms
= 60,1 kare/s**, kaçan vsync **0**, jank %6,8, p50 20 ms. Pencere ortalamasının
58,5'te kalması kare düşmesi değil, sürüklemeler arasındaki çizim boşlukları
(119 aralıktan 4'ü 25 ms üstü ve hepsi sürükleme sınırında). Adımlayıcıda p50
22 ms, kaçan vsync 0; dokunuş başına birkaç kare düğme dalgacığı.

**C — giriş kalibrasyonu.** Sipariş'te sürükleme yok; kalibre edilecek şey koli
adımlayıcısının **dokunma hedefi**. Çizilen düğüm 44,2×32,0 dp (semantik sınır
= çizilen sınır, E3'teki uyarı). Gerçek dokunma bandı, düğüm merkezinden dp
kaydırarak dokunup koli sayacına bakarak ölçüldü (her denemede satır sıfırlanıp
tek dokunuş):

| Eksen | ±20 dp | ±22 dp | ±24 dp | ±26 dp |
| --- | --- | --- | --- | --- |
| yatay | ✓ | ✓ | ✓ | — |
| dikey, yukarı | ✓ | ✓ | ✓ | — |
| dikey, aşağı | ✓ | — | — | — |

Etkin dokunma alanı **48 dp geniş × 44 dp yüksek** (24 dp yukarı, 20 dp aşağı).
Yatayda Material'ın 48 dp tabanı tutuyor; dikeyde 4 dp eksik, çünkü büyütme
satır kartının alt kenarında kesiliyor — düğüm kartın alt kenarının yalnızca
6 dp üstünde duruyor. Bu yoğunlukta 48 dp = 7,6 mm, 44 dp = 7,0 mm.

Işkalayan dokunuş komşu ürünü oynatmıyor: bandın dışına dokunmak hiçbir sayacı
değiştirmedi, yani hatanın bedeli yalnızca bir kez daha dokunmak.

Hızlı dokunuş sadakati: arka arkaya (beklemesiz) üç dokunuş üç kez denendi, her
seferinde 0→3 koli ve 3→0 koli — düşen dokunuş yok.

**Düzeltmeler (v0.28.1).** Bulgu 1: `OverlayCard` kabın yüksekliğine sığmazsa
içi kayıyor (yükseklik sınırsızsa kaydırma eklenmez); dört Reyon brifi
kısaltıldı. Robolectric 360×640'ta Sipariş menüsünde "Haftaya başla" ve "Menüye
dön" kaydırılıp görünür alana geliyor (`ReyonAuditLayoutTest`). Bulgu 2:
`KindChips` kart içi genişlik 280 dp'nin altındaysa (411 dp'de 299, 360 dp'de 248) 2×2 diziliyor, çip metni
sığmazsa üç nokta; 411 dp'de tek satır korunuyor. Bulgu 3: dokunarak keşif
açıkken uygulama sistem çubuklarını gizlemiyor (`MainActivity`, dinleyiciyle
canlı), içerik çubukların üstünde kalıyor; `cihaz_testi.py erisim` sıfır
sınırlı dokunulabilir düğümleri sayıp listeliyor ("sınırı sıfır … 0"
beklenir). C: sipariş satırının alt payı 10 dp, adımlayıcının 48 dp dokunma
alanı kartın kırpma sınırında kesilmiyor. Cihazda doğrulanacaklar: TalkBack
açıkken `erisim` çıktısında sıfır sınırlı düğüm 0; adımlayıcı dikey bandı
±24 dp; 360 dp'de menü kaydırılarak başlıyor.

**Doğrulama turu (v0.28.1, aynı cihaz, kullanıcının kendi sürüm derlemesi).**

- **Adımlayıcı bandı ✅.** İki eksende de ±24 dp kayıt alıyor, ±26 dp almıyor:
  etkin dokunma alanı **48×48 dp** (önce 48×44). Satır kartının alt payı 9,9 dp
  ölçüldü; büyütme artık kartın kenarında kesilmiyor.
- **360 dp'de menü ✅.** Kart kayıyor ve dört türün başlatma düğmesi de kaydırma
  sonrası geliyor: Sipariş "Haftaya başla" (dokunuldu, hafta başladı), Diziliş
  "Başla", Denetim "Denetime başla", Satış "Dizmeye başla" — "Menüye dön" de
  erişilebilir. Tür çipleri 360 dp'de 2×2 diziliyor ve dört ad da tam;
  "Denetim" artık kırpılmıyor.
- **TalkBack açıkken `erisim` ⚠️ yarım.** Dokunarak keşif açılınca uygulama
  çubukları gerçekten gösteriyor (ekranda doğrulandı, içerik çubukların üstünde)
  ve tarayıcı sıfır sınırlı düğümleri artık sayıp listeliyor. Ama sayı **0
  değil, 2**: "İpucu" ve "Günü kapat" TalkBack açıkken de `[0,0][0,0]` geliyor.

  Ölçülen sebep, çubukları göstermenin çözemediği bir çerçeve kayması: çubuklar
  görünürken uygulama alanı ekranda y = 88…2274, erişilebilirlik pencere
  dikdörtgeni ise **(0,0)–(1080,2186)** — yani uygulama sınırının *boyutu*
  (2274 − 88) y = 0'a çakılmış. Alanın **son 88 px'i (33 dp)** ağaçtan düşüyor;
  eylem satırının dolgusu tam orada (ölçülen y = 2190…2266), kural metni de
  2186'da kırpılıyor. Bu ROM'da çare çubukları göstermek değil, **son 33 dp'ye
  dokunulabilir öğe koymamak**: dokunarak keşif açıkken alta durum çubuğu
  kadar ek pay vermek ya da eylem satırını kural metninin üstüne almak.

  **Düzeltme (v0.28.2):** dokunarak keşif açıkken uygulama kökü alta durum
  çubuğu yüksekliği kadar pay veriyor (`MainActivity.ExplorationInset`); içerik
  2186 px'te bitiyor, eylem satırı bandın üstünde kalıyor. Cihazda
  doğrulanacak: TalkBack açıkken `erisim` → "sınırı sıfır … 0".

**Hafta grafiği (v0.28.2, cihazda).** Kolay bir hafta sonuna kadar oynanıp sonuç
kartı 411 dp ve 360 dp'de bakıldı: çubuklar (1. gün +32, 2. gün +26, kalan üç
gün 0), biriken devir çizgisi kartın yazdığı sayıya varıyor (12,5), uzman devri
kesikli çizgide (3,9). 360 dp'de kart kayıyor; grafik de altındaki düğmeler de
okunuyor. Ekran okuyucu açıklaması günleri kârıyla veriyor: "Hafta grafiği:
1. gün +32, 2. gün +26, … · stok devri 12,5 (uzman 3,9)". Çökme yok, `logcat`
temiz.

### v0.43.2 · Reyon kısa ekran yerleşimi · 2026-09-20

v0.43.1'in açık kalan bulgusu kapatıldı: 360×640 dp'de Diziliş'in planogram
brifi çizilmiyordu.

**Sebep.** Üç tür (Diziliş, Satış, Sipariş) aynı iskelete oturuyor: üstte raf
tuvali, altında kaydırılabilir panel, en altta tepsi. Tuval `fillMaxWidth()` +
`aspectRatio()` ile ölçülüyordu, yani yüksekliğini genişlik belirliyordu; sütunda
ağırlıksız olduğu için de yüksekliği önce o alıyordu. Panel `weight(1f, fill =
false)` ile artandan besleniyor. 360 dp genişlikte tuval 175–210 dp, tepsi 12
ürünle 270 dp ediyor; oyun alanı ~448 dp olduğu için panele ~26 dp kalıyordu —
ölçülen 13 dp'lik kural satırı ve sıfır erişilebilirlik kutusu bu.

**Düzeltme.** Oyun alanı `BoxWithConstraints`'e alındı; tuval en çok alanın
`SHELF_SHARE` = %40'ını alıyor. Kalan yükseklik panel ile tepsi arasında
bölünüyor: tepsi, panele `PANEL_MIN` = 140 dp bırakacak kadar yer alıyor
(`ReyonLayout.kt`), taşan kısmı kendi içinde kayıyor. "Kalan" tahmin edilmiyor,
ölçülüyor — panel ile tepsi kendi `BoxWithConstraints`'inin içinde durduğu için
aradaki ipucu/döküm satırı hesaba kendiliğinden giriyor. Panelin tabanı böylece
başlık + üç satır. Tavan **en boy oranı
değiştirilerek** uygulandı, çünkü genişlik `fillMaxWidth()` ile sabitken
`heightIn(max = …)` ile `aspectRatio(…)` birlikte çalışmıyor — oran hiçbir boyutu
kısıtı sağlayacak şekilde bulamayınca kısıtı yok sayıp yine genişlikten
hesaplıyor (ilk denemede tavan bu yüzden hiç bağlamadı). Tuval tam genişlikte
kalıyor, kısa ekranda gözler basıklaşıyor; çizim de dokunma da tuvalin ölçülen
boyutundan türediği için (`ShelfGeom(size.width, size.height, …)`) eşleme
bozulmuyor. Uzun telefonda iki tavan da doğal yüksekliğin üstünde kaldığı için
411 dp'de yerleşim aynen sürüyor.

`ReyonShortScreenTest` üç modu 360×640 dp'de ölçüyor; kırpılmış kutulara bakıyor,
yani cihazın erişilebilirlik ağacında gördüğü değerlere.

**Cihazda ölçülecek** (360×640 dp, üç mod). Ölçüm tek komutla:

```
python3 tools/cihaz_testi.py reyon --apk za-v0.43.2.apk
```

Betik APK'yı kurar, ekranı 360×640 dp'ye alır (`wm size 720x1280` + `wm density
320`), üç modu sırayla açar ve rafın, panelin, tepsinin kutularını dp olarak
yazar; sonra ekranı sıfırlayıp aynı ölçümü cihazın kendi çözünürlüğünde yineler
(G6). Panel satırlarının kaçının görünür olduğunu da sayar — kırpılan satır 0 dp
gelir, cihazın erişilebilirlik ağacında göründüğü gibi.

| # | Ne | Beklenen |
| --- | --- | --- |
| G1 | Diziliş'te brif kural satırı | ≥ 20 dp, kaç kural okunuyor (hedef üç); kaydırma kalanları getiriyor |
| G2 | Raf gözündeki ürün adı | kırpılmamış (üç nokta yok), en dar göz Zor planında |
| G3 | Dokunma eşlemesi | tepsiden seçilen ürün dokunulan göze yerleşiyor (basıklaşan tuvalde de) |
| G4 | Satış'ta puan kuralları | en az üç kural okunuyor (v0.43.1'de beşten ikisi görünüyordu) |
| G5 | Sipariş listesi | ilk ürün satırı tam görünüyor, kalanına kaydırmayla ulaşılıyor |
| G6 | 411 dp | üç modun yerleşimi v0.43.1 ile aynı (raf yüksekliği değişmemiş) |

Ölçülecek iki sayı: tuval payı `SHELF_SHARE` = %40 ve panel tabanı `PANEL_MIN`
= 140 dp. G1 tutmazsa taban yükseltilir; G2 kırpılma gösterirse tuval payı
yükseltilir (blok etiketi 8 sp'ye kadar iniyor, ad bölgesi ~19 dp'nin altında üç
nokta çıkıyor) — ikisi aynı yükseklikten besleniyor, yani biri artınca öbürü
azalıyor; 360×640 dp'de tepsi kaydırılarak yer açılıyor.

`ReyonShortScreenTest` brifi üç uygulama alanı yüksekliğinde ölçüyor: 640, 568
(cihazda 360×640 dp ekranın uygulama alanı, bulgunun geldiği ölçü) ve 480 dp.
Ölçtüğü, yerleşimin gerçekten söz verdiği şey: panel ya tabanını (140 dp) almış
olur, ya da içeriği tabandan kısa olduğu için hiçbir satırı kırpmaz — ve içinde
en az bir kural çizilmiştir. İki kollu, çünkü panel `weight(1f, fill = false)`
ile duruyor: bırakılan yerden fazlasını almıyor ama içeriğinden de büyümüyor.
Tabanda 4 dp pay var: taban iç içe iki ölçüm geçişinden geçtiği için px/dp
yuvarlaması birkaç dp yiyor — Satış'ta 140 dp hedefiyle 138 dp ölçüldü.

Beklenen taban aslında iki tabanın küçüğü, çünkü panel ile tepsi aynı kalandan
besleniyor ve tepsinin de bir tabanı var (`TRAY_MIN` = 72 dp): kalan ikisine
birden yetmezse panele `kalan − 72` düşüyor. Ölçülen: 480 dp'lik uygulama
alanında kalan 177,5 dp, tepsi 72 dp, panel 105,5 dp — dört kuraldan üçü
görünüyor, dördüncüsü kaydırmayla geliyor. Bu bilinçli: ürün seçilemeyen bir
tepsi de bulmacayı çözülemez yapar. 568 ve 640 dp'de kalan ikisine yetiyor ve
panel tam tabanını alıyor.
Panele kaç kural sığdığı üretilen ipucu metninin kaç satıra sardığına bağlı —
bulmaca her koşumda yeniden üretildiği için satır saymak kararsız, bir koşumda
tam bu yüzden kırıldı. G1 o yüzden cihaza kalıyor: gerçek metinle kaç kural
okunuyor?

**Açık madde — Sipariş'in listesi gün başlığına sıkışıyor.** Robolectric ölçümü:
568 dp'lik uygulama alanında listenin görünen kısmı ~112 dp, yani bir ürün satırı
(ikincisi 0,5 dp'ye iniyor). Tuvalin payı burada suçlu değil — tuval 156 dp,
tavanın (179 dp) altında. Yüksekliği yiyen, tuval ile liste arasındaki gün
başlığı: gün, tahmin, teslimat ve ipucu satırlarıyla ~180 dp. Liste kaydığı ve
her satıra ulaşıldığı için mod oynanabilir, ama tek satır dar. Bu sürümün
kapsamında değil; başlığın sıkıştırılması (ya da katlanması) ayrı bir iş, cihazda
411 dp'de sorun görünmediği için de aceleci davranmamak doğru. Cihazda 360×640
dp'de kaç satır göründüğü ölçülürse iş için sayı elde edilir.

### Reyon Satış paneli · toplu cihaz raporu · 2026-09-21

Bu bölüm G4 çevresindeki bütün cihaz koşumlarını tek yerde topluyor; ayrıntılar
yukarıdaki tarihli bölümlerde. Cihaz **SM-A515F**, yazı ölçeği **1,1** (aksi
yazmadıkça), kısa ekran `wm size 1080x1920` + `wm density 480`. Her koşumda
ölçülen yapının `base.apk` özeti yerel APK ile karşılaştırıldı.

**Nereden nereye.**

| Yapı | 360×640 dp'de okunan kural | 411 dp'de okunan kural |
| --- | --- | --- |
| v0.43.1 | panel çizilmiyor (26 dp) | 5/5 |
| v0.43.2 | 2/5 | 5/5 ya da 4,5/5 (tepsiye göre) |
| v0.43.3 (`maxLines = 2`) | **3/5**, `Konum` gövdesi üç noktalı | aynı |
| + açılan satır | 3/5, kesilen gövde dokununca tam | aynı |
| + tepsi tavanı / içerikten pay | 3/5 | **5/5, tura ve yazı ölçeğine bakmadan** |

**Bugünkü yapı** (`1c1432b`, `sha256=27f8d21f…`) üç dilde ölçüldü.

| Ekran | Dil | Panel | 1. kuralın gövdesi | Okunan kural |
| --- | --- | --- | --- | --- |
| 411 dp | tr | 232,4 dp | 32,0 dp | **5/5** |
| 411 dp | de | 232,4 dp | 32,0 dp | **5/5** |
| 411 dp | fi | 232,4 dp | 32,0 dp | **5/5** |
| 360×640 dp | tr | 140,0 dp | 31,0 dp | **3/5** |
| 360×640 dp | de | 140,0 dp | 35,3 dp | **2/5** |
| 360×640 dp | fi | 140,0 dp | 35,3 dp | **2/5** |

**Bulgu 1 — G4'ün hedefi yalnız Türkçe'de tutuyor.** Kısa ekranda Almanca ve
Fince'de üçüncü kuralın adı 5,3 dp'ye iniyor. Sebep ekran görüntüsünde görünüyor:
Almanca'da **ikinci** kuralın gövdesi de iki satıra sarıyor (`Paare wie Chips und
Dip nebeneinander +6, / übereinander +3`), Türkçe'de tek satır. Buna birinci
kuralın gövdesindeki 4,3 dp eklenince 140 dp'lik tabanın 2 dp'lik payı bitiyor.
Planın "payı 2 dp, dar" uyarısı dilde gerçekleşmiş. Ok her iki dilde de doğru
yerde çıkıyor ve dokununca gövde tam açılıyor, yani içerik ulaşılabilir; kayıp
üçüncü kuralın **adının** kaydırmasız okunması.

**Düzeltildi (ölçüm bekliyor): panel tabanına sıkışmışken gövdeler tek satır.**
Satır sayısı artık panelin payına bağlı — `rulesAreCompact(rest)`, yani tavan
tabana çakılı mı (`rest` ≤ 308 dp). Eşik ayrı bir sayı değil, `panelCap`'in
kendisi; uzun ekranda gövdeler iki satır kalıyor, 411 dp'de hiçbir şey
değişmiyor.

Cihazın kendi sayılarıyla beklenen: gövdesi iki satır olan kural 50,7 dp yerine
35,2 dp tutuyor, panel başlığı ~36 dp, yani üçüncü kuralın adı 106–124 dp'ye
düşüyor — **dile bakmadan** 140 dp'nin içinde. Almanca ve Fince'de 2/5 olan
sayının 3/5'e çıkması, Türkçe'de değişmemesi bekleniyor. Bedeli kısa ekranda
her açıklamanın tek satıra inmesi; ok o satırlarda da çıkıyor ve dokunmak gövdeyi
tam açıyor, yani metin kaybolmuyor.

Tepsi boşalınca sıkışma kalkıyor (panel kalanın hepsini aldığı için gövdeler iki
satıra dönüyor) — kuralların en çok okunduğu an orası.

**Bulgu 2 — tepsi boşalınca tavan kalkmıyor.** Tavan `st.finished`'a bağlı, ama
bütün ürünler rafa konduğunda tepsi yalnız tek satırlık `reyon_tray_empty`
notunu çiziyor ve `st.finished` hâlâ `false`; `panelCap` tepsiye `TRAY_KEEP`
ayırmayı sürdürüyor. Ölçülen:

| Uygulama alanı | Panel | Okunan kural | Altındaki boşluk |
| --- | --- | --- | --- |
| 563 dp (360×640) | ~128,7 dp | 3/5, `Çakışma`'nın gövdesi ortadan kesik | ~83 dp |
| 480 dp | ~105 dp | 2/5, `Tamamlayıcı`'nın gövdesi ortadan kesik | ~83 dp |

411 dp'de kural kaybı yok (içerik zaten sığıyor), yalnız aynı boşluk kalıyor.
Oyuncunun kuralları okumak için en çok durduğu an tam bu.

**Düzeltildi (ölçüm bekliyor).** Kol artık `st.finished`'a değil "tepside
yerleştirilecek ürün kaldı mı"ya bakıyor — tepsinin çizdiği şeyin ta kendisine:

```kotlin
val trayPending = st.sales.products.any { !st.isPlaced(it.id) }
// ürün varsa  → panel ağırlıksız (heightIn(max = panelCap)), tepsi kalanı alır
// ürün yoksa  → panel ağırlıklı (weight(1f, fill = false)), tepsi notu kadar yer
```

Ürün kalmayınca kol Diziliş'inkine dönüyor: tek satırlık not doğal boyunu alıyor,
panel kalanın hepsini kullanabiliyor. Tur bitmiş hâl (tepsi hiç çizilmiyor) de
aynı kola düşüyor, yani eski `st.finished` özel durumu ayrıca gerekmiyor.
Beklenen: 563 dp'de panelin ~128,7 dp'den içeriğinin tam boyuna çıkması,
480 dp'de ikiden fazla kuralın okunması ve iki ekranda da ~83 dp'lik boşluğun
kapanması; ürün dururken hiçbir sayının değişmemesi.

Bu durum CI'da sınanmıyor: bütün ürünleri rafa koymak için tuvale koordinat
koordinat dokunmak gerekiyor ve o dokunuşlar raf geometrisine bağlı — kırılgan
bir test olurdu. Doğrulaması cihazda.

**Doğrulananlar.** 411 dp'de panel üç turda da 232,4 dp — tepsiye göre 240 ↔ 208
salınımı bitti. Yazı ölçeği 1,3'te içerikten gelen pay 254,1 dp alıp beş kuralı
tam tutuyor, sabit `PANEL_WANT` = 240'lı yapı ise 240,0'a çakılıp beşinci kuralı
14,1 dp'ye indiriyordu. 480 dp'lik uygulama alanında tepsi tabanını koruyor (iki
ürün, 29,7 dp, dokunuş seçimi alıyor). Kısa ekranda Türkçe sayılar değişiklik
boyunca hiç kıpırdamadı: 17,7 · 17,7 · 17,7 dp, gövde 31,0 → dokununca 48,7 →
ikinci dokunuş kapatıyor.

**Araç düzeltmesi — `reyon --olcek 2.0` artık çalışıyor.** Varsayılan ölçek üç
türde de "Reyon açılamadı" veriyordu. Sebep `oyunu_ac`'taki 250 **piksel**lik
yakınlık eşiği: 320 dpi'de 125 dp ediyor ve "son oynananlar" şeridindeki Reyon
adı ile ilk oyun kartının Oyna düğmesi 248 px uzakta düşüyor, yani tarama Reyon
yerine Blok'u açıyor; Blok'ta geri tuşu duraklatma katmanını açtığı için 14
denemenin hepsi orada sıkışıyordu. Eşik dp'ye çevrildi (95 dp). Ölçek 2.0 ile
alınan sayılar 3.0'la aynı çıkıyor (raf 167,0 / 156,0 dp).

`logcat AndroidRuntime:E` bütün koşumlarda boş. Ekran ayarları, yazı ölçeği ve
arayüz dili koşumlardan sonra geri alındı.

### v1.0.1 · A–F cihaz koşumu ve Play ekran görüntüleri · 2026-09-23

Ölçülen yapı yayındaki APK: `com.aripd.reyon 1.0.1
sha256=c3d46e0cd7e2b0f73ff29252ffe0fae4aacacac4330773985c0fabcc0e0b54a3`
(`kurulu_yapi`; özet Release'teki `SHA256SUMS.txt` ile aynı). Cihaz SM-A515F,
Android 13, yazı ölçeği 1,1. Uygulama artık tek başına: açılış doğrudan
Reyon, hub yok.

**Araç düzeltmesi — `reyon` Denetim'i ölçemiyordu.** Başlatma etiketi
"Denetlemeye başla" diye aranıyordu (uygulamadaki metin "Denetime başla"), raf
ön ekleri arasında "Denetim rafı" / "Audit shelf" yoktu. İkisi düzeltilince dört
mod da ölçülüyor.

**Yerleşim** (`reyon --ekran 360x640`, ölçek 2,0; sayılar dp):

| Mod | Raf 360×640 | Panel 360×640 | Raf 411 dp | Panel 411 dp |
| --- | --- | --- | --- | --- |
| Diziliş | 336 × 167,0 | 3 kural, 32,5 dp, 3/3 görünür | 387 × 226,3 | 3 kural, 3/3 |
| Denetim | 336 × 190,5 | plan 161,5 dp | 387 × 261,3 | plan 222,1 dp |
| Satış | 336 × 167,0 | başlık y = 343 | 387 × 226,3 | başlık y = 406 |
| Sipariş | 336 × 156,0 | 2 satır (32,0 + 14,5) | 387 × 179,8 | 4 satır, 4/4 |

Raf yükseklikleri v0.43.x kayıtlarıyla aynı (167,0 / 156,0; 411 dp'de 226,3 /
179,8): G6 tutuyor.

**A — koşum ✅.** Dört modun her birinde Kolay/Serbest bir tur sonuna kadar
oynandı: Sipariş 266/327 kâr ★☆☆ "Yeni rekor!", hafta grafiği çizildi; Diziliş
0:44 "Tebrikler!" (5 ipucu, rekor yazılmadı — ipuçlu tur); Denetim 2/2, 0 hata,
"Yeni rekor!"; Satış 95/95 ★★★ "Yeni rekor!" (tepsi boşalınca beş kural da
okunuyor: 2026-09-21 Bulgu 2'nin düzeltmesi cihazda doğrulandı). Yarım tur arka
plana alıp dönmede ve `am force-stop` sonrasında aynen geri geldi. Geri tuşu
turdan doğrudan uygulamadan çıkıyor (`ReyonApp`: ana ekranda `onExit = onQuit`);
tur saklandığı için yeniden açılışta kaldığı yerden sürüyor. `logcat
AndroidRuntime:E` bütün koşum boyunca boş.

**B — kare hızı** (12 s pencereler).

| Pencere | Kare | p50 / p90 / p99 | Kaçan vsync | Takılma |
| --- | --- | --- | --- | --- |
| Diziliş turu, dokunmadan | 12 (1/s, süre sayacı) | 34 / 36 / 36 ms | 0 | — |
| Sipariş listesi, 16 sürükleme | 676 = 56,3 kare/s | 18 / 22 / 32 ms | 1 | %8,9 |
| Adımlayıcı, 24 dokunuş | 481 = 40,1 kare/s | 22 / 23 / 30 ms | 0 | %3,5 |

`fazlar` kaydırmada: toplam ortanca 18,0 ms, bunun 13,4 ms'si GPU; çizim kaydı
0,4 ms. Maliyet çizim kodunda değil dolguda. Boştaki kareler saniyede bir süre
sayacı; takılma sayılmaları 34 ms'lik tek karelerden.

**C — giriş kalibrasyonu.** Menüde ve dört modda dokunulabilir düğümler tarandı
(cihazın kendi ekranı): düğmeler ve çipler ≥ 48 dp (geri oku 47,6 dp = 125 px,
yuvarlama). 48 dp'nin altında kalan iki satır türü var:

- Diziliş brif satırları **32,4 dp** yüksekliğinde ve dokunulabilir (taşan satırı
  açar); bitişik diziliyorlar, ıska komşu satırı açar — bedeli bir dokunuş.
- Satış kural satırları **38,1 dp** (sonuncusu 43,0).

Tuval gözü en dar durumda (Zor 4×6, 360×640 dp) **56 × ~35 dp**. Dokunma eşlemesi
ölçüldü (tepsiden tek yüzlü ürün, 2. raf 3. göz, merkezden dikey kaydırma):
−17…+20 dp aynı göze, −20 dp üst rafa düşüyor — raf aralığı 41,7 dp, yani bant
aralığın tamamı ve kaçan dokunuş en yakın göze gidiyor. Hızlı dokunuş uzun basış
sayılmıyor: yerleşmiş ürüne 10 × dokunuş ürünü yerinde bıraktı, 700 ms basış
tepsiye geri gönderdi.

**D — denge** (`./gradlew :engine:probe`). Bütün ölçüm sayıları 2026-09-09
kaydıyla birebir aynı (brif, teknik payları, denetim tür karışımı, satış
hedef/taban, sipariş uzman/kâhin %99 · %98 · %96). Değişen yalnız süreler (yalnız
rapor): Satış iyileştiricisi 27 / 40 / 84 ms (önce 18 / 36 / 70), üretim en kötü
36 / 57 / 85 ms.

**E — erişilebilirlik ⚠️ yarım.** TalkBack kapalıyken `erisim` dört modda da
"etiketsiz 0". Sınırı sıfır düğüm Diziliş 3, Denetim 1, Satış 3, Sipariş 2 —
hepsi alt eylem satırı; çubuklar gizliyken beklenen (2026-09-10 Bulgu 3).
**TalkBack açık ölçüm yapılamadı:** Samsung TalkBack (13.5) bu cihazda kullanıcı 0
için kaldırılmış (`installed=false`); servis ayarlarda etkinleştirilse de
bağlanmıyor. v0.28.2'den beri bekleyen "TalkBack açıkken sınırı sıfır … 0"
doğrulaması hâlâ açık.

Yeni bulgu: **raf gözleri ekran okuyucuya tek tek açılmıyor.** Dört modun tuvali
de tek bir düğüm ("Reyon 3 raf × 4 göz, 5 ürün yerleşmedi"); gözlerin konumu
okunmuyor ve bir göze ürün koymanın erişilebilir bir yolu yok
(`ReyonScreen.kt` tuvalde yalnız `contentDescription`). Ekran okuyucuyla
Diziliş, Satış ve Denetim oynanamaz; Sipariş'in listesi okunuyor.

**F — diller** (14 dil, 360×640 dp = `wm size 1080x1920` + `wm density 480`,
dil `cmd locale set-app-locales` ile; kurulum kartı ve dört mod). Arapça'da
yerleşim sağdan sola, rakamlar Latin (81%, 1/5); raf tuvali aynalanmıyor ve brif
de "soldan" diyor, tutarlı. Satış'ta kısa ekranda okunan kural tr, de, fi'de
**3/5** — 2026-09-21 Bulgu 1'in düzeltmesi (tek satır gövde) cihazda doğrulandı
(de ve fi önce 2/5).

Bulgular, hepsi 360 dp genişlikte:

| # | Ne | Diller |
| --- | --- | --- |
| F1 | Diziliş ve Satış'ın üç düğmeli alt satırında etiket **kelime ortasından** bölünüyor: "Tamaml·a", "Rückgä·ngig", "Alustall·e", "Deshac·er", "Termine·r", "Suggeri·mento", "Desfaze·r", "Отмени·ть", "Подска·зка" | tr, de, fi, es, fr, it, pt, ru |
| F2 | Aynı satırda etiket üç noktayla kırpılıyor: "Ongedaan ma…", "Naar het ba…" | nl |
| F3 | Stat kartı başlığı kelime ortasından bölünüyor: "ENCONTRAD·AS/OS" (Denetim), "FORTJENEST·E" (Sipariş) | es, pt, da, nb |
| F4 | Üst çubukta uygulama adı "REYO·N" diye bölünüyor ("Terug naar het begin" yer yiyor), dört modda da | nl |
| F5 | Kurulum kartında zorluk çipi kırpılıyor: "Keskit…", "Слож…", "Makk…" / "Gemid…", "Vansk…" | fi, ru, nl, nb |
| F6 | "Tepsiye al" ve karşılıkları iki satıra sarıyor (kelime sınırında), "koli 6 · Salı gelir" iki-üç satır | çoğu dil |

F1–F4 kelimeyi bölüyor ya da gizliyor; F5 yalnız zorluk adını kırpıyor (seçili
olan üst satırda tam yazıyor); F6 kabul edilebilir sarma. İngilizce, Danca (F3
hariç), Norveççe (F3, F5 hariç) ve İsveççe temiz. Rusça brifte ürün adı
çekimsiz kalıyor ("над Сыр"): şablonun sınırı, yerleşim sorunu değil.

**Play ekran görüntüleri** `store/screenshots/{tr,en}/` altında, 1080×1920
(`wm size 1080x1920`, yoğunluk cihazınki, 411 × 731 dp), dört mod: Diziliş
(brif + üç ipucuyla kısmen dolu raf, ✓'li kurallar), Denetim (plan/raf ikilisi),
Satış (dolu raf, puan paneli 49/66), Sipariş (hafta sonucu, grafik; 218/323,
%67). Sipariş karesinde yıldız yok; daha iyi bir hafta ile yeniden alınabilir.

Ekran boyutu, yoğunluk, uygulama dili ve erişilebilirlik ayarları koşumlardan
sonra geri alındı.
