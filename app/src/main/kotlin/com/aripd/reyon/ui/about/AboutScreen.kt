package com.aripd.reyon.ui.about

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aripd.reyon.R
import com.aripd.reyon.platform.Links
import com.aripd.reyon.platform.appString
import com.aripd.reyon.platform.installedVersion
import com.aripd.reyon.ui.common.GameTopBar

/** Testlerin ayar satırlarını bulması için. */
const val ABOUT_SOUND_TAG = "about_sound"
const val ABOUT_HAPTICS_TAG = "about_haptics"
const val ABOUT_LANGUAGE_TAG = "about_language"

/** Uygulamada kullanılan açık kaynak bileşen; metinler yerelleştirilir. */
private class OssComponent(
    @StringRes val nameRes: Int,
    val license: String,
    @StringRes val descRes: Int,
    val url: String,
)

private val Components = listOf(
    OssComponent(R.string.about_lic_androidx_name, "Apache-2.0", R.string.about_lic_androidx_desc, Links.ANDROIDX),
    OssComponent(R.string.about_lic_kotlin_name, "Apache-2.0", R.string.about_lic_kotlin_desc, Links.KOTLIN),
)

/**
 * Ayarlar ve Hakkında tek ekranda: ses, titreşim, dil; sonra sürüm, gizlilik
 * özeti, bağlantılar ve açık kaynak lisansları.
 *
 * ZA Games'te ayarlar ana menünün başlığındaydı; tek ekranlı uygulamada ana
 * menü yok, o yüzden buraya indi ve üst çubuktaki dişliden açılıyor.
 * Uygulama ağa çıkmaz; bağlantılar cihazın tarayıcısında açılır.
 */
@Composable
fun AboutScreen(
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    hapticsOn: Boolean,
    onToggleHaptics: () -> Unit,
    languageLabel: String,
    onLanguage: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember { installedVersion(context) }
    var showApache by rememberSaveable { mutableStateOf(false) }
    val apacheText = remember(showApache) {
        if (showApache) {
            context.resources.openRawResource(R.raw.license_apache2).bufferedReader().use { it.readText() }
        } else {
            ""
        }
    }
    BackHandler { onExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        GameTopBar(title = stringResource(R.string.about_title), onExit = onExit)
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = appString(R.string.about_version_fmt, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.chip_no_ads))
                    Chip(stringResource(R.string.chip_no_trackers))
                    Chip(stringResource(R.string.chip_no_permissions))
                }
            }

            item { SectionTitle(stringResource(R.string.settings_title)) }
            item {
                ToggleRow(
                    label = stringResource(R.string.settings_sound),
                    action = stringResource(if (soundOn) R.string.sound_off else R.string.sound_on),
                    checked = soundOn,
                    onClick = onToggleSound,
                    tag = ABOUT_SOUND_TAG,
                )
            }
            item {
                ToggleRow(
                    label = stringResource(R.string.settings_haptics),
                    action = stringResource(if (hapticsOn) R.string.haptics_off else R.string.haptics_on),
                    checked = hapticsOn,
                    onClick = onToggleHaptics,
                    tag = ABOUT_HAPTICS_TAG,
                )
            }
            item {
                val title = stringResource(R.string.language_title)
                DetailRow(
                    label = title,
                    detail = languageLabel,
                    description = "$title · $languageLabel",
                    onClick = onLanguage,
                    tag = ABOUT_LANGUAGE_TAG,
                )
            }

            item { InfoCard(stringResource(R.string.about_privacy_summary)) }

            item { SectionTitle(stringResource(R.string.about_links)) }
            item {
                LinkButton(stringResource(R.string.about_website), Links.SITE)
                LinkButton(stringResource(R.string.about_source), Links.SOURCE)
                LinkButton(stringResource(R.string.about_report), Links.REPORT)
                LinkButton(stringResource(R.string.about_privacy), Links.PRIVACY)
                MailButton(stringResource(R.string.about_contact), Links.CONTACT)
            }

            item { SectionTitle(stringResource(R.string.about_licenses)) }
            item {
                InfoCard(stringResource(R.string.about_app_license))
                TextButton(onClick = { Links.open(context, Links.LICENSE) }) {
                    Text(stringResource(R.string.about_app_license_link))
                }
            }
            item {
                Text(
                    text = stringResource(R.string.about_licenses_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                )
            }
            items(Components.size) { index -> ComponentCard(Components[index]) }
            item {
                OutlinedButton(onClick = { showApache = !showApache }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (showApache) R.string.about_license_hide else R.string.about_license_show))
                }
                if (showApache) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = apacheText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Açma/kapama satırı. Görünen etiket ayarın adı, ekran okuyucuya okunan ise
 * dokununca ne olacağı — ZA'nın menü düğmeleriyle aynı kural.
 */
@Composable
private fun ToggleRow(label: String, action: String, checked: Boolean, onClick: () -> Unit, tag: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { contentDescription = action },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** Başka bir ekrana götüren satır; sağında o anki değer yazar. */
@Composable
private fun DetailRow(label: String, detail: String, description: String, onClick: () -> Unit, tag: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Chip(label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LinkButton(label: String, url: String) {
    val context = LocalContext.current
    ActionButton(label, url.removePrefix("https://")) { Links.open(context, url) }
}

@Composable
private fun MailButton(label: String, address: String) {
    val context = LocalContext.current
    ActionButton(label, address) { Links.email(context, address) }
}

@Composable
private fun ActionButton(label: String, detail: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ComponentCard(component: OssComponent) {
    val context = LocalContext.current
    Surface(
        onClick = { Links.open(context, component.url) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(component.nameRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = component.license,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(component.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Text(
                text = component.url.removePrefix("https://"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
        }
    }
}
