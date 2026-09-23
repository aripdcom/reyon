package com.aripd.reyon.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.setAppContent
import com.aripd.reyon.str
import com.aripd.reyon.testLocale
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Günün vakası düğmesi bitince adı sonucun yanında kırpılmamalı. 1.1.0 cihaz koşumunda
 * 360 dp'de neredeyse her dilde kırpılıyordu: "Sipa… 402/558", "Best… 402/558",
 * "Раскла… 0:47" (docs/cihaz-testi.md, 1.1.0, G1). Sonuç sığmazsa ikinci satıra iner.
 *
 * Dört modun sonucu cihazdakiyle aynı uzunlukta kaydedilir; en uzun sonuçlar (402/558)
 * ile en uzun mod adları (Bestücken, Раскладка, Controleren) aynı düğmede buluşur.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "+w360dp-h640dp-xhdpi")
class ReyonDailyButtonTest {

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun clearSavedState() = ReyonTestSupport.clearPrefs()

    @After
    fun clear() = ReyonTestSupport.clearPrefs()

    private fun checkNoDailyNameIsCut() {
        val store = ReyonStore(ApplicationProvider.getApplicationContext<Context>())
        val today = LocalDate.now().toEpochDay()
        val level = dailyLevel(today)
        store.saveDaily(today, level, time = 47, hints = 0)
        store.saveAuditDaily(today, level, time = 33, mistakes = 0, hints = 0)
        store.saveSalesDaily(today, level, score = 90, target = 146)
        store.saveOrderDaily(today, level, score = 402, target = 558)
        rule.setAppContent {
            // Ölçüm cihazının yazı ölçeği (SM-A515F, 1,1); 1,0'da Türkçe "Sipariş" sığıyordu.
            val outer = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(outer.density, 1.1f)) {
                ReyonHome(practiceLevel = ReyonLevel.KOLAY, onPracticeLevel = {}, onDaily = {}, onPractice = {}, onExit = {})
            }
        }
        rule.waitForIdle()
        val locale = testLocale().toLanguageTag()
        for (kind in ReyonKind.entries) {
            val name = str(kindLabel(kind))
            val node = rule.onNode(
                hasText(name) and hasAnyAncestor(hasTestTag(homeDailyTag(kind))),
                useUnmergedTree = true,
            ).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            assertFalse("$locale: günün vakası düğmesinde \"$name\" kırpıldı", layout.isLineEllipsized(0))
            assertTrue("$locale: \"$name\" sığmadı", node.boundsInRoot.width > 0f)
        }
    }

    @Test
    fun turkish() = checkNoDailyNameIsCut()

    @Test
    @Config(qualifiers = "en")
    fun english() = checkNoDailyNameIsCut()

    @Test
    @Config(qualifiers = "de")
    fun german() = checkNoDailyNameIsCut()

    @Test
    @Config(qualifiers = "nl")
    fun dutch() = checkNoDailyNameIsCut()

    @Test
    @Config(qualifiers = "ru")
    fun russian() = checkNoDailyNameIsCut()

    @Test
    @Config(qualifiers = "fr")
    fun french() = checkNoDailyNameIsCut()

    @Test
    @Config(qualifiers = "es")
    fun spanish() = checkNoDailyNameIsCut()

    @Test
    @Config(qualifiers = "fi")
    fun finnish() = checkNoDailyNameIsCut()
}
