package com.aripd.reyon.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aripd.reyon.R
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.ui.common.GameTopBar

/** Testlerin dil listesini bulması için. */
const val LANGUAGE_LIST_TAG = "language_list"

/**
 * Dil seçimi.
 *
 * İlk satır "telefonun dili"dir ([AppLocale.SYSTEM]); altında desteklenen
 * diller kendi adlarıyla, alfabetik sırada ([AppLocale.PICKER_ORDER])
 * listelenir. Seçim uygulanınca etkinlik yeniden oluşur, o yüzden ekran
 * seçimden sonra kendiliğinden kapanır.
 *
 * [selected] kullanıcının açık seçimi, [effective] o an çizilen dil: kullanıcı
 * "telefonun dili"nde kaldıysa ikincisi hangi dile düşüldüğünü gösterir.
 */
@Composable
fun LanguageScreen(
    selected: String,
    effective: String,
    onPick: (String) -> Unit,
    onExit: () -> Unit,
) {
    BackHandler { onExit() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        GameTopBar(title = stringResource(R.string.language_title), onExit = onExit)
        LazyColumn(
            modifier = Modifier.testTag(LANGUAGE_LIST_TAG),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                LanguageRow(
                    label = stringResource(R.string.language_system),
                    detail = if (selected == AppLocale.SYSTEM) AppLocale.endonym(effective) else null,
                    checked = selected == AppLocale.SYSTEM,
                    onClick = { onPick(AppLocale.SYSTEM) },
                )
            }
            items(AppLocale.PICKER_ORDER) { tag ->
                LanguageRow(
                    label = AppLocale.endonym(tag),
                    detail = null,
                    checked = selected == tag,
                    onClick = { onPick(tag) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(label: String, detail: String?, checked: Boolean, onClick: () -> Unit) {
    val state = stringResource(if (checked) R.string.language_selected else R.string.language_select)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label · $state" },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            if (checked) {
                Text(
                    text = "✓",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
