package com.aripd.reyon.platform

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Derlenmiş metinlerin değişmezleri.
 *
 * aapt2 iki şeyi sessizce yapar ve ikisi de derlemeyi kırmaz, yalnızca
 * kullanıcı görür: kaçışsız çift tırnağı tırnak aç/kapa sayıp atar, %% ikilisini
 * olduğu gibi bırakır (yüzdeyi tekleştiren şey String.format'tır, argümansız
 * getString onu çalıştırmaz). Kaynak tarafını tools/check_strings.py denetler;
 * burada okunan, aapt2'nin gerçekten ne ürettiğidir.
 */
@RunWith(AndroidJUnit4::class)
class StringResourceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Verilen dile çözülmüş kaynaklar.
     *
     * Robolectric'in varsayılan dili Türkçe (`robolectric.properties`), oysa
     * `res/values` İngilizce. Belli bir dilin metnine bakan test dili kendisi
     * seçmeli, yoksa varsayılanın hangi dil olduğuna sessizce bağlanır.
     */
    private fun resourcesIn(tag: String): Resources {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }
        return context.createConfigurationContext(config).resources
    }

    @Test
    fun theLicenseNoteKeepsItsQuotes() {
        assertTrue(
            "marka adı tırnak içinde kalmalı",
            context.getString(R.string.about_app_license).contains("\"Reyon\""),
        )
    }

    /**
     * Arapça'da sayı taşıyan metinler Latin rakamla mı yazılıyor?
     *
     * `getString(id, sayı)` cihazın yerel ayarıyla biçimler ve Arapça'da
     * `%d`'yi Hint-Arap rakamına çevirir; stok sayaçları cihazda `٩/١٢`
     * görünüyordu. [appText] Locale.ROOT ile biçimlediği için rakamlar Latin
     * kalır. Test ham okumanın hâlâ Hint-Arap bastığını da doğrular: kural
     * gerçekten gerekli.
     */
    @Test
    fun numbersStayLatinInArabic() {
        val arabic = resourcesIn("ar")
        val id = R.string.reyon_order_item_desc_fmt

        val safe = appText(arabic, id, "Kola", 9, 12, 14)
        assertTrue("metin Arapça kaynaktan gelmeli", safe.contains("المخزون"))
        assertTrue("rakamlar Latin olmalı: $safe", safe.contains("9") && safe.contains("12"))
        assertFalse("Hint-Arap rakamı kalmamalı: $safe", safe.any { it in '٠'..'٩' })

        val raw = arabic.getString(id, "Kola", 9, 12, 14)
        assertTrue(
            "ham okuma Hint-Arap basıyor, kural bu yüzden var: $raw",
            raw.any { it in '٠'..'٩' },
        )
    }

    /**
     * Yüzde taşıyan metinler biçimlenince tek yüzde işareti bırakır.
     *
     * Kaynakta `%%` yazmak zorunlu (aapt2 tek `%`'yi biçim belirteci sanır);
     * ekrana `%` olarak inmesini String.format sağlıyor. Metin argümansız
     * okunursa kullanıcı `%%` görür — testin koruduğu şey bu.
     *
     * İşaretin kendisi yalnız onu yazan dilde aranır: yüzde taşıyan beş
     * anahtarın hepsinde Türkçe cümleyi "hedefin yüzde 80 kadarı" diye kurar ve
     * `%` hiç yazmaz. Bu bilinçli bir üslup farkı — `tools/check_strings.py` de
     * `%%`'yi biçim belirteci paritesinden ayrı tutar — ama testin dili açıkça
     * seçmesini gerektirir, çünkü Robolectric'in varsayılan dili Türkçe.
     * Çift yüzdenin ekrana inmemesi ise dilden bağımsız: on dört dil de bakılır.
     */
    @Test
    fun percentSignsCollapseWhenFormatted() {
        val id = R.string.reyon_sales_best_fmt
        val english = resourcesIn("en")

        val raw = english.getString(id)
        assertTrue("kaynakta %% durmalı, tekleştiren String.format: $raw", raw.contains("%%"))

        val formatted = appText(english, id, 80)
        assertTrue("yüzde ekrana inmeli: $formatted", formatted.contains("80%"))

        for (tag in AppLocale.TAGS) {
            val text = appText(resourcesIn(tag), id, 80)
            assertFalse("çift yüzde kalmamalı ($tag): $text", text.contains("%%"))
        }
    }
}
