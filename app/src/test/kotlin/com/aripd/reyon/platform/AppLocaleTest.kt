package com.aripd.reyon.platform

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * Dil altyapısının değişmezleri: desteklenen dil listesi tek kaynaktan gelir,
 * eşleşmeyen dil İngilizce'ye düşer, her dilin kendi çevirisi vardır, sayılar
 * her dilde Latin rakamla yazılır.
 */
@RunWith(AndroidJUnit4::class)
class AppLocaleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Verilen dile çözülmüş kaynak dizesi. */
    private fun stringIn(tag: String, id: Int): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return context.createConfigurationContext(config).getString(id)
    }

    /** locales_config.xml içindeki dil etiketleri, dosyadaki sırayla. */
    private fun configuredTags(): List<String> {
        val parser = context.resources.getXml(R.xml.locales_config)
        val tags = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                tags += parser.getAttributeValue(ANDROID_NS, "name")
            }
        }
        return tags
    }

    @Test
    fun supportedTagsMatchTheLocaleConfig() {
        // Sistem dil seçicisi locales_config.xml'i, uygulama içi seçici
        // AppLocale.TAGS'i okur; ikisi ayrışırsa bir dil yalnız birinde görünür.
        assertEquals(configuredTags(), AppLocale.TAGS)
    }

    @Test
    fun everySupportedLanguageHasAnEndonym() {
        for (tag in AppLocale.TAGS) {
            val name = AppLocale.endonym(tag)
            assertTrue("$tag için dil adı yok", name.isNotBlank() && name != tag)
        }
    }

    @Test
    fun unsupportedLanguagesFallBackToEnglishNotTurkish() {
        // Düzeltilen hata: Türkçe res/values altındaydı, desteklenmeyen her dil
        // uygulamayı Türkçe görüyordu. Japonca hiçbir zaman listeye girmeyeceği
        // için yedeğin İngilizce olduğunu güvenle gösterir.
        val english = stringIn("en", R.string.app_tagline)
        assertEquals("varsayılan res/values İngilizce olmalı", "FMCG shelf management simulator", english)
        assertEquals("desteklenmeyen dil İngilizce\'ye düşmeli", english, stringIn("ja", R.string.app_tagline))
        assertEquals("FMCG raf yönetimi simülatörü", stringIn("tr", R.string.app_tagline))
    }

    @Test
    fun everySupportedLanguageHasItsOwnTagline() {
        // Her dil kendi metnini almalı; biri varsayılana düşüyorsa o dilin
        // klasörü ya eksik ya yanlış adlandırılmış.
        val english = stringIn("en", R.string.app_tagline)
        for (tag in AppLocale.TAGS - setOf("en")) {
            assertNotEquals("$tag kendi metnini almalı", english, stringIn(tag, R.string.app_tagline))
        }
    }

    @Test
    fun sharedDailyModeStringsStayTranslatable() {
        // Dört modun hepsinde günün vakası var; metin ortak bölümde, her dilde.
        assertEquals("Case of the day", stringIn("en", R.string.mode_daily))
        assertEquals("Günün vakası", stringIn("tr", R.string.mode_daily))
    }

    @Test
    fun normalizeStripsRegionsForEverySupportedLanguage() {
        // Desteklenen her dil için bölge eki düşer: de-DE → de, pt-BR → pt.
        // Liste büyüdükçe bu test kendiliğinden yeni dilleri de kapsar.
        for (tag in AppLocale.TAGS) {
            assertEquals("$tag-XX etiketi $tag\'e inmeli", tag, AppLocale.normalize(Locale.forLanguageTag("$tag-XX")))
        }
        assertEquals("tr", AppLocale.normalize(Locale.forLanguageTag("tr-CY")))
    }

    @Test
    fun normalizeMapsLegacyNorwegianAndRejectsUnsupported() {
        // Locale("no") tarihsel olarak Norveççe'yi gösterir; kaynaklarımız nb altında.
        val legacyNorwegian = Locale.Builder().setLanguage("no").setRegion("NO").build()
        val expected = if ("nb" in AppLocale.TAGS) "nb" else null
        assertEquals(expected, AppLocale.normalize(legacyNorwegian))
        // Japonca desteklenmiyor ve desteklenmeyecek: null dönmeli.
        assertNull("desteklenmeyen dil null dönmeli", AppLocale.normalize(Locale.forLanguageTag("ja")))
    }

    @Test
    fun numbersKeepLatinDigitsWithLocalSeparators() {
        assertEquals("1,234,567", AppLocale.number(1_234_567L, Locale.forLanguageTag("en")))
        assertEquals("1.234.567", AppLocale.number(1_234_567L, Locale.forLanguageTag("tr")))
        // Arapça varsayılanı ١٢٣٤٥٦٧ olurdu; skorlar tuvale sabit ölçüyle çizilir.
        val arabic = AppLocale.number(1_234_567L, Locale.forLanguageTag("ar"))
        assertEquals("Arapça'da yedi Latin rakam olmalı: $arabic", 7, arabic.count { it in '0'..'9' })
        assertTrue("Arap-Hint rakamı kalmamalı: $arabic", arabic.none { it in '٠'..'٩' })
    }

    @Test
    fun decimalsKeepLatinDigitsWithLocalSeparators() {
        assertEquals("1.5", AppLocale.decimal(1.5f, locale = Locale.forLanguageTag("en")))
        assertEquals("1,5", AppLocale.decimal(1.5f, locale = Locale.forLanguageTag("tr")))
        assertEquals("1.50", AppLocale.decimal(1.5f, digits = 2, locale = Locale.forLanguageTag("en")))
        val arabic = AppLocale.decimal(1.5f, locale = Locale.forLanguageTag("ar"))
        assertEquals("Arapça'da iki Latin rakam olmalı: $arabic", 2, arabic.count { it in '0'..'9' })
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
