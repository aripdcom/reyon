package com.aripd.reyon.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sitedeki sayfaların adresi uygulamanın diline göre: İngilizce kökte, diğer
 * diller kendi klasöründe (tools/gen_site.py aynı düzeni üretir,
 * tools/check_site.py Links.PRIVACY'nin sitedeki privacy.html olduğunu denetler).
 */
@RunWith(AndroidJUnit4::class)
class LinksTest {

    @Test
    fun englishAndUnknownLanguagesOpenTheRootPages() {
        for (tag in listOf("en", null, "ja")) {
            assertEquals("https://reyon.aripd.com/privacy.html", Links.privacyFor(tag))
            assertEquals("https://reyon.aripd.com/", Links.siteFor(tag))
        }
        assertEquals(Links.PRIVACY, Links.privacyFor("en"))
    }

    @Test
    fun everyOtherLanguageOpensItsOwnFolder() {
        assertEquals("https://reyon.aripd.com/tr/privacy.html", Links.privacyFor("tr"))
        assertEquals("https://reyon.aripd.com/ar/", Links.siteFor("ar"))
        for (tag in AppLocale.TAGS - "en") {
            assertEquals("${Links.SITE}/$tag/privacy.html", Links.privacyFor(tag))
            assertEquals("${Links.SITE}/$tag/", Links.siteFor(tag))
        }
    }
}
