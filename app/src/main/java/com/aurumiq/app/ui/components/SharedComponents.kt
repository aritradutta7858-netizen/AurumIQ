package com.aurumiq.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurumiq.app.data.model.DataSource
import com.aurumiq.app.data.model.SignalCategory
import com.aurumiq.app.ui.theme.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════════
// Shared UI Components
// ═══════════════════════════════════════════════════════════════

/**
 * Data source badge — clearly labels the origin of data.
 * This is a critical transparency requirement.
 */
@Composable
fun DataSourceBadge(dataSource: DataSource, modifier: Modifier = Modifier) {
    val (text, color) = when (dataSource) {
        DataSource.DEMO -> "⚠ DEMO SYNTHETIC DATA — NOT LIVE MCX DATA" to BadgeWarning
        DataSource.IMPORTED -> "IMPORTED DATA" to BadgeInfo
        DataSource.LIVE -> "LIVE DATA" to BadgeSuccess
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
        border = null
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Metric label badge for transparency.
 */
@Composable
fun MetricLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        fontWeight = FontWeight.Medium
    )
}

/**
 * Signal category chip with color coding.
 */
@Composable
fun SignalChip(category: SignalCategory, modifier: Modifier = Modifier) {
    val (color, icon) = when (category) {
        SignalCategory.NORMAL -> SignalGray to "●"
        SignalCategory.WATCH -> SignalAmber to "◐"
        SignalCategory.HIGH_RELATIVE_PREMIUM -> SignalGreen to "▲"
        SignalCategory.HIGH_RELATIVE_DISCOUNT -> SignalRed to "▼"
        SignalCategory.LIQUIDITY_WARNING -> SignalAmber to "⚠"
        SignalCategory.INSUFFICIENT_DATA -> SignalGray to "○"
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = "$icon ${category.displayName}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Dashboard stat card.
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    valueColor: Color = TextPrimary,
    labelType: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = SurfaceContainerDark,
        border = null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
                if (labelType != null) {
                    MetricLabel(labelType)
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = valueColor,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

/**
 * Simple line chart component.
 */
@Composable
fun LineChart(
    data: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    lineColor: Color = ChartLine1,
    fillColor: Color = ChartFill1,
    showGrid: Boolean = true,
    label: String? = null,
    secondaryData: List<Pair<Long, Double>>? = null,
    secondaryColor: Color = ChartLine2,
    zeroLine: Boolean = false
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        return
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val width = size.width
            val height = size.height
            val paddingLeft = 60f
            val paddingBottom = 24f
            val paddingTop = 8f
            val chartWidth = width - paddingLeft
            val chartHeight = height - paddingBottom - paddingTop

            val allValues = data.map { it.second } + (secondaryData?.map { it.second } ?: emptyList())
            val minVal = allValues.minOrNull() ?: 0.0
            val maxVal = allValues.maxOrNull() ?: 1.0
            val range = if (maxVal == minVal) 1.0 else maxVal - minVal

            // Adjust for zero line
            val effectiveMin = if (zeroLine) minOf(minVal, 0.0) else minVal
            val effectiveMax = if (zeroLine) maxOf(maxVal, 0.0) else maxVal
            val effectiveRange = if (effectiveMax == effectiveMin) 1.0 else effectiveMax - effectiveMin

            fun xPos(index: Int, total: Int): Float =
                paddingLeft + (index.toFloat() / maxOf(1, total - 1)) * chartWidth

            fun yPos(value: Double): Float =
                paddingTop + ((effectiveMax - value) / effectiveRange * chartHeight).toFloat()

            // Grid lines
            if (showGrid) {
                for (i in 0..4) {
                    val y = paddingTop + (i / 4f) * chartHeight
                    drawLine(ChartGrid, Offset(paddingLeft, y), Offset(width, y), strokeWidth = 1f)

                    val gridValue = effectiveMax - (i / 4.0) * effectiveRange
                    drawContext.canvas.nativeCanvas.drawText(
                        formatCompact(gridValue),
                        4f, y + 4f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#6E7681")
                            textSize = 22f
                            isAntiAlias = true
                        }
                    )
                }
            }

            // Zero line
            if (zeroLine && effectiveMin < 0 && effectiveMax > 0) {
                val zeroY = yPos(0.0)
                drawLine(ChartZeroLine, Offset(paddingLeft, zeroY), Offset(width, zeroY), strokeWidth = 2f)
            }

            // Draw secondary line
            if (secondaryData != null && secondaryData.size > 1) {
                val path2 = Path()
                secondaryData.forEachIndexed { i, (_, v) ->
                    val x = xPos(i, secondaryData.size)
                    val y = yPos(v)
                    if (i == 0) path2.moveTo(x, y) else path2.lineTo(x, y)
                }
                drawPath(path2, secondaryColor, style = Stroke(width = 2f))
            }

            // Draw primary line
            if (data.size > 1) {
                val path = Path()
                data.forEachIndexed { i, (_, v) ->
                    val x = xPos(i, data.size)
                    val y = yPos(v)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 2.5f))

                // Fill
                val fillPath = Path()
                data.forEachIndexed { i, (_, v) ->
                    val x = xPos(i, data.size)
                    val y = yPos(v)
                    if (i == 0) fillPath.moveTo(x, y) else fillPath.lineTo(x, y)
                }
                fillPath.lineTo(xPos(data.size - 1, data.size), paddingTop + chartHeight)
                fillPath.lineTo(paddingLeft, paddingTop + chartHeight)
                fillPath.close()
                drawPath(fillPath, fillColor)
            }
        }
    }
}

/**
 * Section header with optional label.
 */
@Composable
fun SectionHeader(
    title: String,
    labelType: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        if (labelType != null) {
            MetricLabel(labelType)
        }
    }
}

// ── Formatting Utilities ────────────────────────────────────

private val compactFormat = DecimalFormat("#,##0.#")
private val percentFormat = DecimalFormat("+0.00;-0.00")
private val priceFormat = DecimalFormat("#,##0.00")
private val dateFormat = SimpleDateFormat("dd MMM yy", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun formatCompact(value: Double): String = compactFormat.format(value)
fun formatPercent(value: Double): String = "${percentFormat.format(value)}%"
fun formatPrice(value: Double): String = "₹${priceFormat.format(value)}"
fun formatDate(epochMillis: Long): String = dateFormat.format(Date(epochMillis))
fun formatCurrency(value: Double): String {
    return when {
        kotlin.math.abs(value) >= 10_000_000 -> "₹${compactFormat.format(value / 10_000_000)}Cr"
        kotlin.math.abs(value) >= 100_000 -> "₹${compactFormat.format(value / 100_000)}L"
        else -> "₹${priceFormat.format(value)}"
    }
}

fun colorForValue(value: Double): Color = when {
    value > 0.01 -> SignalGreen
    value < -0.01 -> SignalRed
    else -> TextSecondary
}
