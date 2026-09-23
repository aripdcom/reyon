package com.aripd.reyon.ui.common

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aripd.reyon.R
import com.aripd.reyon.platform.ShareCard
import com.aripd.reyon.platform.ShareContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.aripd.reyon.platform.appString

/**
 * Bitiş kartlarındaki "Paylaş" düğmesi: kartı üretir ve sistemin paylaşım
 * sayfasını açar. [compact] verilirse yalnızca simge gösterir (dar satırlar).
 */
@Composable
fun ShareButton(content: ShareContent, modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(content)
    var busy by remember { mutableStateOf(false) }
    val label = stringResource(R.string.share)

    OutlinedButton(
        onClick = {
            if (busy) return@OutlinedButton
            busy = true
            scope.launch {
                try {
                    ShareCard.share(context, latest)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
        modifier = if (compact) {
            modifier.semantics { contentDescription = label }
        } else {
            modifier.fillMaxWidth()
        },
    ) {
        Icon(imageVector = ReyonIcons.Share, contentDescription = null, modifier = Modifier.size(18.dp))
        if (!compact) {
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}

/**
 * Paylaşım kartı için mod etiketi: günlük bulmacada tarih de yazılır
 * (arkadaşlar aynı günün sonucunu karşılaştırır), serbest modda yalnızca "Serbest".
 */
@Composable
fun modeShareLabel(daily: Boolean, epochDay: Long?): String = if (daily) {
    val day = epochDay ?: LocalDate.now().toEpochDay()
    appString(
        R.string.share_daily_fmt,
        LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
    )
} else {
    stringResource(R.string.mode_free)
}
