package com.aripd.reyon.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Reyon ekranlarının kısa telefon yerleşimi.
 *
 * Üç tür de aynı iskelete oturuyor: üstte raf tuvali, altında kaydırılabilir
 * bir panel (Diziliş'te planogram brifi, Satış'ta puan kuralları, Sipariş'te
 * sipariş listesi), en altta tepsi. Tuval yalnız genişlikten ölçülüyordu — en
 * boy oranı yüksekliği belirliyordu — ve sütunda ağırlıksız olduğu için
 * yüksekliği önce o alıyordu. 360×640 dp bir telefonda tuval ile tepsi
 * yüksekliğin tamamını yiyor, panele ~0 kalıyordu: v0.43.1 cihaz koşumunda
 * Diziliş'in brifi hiç çizilmedi (kural satırı ≈13 dp, ikinci kuralın
 * erişilebilirlik kutusu sıfır) ve brif olmadan bulmaca çözülemediği için mod
 * o ekranda oynanamaz durumdaydı. Aynı açlık Satış'ta da vardı: beş puan
 * kuralından ikisi görünüyordu (Robolectric ölçümü).
 *
 * Kural iki adımda:
 *
 *  1. Tuval oyun alanının en çok [SHELF_SHARE] payını alır ([shelfHeight]).
 *  2. Kalan yükseklik panel ile tepsi arasında bölünür, taşan kısım kendi
 *     içinde kayar. "Kalan" tahmin edilmiyor, ölçülüyor: panel ile tepsi kendi
 *     `BoxWithConstraints`'inin içinde duruyor, böylece aradaki ipucu/döküm
 *     satırı gibi değişken yükseklikler hesaba kendiliğinden giriyor.
 *
 * Bölüşmenin iki kolu var ve hangisinin "önce ölçüldüğü" ekrana göre seçiliyor.
 * Diziliş'te tepsi önce ölçülüp panele [PANEL_MIN] bırakıyor ([trayHeight]).
 * Satış'ta panel önce ölçülüp tepsiye [TRAY_KEEP] bırakıyor ([panelCap]), çünkü
 * orada sığması gereken şey panelin içeriği: beş puan kuralı. Compose sütunda
 * ağırlıksız çocukları önce ölçtüğü için kolu seçmek, tavanı hangi çocuğa
 * koyduğumuzla oluyor.
 *
 * Böylece panel en az [PANEL_MIN] yüksekliğinde oluyor — başlık + üç satır.
 * Uzun telefonda iki tavan da doğal yüksekliklerin üstünde kaldığı için orada
 * yerleşim aynen sürüyor.
 *
 * Tek istisna, kalanın iki tabana birden yetmediği çok kısa ekran: orada tepsi
 * kendi tabanını ([TRAY_MIN]) korur ve panele kalan ne varsa o düşer. Ölçülen
 * örnek: 480 dp'lik uygulama alanında kalan 177,5 dp, tepsi 72 dp, panel 105,5
 * dp — dört kuraldan üçü görünüyor, dördüncüsü kaydırmayla geliyor. İki taban
 * aynı yükseklikten beslendiği için biri artınca öbürü azalıyor; ürün
 * seçilemeyen bir tepsi de bulmacayı çözülemez yapardı.
 *
 * Tavanlar `BoxWithConstraints`'in içinde, ama `Column`'un dışında hesaplanmalı:
 * `ColumnScope` da `@LayoutScopeMarker` taşıdığı için sütunun içinde
 * `BoxWithConstraintsScope` örtülüyor ve maxWidth/maxHeight örtük alıcıyla
 * okunamıyor (derleme hatası).
 */
internal const val SHELF_SHARE = 0.40f

/** Panele bırakılan taban: başlık + üç satır. */
internal val PANEL_MIN = 140.dp

/** Tepsinin tabanı: başlık + bir sıra ürün. Ürün seçilemezse bulmaca çözülemez. */
internal val TRAY_MIN = 72.dp

/** Panelin ölçüm etiketi; yerleşim garantisi testte bundan okunuyor. */
const val REYON_PANEL_TAG = "reyon_panel"

/** Tepsinin ölçüm etiketi; panelin payı tepsinin tabanına bağlı olduğu için gerekli. */
const val REYON_TRAY_TAG = "reyon_tray"

/**
 * Raf tuvalinin yüksekliği: en boy oranının istediği kadar, ama oyun alanının
 * [SHELF_SHARE] payını geçmeden.
 *
 * Tavan neden en boy oranı değiştirilerek uygulanıyor: `fillMaxWidth()`
 * genişliği sabitliyor, bu yüzden `heightIn(max = ...)` ile `aspectRatio(...)`
 * birlikte çalışmıyor — oran hiçbir boyutu kısıtı sağlayacak şekilde
 * bulamayınca kısıtı yok sayıp yine genişlikten hesaplıyor. Tuval tam
 * genişlikte kalsın istediğimiz için tavan yüksekliğe uygulanıyor: kısa ekranda
 * gözler basıklaşıyor, ama çizim de dokunma da tuvalin ölçülen boyutundan
 * türediği (`ShelfGeom(size.width, size.height, …)`) için eşleme bozulmuyor.
 */
internal fun shelfHeight(width: Dp, available: Dp, ratio: Float): Dp =
    minOf(width / ratio, available * SHELF_SHARE)

/**
 * Tepsiye her hâlükârda bırakılan pay: başlık + iki sıra ürün (cihazda 167 dp).
 *
 * [panelCap] bunu koruyor: panel ne kadar uzun olursa olsun tepsinin iki sırası
 * duruyor, üçüncü sırası bir sürüklemeye kalıyor (411 dp'de ölçüldü: 3,0 dp'lik
 * şerit tek sürüklemeyle 30,1 dp'ye geliyor ve dokunuş seçimi alıyor).
 */
internal val TRAY_KEEP = 168.dp

/**
 * Tepsinin tavanı: panel ile tepsiye kalan [rest] yükseklikten panele
 * [PANEL_MIN] bırakacak kadar. Doğal yükseklik bunun altında kalırsa
 * bağlamıyor, yani uzun telefonda tepsi eskisi gibi tam görünüyor.
 *
 * Bu kolu Diziliş kullanıyor: brifin içeriği tura göre uzayıp kısalıyor ve
 * panelin tepsiden artanı alması orada sorun olmadı (kısa ekranda taban zaten
 * bağlıyor, uzun ekranda brif sığıyor).
 */
internal fun trayHeight(rest: Dp): Dp = (rest - PANEL_MIN).coerceAtLeast(TRAY_MIN)

/**
 * Panelin tavanı: kalan [rest] yükseklikten tepsiye [TRAY_KEEP] bırakacak
 * kadar, ama [PANEL_MIN]'in altına inmeden. Panel ağırlıksız olduğu için
 * **önce ölçülüyor** ve bu tavana kadar kendi içeriği kadar yer alıyor; tepsi
 * kalanı alıyor, taşarsa kendi içinde kayıyor.
 *
 * Satış bunu kullanıyor, çünkü orada panelin içeriği "sığmalı" olan şey: beş
 * puan kuralı. Kolun tersi (tepsi önce, panel artan) 411 dp'de beşinci kuralın
 * adını kırpıyordu — tepsi ürün adları sarınca iki sıra yerine üç sıra oluyor
 * (167 ↔ 219 dp) ve panel onunla 240 ↔ 208 dp arasında gidiyordu; 208 dp'de
 * `Marka bloğu` 12,6 dp'ye iniyordu (cihazda ölçüldü).
 *
 * Panelin ihtiyacı sabit bir sayı değil, ölçülen içerik: cihazda beş kural
 * 232,4 dp tuttu, ama her kural satırı ad (17,7 dp) + gövde (1–2 satır × 17,7
 * dp) olduğu için bir gövdenin daha sarması 17,7 dp ekliyor. Dil ve yazı
 * ölçeği bunu değiştirdiği için tavan "panelin istediği kadar" diye
 * yazılamazdı; onun yerine panel kendi boyunu alıyor, tavan yalnız tepsiyi
 * koruyor. Böylece uzun bir dilde panel tavana dayanıyor, kısa bir içerikte
 * tepsiye daha çok yer kalıyor.
 *
 * Tepsinin tabanı ([TRAY_MIN]) tavanın üstünde: panel önce ölçüldüğü için tavan
 * onu sınırlamazsa çok kısa ekranda tepsiye hiç yer kalmazdı — 480 dp'lik
 * uygulama alanında kalan 177,5 dp ve `maxOf` tek başına 140 dp derdi, tepsiye
 * 37,5 dp bırakırdı. Ürün seçilemeyen bir tepsi bulmacayı çözülemez yapar.
 *
 * İki sınırla birlikte kural, `rest` 308 dp'nin altında **eski kolun birebir
 * aynısı**: orada `maxOf` 140 veriyor ve tavan `min(140, rest - TRAY_MIN)`
 * oluyor, ki bu da tepsi önce ölçüldüğünde panele düşen paydı
 * (`rest - trayHeight(rest)`). Test bunu bir aralık üzerinde doğruluyor.
 */
internal fun panelCap(rest: Dp): Dp =
    minOf(maxOf(PANEL_MIN, rest - TRAY_KEEP), rest - TRAY_MIN).coerceAtLeast(0.dp)

/**
 * Satış'ın kural gövdeleri tek satıra inmeli mi?
 *
 * Tavan tabanda ([PANEL_MIN]) sıkışmışsa evet. Orada iki satırlık gövdeler
 * panele ancak iki buçuk kural sığdırıyor ve kaç kuralın **adının** okunduğu
 * dile bağlı hâle geliyor: cihazda 360×640 dp'de Türkçe üç, Almanca ve Fince
 * iki kural veriyordu, çünkü o dillerde ikinci kuralın gövdesi de iki satıra
 * sarıyor (`Paare wie Chips und Dip nebeneinander +6, / übereinander +3`).
 * Ölçülen satır boyları: gövdesi iki satır olan kural 50,7 dp, tek satır olan
 * 35,2 dp, panel başlığı ~36 dp.
 *
 * Tek satıra inince satır 35,2 dp oluyor ve üçüncü kuralın adı 106–124 dp'ye
 * düşüyor — 140 dp'nin içinde, **dile bakmadan**. Bedeli, kısa ekranda her
 * açıklamanın tek satıra inmesi; satıra dokunmak gövdeyi tam açtığı için metin
 * kaybolmuyor, sıkışan yalnız kaydırmasız görünüm.
 *
 * Eşik ayrı bir sayı değil, [panelCap]'in kendisi: tavan tabana çakılıysa ekran
 * kısadır (`rest` ≤ 308 dp). Uzun ekranda tavan yükseldiği için gövdeler iki
 * satır kalıyor.
 */
internal fun rulesAreCompact(rest: Dp): Boolean = panelCap(rest) <= PANEL_MIN
