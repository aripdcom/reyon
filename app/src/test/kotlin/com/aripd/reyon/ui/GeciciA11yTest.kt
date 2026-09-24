package com.aripd.reyon.ui

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aripd.reyon.R
import com.aripd.reyon.setAppContent
import com.aripd.reyon.str
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeciciA11yTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun compose(v: View): View? {
        if (v.javaClass.name.contains("AndroidComposeView")) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) compose(v.getChildAt(i))?.let { return it }
        return null
    }

    @Test
    fun cipTiklama() {
        ReyonTestSupport.clearPrefs()
        rule.setAppContent { ReyonScreen(highScore = 0L, onScore = {}, onExit = {}) }
        rule.reyonStartPractice(ReyonKind.PUZZLE)
        val tray = hasContentDescription(str(R.string.reyon_tray_label) + ":", substring = true)
        rule.waitUntil(30_000) { rule.onAllNodes(tray).fetchSemanticsNodes().isNotEmpty() }
        val chip = rule.onAllNodes(tray).fetchSemanticsNodes().first()
        val root = compose(rule.activity.window.decorView)!!
        val provider = root.accessibilityNodeProvider!!
        val info = provider.createAccessibilityNodeInfo(chip.id)!!
        println("A11Y çip id=${chip.id} desc=${info.contentDescription} actions=${info.actionList.map { it.id }}")
        rule.runOnIdle { println("A11Y click sonucu=" + provider.performAction(chip.id, AccessibilityNodeInfo.ACTION_CLICK, null)) }
        rule.waitForIdle()
        val back = rule.onAllNodes(hasContentDescription(str(R.string.back))).fetchSemanticsNodes().isNotEmpty()
        val selected = rule.onAllNodes(hasContentDescription(str(R.string.reyon_slot_selected), substring = true)).fetchSemanticsNodes().size
        val home = rule.onAllNodes(androidx.compose.ui.test.hasText(str(R.string.home_practice_title))).fetchSemanticsNodes().isNotEmpty()
        println("A11Y sonra: oyun ekranı=$back görevler=$home")
    }
}
