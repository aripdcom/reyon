package com.aripd.reyon

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.platform.LocalHaptics
import com.aripd.reyon.platform.LocalSound
import com.aripd.reyon.platform.ScoreStore
import com.aripd.reyon.platform.SettingsStore
import com.aripd.reyon.platform.SoundPlayer
import com.aripd.reyon.platform.appLocale
import com.aripd.reyon.platform.gatedBy
import com.aripd.reyon.ui.ReyonScreen
import com.aripd.reyon.ui.about.AboutScreen
import com.aripd.reyon.ui.common.LocalSettingsRoute
import com.aripd.reyon.ui.settings.LanguageScreen
import com.aripd.reyon.ui.theme.ReyonTheme
import com.aripd.reyon.ui.theme.isDark

/** Uygulamanın üç ekranı; Reyon'un dört modu [ReyonScreen]'in kendi içinde. */
private enum class Route { MAIN, ABOUT, LANGUAGE }

/**
 * Uygulama kökü: rekoru, ayarları ve paylaşılan ses çaları tutar.
 *
 * Tek uygulamalık bir kök — açılınca doğrudan Reyon'a girer, mod seçimi
 * ekranın kendi çiplerinde kalır. Ayarlar ve Hakkında üst çubuktaki dişliden
 * açılır ([LocalSettingsRoute]).
 *
 * [onQuit] uygulamadan çıkış: en üstteki geri hareketi ve bitiş kartlarındaki
 * çıkış düğmesi buraya bağlı.
 */
@Composable
fun ReyonApp(onQuit: () -> Unit = {}) {
    val context = LocalContext.current
    val scoreStore = remember { ScoreStore(context) }
    val settings = remember { SettingsStore(context) }
    var highScore by remember { mutableLongStateOf(scoreStore.best()) }

    var soundOn by remember { mutableStateOf(settings.soundEnabled) }
    val soundPlayer = remember { SoundPlayer(context) { soundOn } }
    DisposableEffect(Unit) {
        onDispose { soundPlayer.release() }
    }

    var theme by remember { mutableStateOf(settings.theme) }

    var hapticsOn by remember { mutableStateOf(settings.hapticsEnabled) }
    val systemHaptics = LocalHapticFeedback.current
    val gatedHaptics = remember(systemHaptics) { systemHaptics.gatedBy { hapticsOn } }

    var route by rememberSaveable { mutableStateOf(Route.MAIN) }
    // Kimliği sabit kalsın: LocalSettingsRoute static, her yeni değer bütün
    // ağacı yeniden besteler.
    val openSettings = remember { { route = Route.ABOUT } }

    // Kullanıcının açık dil seçimi ve o an çizilen dil. Seçim uygulanınca
    // etkinlik yeniden oluşur, ikisi de yeni değerle okunur.
    val effectiveLanguage = AppLocale.normalize(appLocale()) ?: "en"
    val selectedLanguage = remember(effectiveLanguage) { AppLocale.selected(context) }

    // Tema seçimi kökte uygulanır: ayarlardan değişince bütün ağaç yeni renklerle çizilir.
    ReyonTheme(choice = theme) {
        SystemBarIcons(dark = theme.isDark())
        CompositionLocalProvider(
            LocalSound provides soundPlayer,
            LocalHaptics provides gatedHaptics,
            // Ayarlar ekranının kendi üst çubuğunda dişli olmasın.
            LocalSettingsRoute provides openSettings.takeIf { route == Route.MAIN },
        ) {
            when (route) {
                Route.ABOUT -> AboutScreen(
                    soundOn = soundOn,
                    onToggleSound = {
                        soundOn = !soundOn
                        settings.soundEnabled = soundOn
                    },
                    hapticsOn = hapticsOn,
                    onToggleHaptics = {
                        hapticsOn = !hapticsOn
                        settings.hapticsEnabled = hapticsOn
                    },
                    theme = theme,
                    onTheme = { choice ->
                        theme = choice
                        settings.theme = choice
                    },
                    languageLabel = AppLocale.endonym(effectiveLanguage),
                    onLanguage = { route = Route.LANGUAGE },
                    onExit = { route = Route.MAIN },
                )

                Route.LANGUAGE -> LanguageScreen(
                    selected = selectedLanguage,
                    effective = effectiveLanguage,
                    onPick = { tag ->
                        route = Route.ABOUT
                        AppLocale.choose(context, tag)
                    },
                    onExit = { route = Route.ABOUT },
                )

                Route.MAIN -> ReyonScreen(
                    highScore = highScore,
                    onScore = { score ->
                        scoreStore.submit(score)
                        if (score > highScore) highScore = score
                    },
                    onExit = onQuit,
                )
            }
        }
    }
}

/**
 * Sistem çubuklarının simgeleri temayla gider. Çubuklar çoğunlukla gizli, ama
 * kenardan kaydırınca ve dokunarak keşif açıkken görünürler (MainActivity):
 * açık zeminde açık renk simge okunmaz.
 */
@Composable
private fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
