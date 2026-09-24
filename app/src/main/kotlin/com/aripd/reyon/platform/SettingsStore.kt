package com.aripd.reyon.platform

import android.content.Context
import com.aripd.reyon.ui.theme.ThemeChoice

/** Platform ayarları; yalnızca cihazda saklanır. */
class SettingsStore(context: Context) {

    // attachBaseContext sırasında (AppLocale.wrap) uygulama bağlamı henüz
    // kurulmamış olabilir; o durumda verilen bağlamla devam edilir.
    private val prefs = (context.applicationContext ?: context)
        .getSharedPreferences("reyon_settings", Context.MODE_PRIVATE)

    /**
     * Ses efektleri. Varsayılan kapalı (1.1.0): uygulama mağazada, eğitimde ve
     * toplantıda açılıyor; ses isteyen ayarlardan açar.
     */
    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND, value).apply()
        }

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HAPTICS, value).apply()
        }

    /** Tema: açık (varsayılan), koyu ya da telefonun ayarı. */
    var theme: ThemeChoice
        get() = ThemeChoice.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeChoice.LIGHT
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    /**
     * Seçili dilin BCP-47 etiketi, ya da [AppLocale.SYSTEM] (telefonun dili).
     *
     * Yalnızca Android 8-12'de kullanılır; 13+ sürümlerde doğru kaynak sistemin
     * kendi uygulama-dili ayarıdır (bkz. [AppLocale.selected]).
     */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, AppLocale.SYSTEM) ?: AppLocale.SYSTEM
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
        }

    private companion object {
        const val KEY_SOUND = "sound_enabled"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
    }
}
