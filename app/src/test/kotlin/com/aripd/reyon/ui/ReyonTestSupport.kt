package com.aripd.reyon.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.aripd.reyon.R
import com.aripd.reyon.engine.ReyonLevel
import com.aripd.reyon.str

/**
 * Reyon ekran testlerinin ortak hijyeni. Görünüm modeli etkinlik yok edilirken
 * (test bittikten, @After çalıştıktan sonra) açık turu yeniden kaydediyor; o
 * yüzden her test önce turu bırakır (geri oku Görevler'e döner, tur saklanır ama
 * ekran boşalır), sonra tercihleri temizler.
 *
 * Testler arası sızıntının asıl nedeni varsayılan görünüm modeli fabrikasıydı:
 * ilk testin Application'ını statik tutuyor, modeller sonraki testlerde de onun
 * kayıtlarını kullanıyordu. Modeller artık [ReyonViewModels] ile o anki
 * Application'dan kuruluyor; temizlik bu testin kaydını gerçekten temizliyor.
 */
object ReyonTestSupport {
    /**
     * Tercihleri temizler. "Nasıl oynanır" kartı ilk girişte kendiliğinden açılıp
     * oyun alanının ortasını kapatıyor; oyun akışını sınayan testler için dört
     * modun kartı görülmüş sayılır. Kartın kendisini [ReyonHomeTest] sınıyor.
     */
    fun clearPrefs(introsSeen: Boolean = true) {
        val editor = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("reyon_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
        if (introsSeen) {
            for (kind in ReyonKind.entries) editor.putBoolean("intro_seen_" + kind.name, true)
        }
        editor.commit()
    }

    /**
     * Kayıtların kısa dökümü ("nasıl oynanır" bayrakları hariç). Başarısız testin
     * mesajına eklenir: format, mod ve son sonuç kaydının o anki hâli görünsün.
     */
    fun prefsDump(): String =
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("reyon_state", Context.MODE_PRIVATE)
            .all.toSortedMap()
            .filterKeys { !it.startsWith("intro_seen_") && !it.endsWith("_snapshot") }
            .entries.joinToString(" ") { "${it.key}=${it.value}" }
}

/**
 * Ekrandaki metinlerin ve açıklamaların kısa dökümü (birleşik ağaç, seçili olanlar
 * işaretli). Beklenen düğüm gelmeyince mesajda ekranda ne olduğu görünsün.
 */
fun ComposeContentTestRule.screenDump(limit: Int = 1500): String {
    val out = ArrayList<String>()
    fun walk(node: SemanticsNode) {
        val texts = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val desc = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val selected = node.config.getOrNull(SemanticsProperties.Selected) == true
        val line = listOfNotNull(texts, desc?.let { "[$it]" }).joinToString(" ")
        if (line.isNotEmpty()) out += if (selected) "($line)" else line
        node.children.forEach { walk(it) }
    }
    onAllNodes(isRoot()).fetchSemanticsNodes().forEach { walk(it) }
    return out.joinToString(" | ").take(limit)
}

private fun ComposeContentTestRule.homeShown(): Boolean =
    onAllNodesWithTag(HOME_TAG).fetchSemanticsNodes().isNotEmpty()

private fun ComposeContentTestRule.backShown(): Boolean =
    onAllNodesWithContentDescription(str(R.string.back)).fetchSemanticsNodes().isNotEmpty()

/** Ekran hangi durumda açılırsa açılsın Görevler'e getirir. */
fun ComposeContentTestRule.reyonOpenHome() {
    waitUntil(timeoutMillis = 30_000) { homeShown() || backShown() }
    if (!homeShown()) {
        onAllNodesWithContentDescription(str(R.string.back)).onFirst().performClick()
        waitUntil(timeoutMillis = 10_000) { homeShown() }
    }
}

/**
 * Görevler'den alıştırma: formatı seçer, modun satırına dokunur, modun ekranı
 * açılana dek bekler. Format seçilmediyse ya da ekran açılmadıysa hata burada,
 * ekranın ve kayıtların dökümüyle çıkar; yanlış formatta açılan tur sonraki
 * adımlarda anlamsız bir hataya dönüşmesin.
 */
fun ComposeContentTestRule.reyonStartPractice(kind: ReyonKind, level: ReyonLevel = ReyonLevel.KOLAY) {
    reyonOpenHome()
    val format = str(formatName(level))
    // Kısa ekranda Görevler kayar: satırlar görünür alana getirilerek basılır.
    onNodeWithText(format).performScrollTo().performClick()
    val selected = onNodeWithText(format).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
    if (selected != true) {
        throw AssertionError("$format seçilmedi (selected=$selected) · ekran: ${screenDump()} · kayıt: ${ReyonTestSupport.prefsDump()}")
    }
    onNodeWithTag(homePracticeTag(kind)).performScrollTo().performClick()
    try {
        waitUntil(timeoutMillis = 10_000) { backShown() }
    } catch (e: ComposeTimeoutException) {
        throw AssertionError("${kind.name} ekranı açılmadı · ekran: ${screenDump()} · kayıt: ${ReyonTestSupport.prefsDump()}", e)
    }
}

/** Test sonunda açık turu bırakır; içerik hiç kurulmadıysa sessizce geçer. */
fun ComposeContentTestRule.reyonLeaveRound() {
    try {
        if (!homeShown() && backShown()) {
            onAllNodesWithContentDescription(str(R.string.back)).onFirst().performClick()
            waitForIdle()
        }
    } catch (e: IllegalStateException) {
        // Compose hiyerarşisi yok: test içerik kurmadan bitti.
    }
}
