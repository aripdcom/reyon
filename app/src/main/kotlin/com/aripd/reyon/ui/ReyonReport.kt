package com.aripd.reyon.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aripd.reyon.R
import com.aripd.reyon.platform.AppLocale
import com.aripd.reyon.platform.appLocale
import com.aripd.reyon.platform.appString
import com.aripd.reyon.ui.common.FitText
import com.aripd.reyon.ui.common.kpiLabelStyle
import com.aripd.reyon.ui.common.monoStyle
import com.aripd.reyon.ui.theme.Reyon
import kotlin.math.roundToInt

/**
 * Uyum bandı: sonuç, aynı vakayı çözen uzmana oranla verilir ve yıldızların
 * yerini alır. Eşikler tasarım taslağındaki öneri (1.1.0): ≥ %90 uzman düzeyi,
 * %75–89 iyi, %50–74 gelişmeli, < %50 zayıf.
 */
enum class ComplianceBand(@StringRes val label: Int) {
    EXPERT(R.string.band_expert),
    GOOD(R.string.band_good),
    DEVELOPING(R.string.band_developing),
    WEAK(R.string.band_weak),
    ;

    companion object {
        fun of(percent: Int): ComplianceBand = when {
            percent >= 90 -> EXPERT
            percent >= 75 -> GOOD
            percent >= 50 -> DEVELOPING
            else -> WEAK
        }
    }
}

/** Bandın zemin ve yazı rengi (hap) ile ilerleme çubuğunun rengi. */
private class BandColors(val background: Color, val content: Color, val bar: Color)

@Composable
private fun ComplianceBand.colors(): BandColors {
    val t = Reyon.tokens
    return when (this) {
        ComplianceBand.EXPERT -> BandColors(t.okWeak, t.ok, t.ok)
        ComplianceBand.GOOD -> BandColors(t.brandWeak, t.onBrandWeak, MaterialTheme.colorScheme.primary)
        ComplianceBand.DEVELOPING -> BandColors(t.warnWeak, t.onWarnWeak, t.warn)
        ComplianceBand.WEAK -> BandColors(t.badWeak, t.onBadWeak, t.bad)
    }
}

/** Uyum bandının hapı: "Gelişmeli" gibi; renk tek başına bilgi taşımaz, ad yazılıdır. */
@Composable
fun BandPill(band: ComplianceBand, modifier: Modifier = Modifier) {
    val c = band.colors()
    Text(
        text = stringResource(band.label),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        color = c.content,
        maxLines = 1,
        modifier = modifier
            .background(c.background, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** İnce, yuvarlak ilerleme çubuğu. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val track = Reyon.tokens.divider
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2)),
    ) {
        drawRect(track)
        drawRect(color, size = Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    }
}

/**
 * Uzmana kıyasla sonuç: başlık ve bant, büyük değer "/ uzman", uzmana oran
 * çubuğu ve altında yüzde. Satış'ın raf verimi ve Sipariş'in haftalık kârı.
 */
@Composable
fun ExpertMetric(
    label: String,
    value: Int,
    expert: Int,
    percent: Int,
    modifier: Modifier = Modifier,
    showBand: Boolean = true,
    large: Boolean = true,
) {
    val band = ComplianceBand.of(percent)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FitText(
                text = label.uppercase(appLocale()),
                style = kpiLabelStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (showBand) BandPill(band)
        }
        Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = value.toString(),
                style = if (large) monoStyle(36f, 44f) else monoStyle(28f, 34f),
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = appString(R.string.report_per_expert_fmt, expert),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        ProgressBar(fraction = percent / 100f, color = band.colors().bar, modifier = Modifier.padding(top = 6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
            Text(
                text = appString(R.string.report_of_expert_fmt, percent),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = appString(R.string.report_expert_is_fmt, 100),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Kişisel en iyi: rekorun yerini alan sade işaret. */
@Composable
fun PersonalBestPill(modifier: Modifier = Modifier) {
    val t = Reyon.tokens
    Text(
        text = stringResource(R.string.report_personal_best),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        color = t.ok,
        maxLines = 1,
        modifier = modifier
            .background(t.okWeak, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Bulgunun tonu: noktasının rengi. Renk tek başına anlam taşımaz; cümle yazılıdır. */
enum class FindingTone { BAD, WARN, OK, NOTE }

/** Raporun bir bulgusu: metin kaynağı ve argümanları. */
class Finding(val tone: FindingTone, @StringRes val text: Int, vararg val args: Any)

/**
 * Sipariş haftasının bulguları: hizmet düzeyi ve stok devri uzmanla kıyaslanır.
 * Eşikler bilinçli olarak kaba: 5 puanın ya da 0,5 devrin altındaki fark ölçüm
 * gürültüsü sayılır, uzmanla "aynı düzeyde" denir.
 */
fun orderFindings(service: Int, targetService: Int, turnover: Float, targetTurnover: Float): List<Finding> {
    val out = ArrayList<Finding>()
    val serviceGap = targetService - service
    val turnGap = targetTurnover - turnover
    val serviceLow = serviceGap > SERVICE_SLACK
    val turnLow = turnGap > TURNOVER_SLACK
    out += if (serviceLow) Finding(FindingTone.BAD, R.string.report_service_low_fmt, serviceGap) else Finding(FindingTone.OK, R.string.report_service_ok)
    out += when {
        turnLow -> Finding(FindingTone.WARN, R.string.report_turnover_low_fmt, decimal1(turnGap))
        -turnGap > TURNOVER_SLACK && serviceLow -> Finding(FindingTone.WARN, R.string.report_turnover_tight)
        else -> Finding(FindingTone.OK, R.string.report_turnover_ok)
    }
    if (serviceLow && turnLow) out += Finding(FindingTone.NOTE, R.string.report_wrong_mix)
    return out
}

private const val SERVICE_SLACK = 5
private const val TURNOVER_SLACK = 0.5f

/** Bir ondalıklı sayı, Latin rakamla ve dilin ayırıcısıyla (bkz. AppLocale.decimal). */
private fun decimal1(x: Float): String = AppLocale.decimal((x * 10f).roundToInt() / 10f)

/** Bulgular listesi: tonlu nokta ve cümle. */
@Composable
fun FindingRow(finding: Finding, text: String) {
    val t = Reyon.tokens
    val dot = when (finding.tone) {
        FindingTone.BAD -> t.bad
        FindingTone.WARN -> t.warn
        FindingTone.OK -> t.ok
        FindingTone.NOTE -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(dot),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp))
    }
}
