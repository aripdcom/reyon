package com.aripd.reyon.platform

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Argümanlı metin; rakamlar her dilde Latin kalır.
 *
 * `getString(id, sayı)` ve `stringResource(id, sayı)` metni cihazın yerel
 * ayarıyla biçimler. Arapça'da bu `%d`'yi Hint-Arap rakamına çevirir: zorluk
 * kartında `٩×١٢`, Tavla çipinde `١ نقطة`. Oysa protokol rakamların Latin
 * kalmasını istiyor ve HUD sayıları zaten öyle ([AppLocale.number]).
 * v0.43.0 cihaz koşumu, F2.
 *
 * Biçimleme [Locale.ROOT] ile yapılır: rakamlar Latin, ayırıcı sorunu yok
 * çünkü depodaki hiçbir metin `%f` ya da `%,d` taşımıyor —
 * `tools/check_strings.py` bunu denetler. Metnin kendisi yine dilin
 * kaynağından gelir, biçimlenen yalnızca sayılar.
 */
fun appText(resources: Resources, @StringRes id: Int, vararg args: Any): String =
    String.format(Locale.ROOT, resources.getString(id), *args)

/** Bağlamdan okuyan kolaylık; kaynakları bağlamdan alır. */
fun appText(context: Context, @StringRes id: Int, vararg args: Any): String =
    appText(context.resources, id, *args)

/** [stringResource]'un Latin rakam garantili karşılığı. */
@Composable
@ReadOnlyComposable
fun appString(@StringRes id: Int, vararg args: Any): String {
    // stringResource de böyle yapar: yapılandırma değişince metin yenilensin.
    LocalConfiguration.current
    return appText(LocalContext.current.resources, id, *args)
}
