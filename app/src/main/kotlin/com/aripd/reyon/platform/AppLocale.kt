package com.aripd.reyon.platform

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.Normalizer
import java.util.Locale

/**
 * Uygulamanın dili.
 *
 * Varsayılan dil İngilizce'dir (`res/values`); her çeviri kendi
 * `res/values-<dil>` klasöründe durur. Listede olmayan bir dile ayarlı telefon
 * İngilizce görür.
 *
 * Dili telefondan bağımsız seçmek iki yoldan yürür:
 * - Android 13+ (API 33): sistemin kendi uygulama-dili altyapısı
 *   ([LocaleManager]). Kullanıcı dili Ayarlar'dan da değiştirebilir, bu yüzden
 *   doğru kaynak her zaman sistemdir — kendi kaydımıza bakmayız.
 * - Android 8-12: seçim [SettingsStore]'da saklanır, [wrap] her etkinliğin
 *   taban bağlamını o dile sarar. `androidx.appcompat` eklemeye gerek yok;
 *   "sıfır bağımlılık" sözü korunur.
 */
object AppLocale {

    /** "Telefonun dili" — kullanıcı özel bir dil seçmemiş. */
    const val SYSTEM = ""

    /**
     * Desteklenen diller: yalnız gerçekten çevirisi olanlar. Listede olup
     * `values-<dil>` karşılığı olmayan bir dil sistem seçicisinde çıkar ama
     * kullanıcıya İngilizce verir — `tools/check_strings.py` bunu hata sayar.
     *
     * `res/xml/locales_config.xml` ile birebir aynı olmalı; [ZaLocaleTest] ve
     * denetim betiği ikisini karşılaştırır.
     */
    val TAGS: List<String> = listOf(
        "en", "tr", "de", "fr", "nl", "es", "pt", "it", "da", "sv", "nb", "fi", "ru", "ar",
    )

    /** Dilin kendi dilindeki adı. Seçicide kullanıcı kendi dilini tanısın diye elle yazılı. */
    private val ENDONYMS: Map<String, String> = mapOf(
        "en" to "English",
        "tr" to "Türkçe",
        "de" to "Deutsch",
        "fr" to "Français",
        "nl" to "Nederlands",
        "es" to "Español",
        "pt" to "Português",
        "it" to "Italiano",
        "da" to "Dansk",
        "sv" to "Svenska",
        "nb" to "Norsk bokmål",
        "fi" to "Suomi",
        "ru" to "Русский",
        "ar" to "العربية",
    )

    /** Seçicide görünen ad; bilinmeyen etiket için etiketin kendisi. */
    fun endonym(tag: String): String = ENDONYMS[tag] ?: tag

    /**
     * Seçicideki sıra: dilin kendi adına göre alfabetik (Dansk, Deutsch,
     * English … Svenska, Türkçe, Русский, العربية). Aksan sırayı bozmaz; Latin
     * dışı yazılar Unicode sırasıyla sona düşer. Sitedeki dil listeleri de aynı
     * sırada (`tools/gen_site.py`, `by_name`). [TAGS]'in kendi sırası değişmez:
     * o sıra `locales_config.xml` ile birebir tutulur.
     */
    val PICKER_ORDER: List<String> by lazy { TAGS.sortedBy { sortKey(endonym(it)) } }

    private fun sortKey(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)

    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /**
     * Kullanıcının seçtiği dil, ya da [SYSTEM].
     *
     * Android 13+'ta sistemden okunur: kullanıcı dili Ayarlar'dan değiştirmiş
     * olabilir, o zaman bizim kaydımız bayat kalır.
     */
    fun selected(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
            if (locales == null || locales.isEmpty) SYSTEM else normalize(locales[0]) ?: SYSTEM
        } else {
            SettingsStore(context).language
        }

    /**
     * Dili uygular ve saklar. Android 13+'ta sistem etkinliği kendisi yeniden
     * oluşturur; altında [Activity.recreate] çağırırız.
     */
    fun choose(context: Context, tag: String) {
        val clean = if (tag in TAGS) tag else SYSTEM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            manager.applicationLocales =
                if (clean == SYSTEM) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(clean)
        } else {
            val store = SettingsStore(context)
            if (store.language == clean) return
            store.language = clean
            context.findActivity()?.recreate()
        }
    }

    /**
     * Bağlamın ardındaki etkinlik. Dili yeniden oluşturarak uygulamak için
     * gerekli; `LocalActivity` activity-compose 1.10'da geldiği ve burada 1.9.3
     * kullanıldığı için bağlamı elle çözüyoruz.
     */
    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * Taban bağlamı seçili dile sarar; `MainActivity.attachBaseContext` çağırır.
     * Android 13+'ta sistem zaten uygulamıştır, bağlam olduğu gibi döner.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = SettingsStore(base).language
        if (tag == SYSTEM || tag !in TAGS) return base
        val locale = Locale.forLanguageTag(tag)
        // Sayı biçimleme ve kaynak dışı metin Locale.getDefault()'a bakıyor;
        // uygulama dili telefondan ayrıldığında ikisi de yeni dile dönmeli.
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return ContextWrapper(base.createConfigurationContext(config))
    }

    /**
     * Bir BCP-47 etiketini desteklenen listeye indirger: `de-DE` → `de`,
     * `pt-BR` → `pt`, `no` → `nb`. Eşleşme yoksa null.
     */
    fun normalize(locale: Locale): String? {
        val language = locale.language.lowercase(Locale.ROOT)
        val canonical = when (language) {
            // Locale("no") tarihsel olarak Norveççe'yi gösterir; kaynaklarımız nb altında.
            "no" -> "nb"
            // Locale("iw"/"in"/"ji") gibi eski kodlar Java tarafında dönüştürülür.
            else -> language
        }
        return canonical.takeIf { it in TAGS }
    }

    /**
     * Binlik ayırıcılı tam sayı: tr "1.234", en "1,234", fr "1 234".
     *
     * Ayırıcı dile uyar ama **rakamlar her zaman Latin** kalır. Arapça'da
     * `%,d` varsayılan olarak Arap-Hint rakamları (٣٤٥) üretir; skorlar
     * tuvale ve 1080 px paylaşım kartına sabit ölçülerle çizildiği için bu
     * taşma yapar, üstelik bir kısmı Latin bir kısmı Arap-Hint olurdu.
     * Tutarlılık okunurluktan önce geliyor.
     */
    fun number(value: Long, locale: Locale = Locale.getDefault()): String =
        format("#,##0", locale).format(value)

    /** Ondalıklı sayı: ayırıcı dile uyar (tr "1,5"), rakamlar Latin kalır. */
    fun decimal(value: Float, digits: Int = 1, locale: Locale = Locale.getDefault()): String =
        format("0." + "0".repeat(digits), locale).format(value)

    private fun format(pattern: String, locale: Locale): DecimalFormat {
        val symbols = DecimalFormatSymbols.getInstance(locale).apply { zeroDigit = '0' }
        return DecimalFormat(pattern, symbols)
    }
}

/**
 * Uygulamanın o an çizdiği dilin yereli.
 *
 * [Locale.getDefault] telefonun dilini verir; uygulama dili ondan ayrıldığında
 * İngilizce metin Türkçe kuralıyla büyütülür ve "CONTINUE" → "CONTİNUE" olur.
 * Büyütme/küçültme yapan her yer bunu kullanmalı.
 */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]
