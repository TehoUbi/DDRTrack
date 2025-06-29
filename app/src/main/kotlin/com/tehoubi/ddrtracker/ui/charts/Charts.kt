package com.tehoubi.ddrtracker.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/*
 * Small Canvas charts. Specs, kept the same everywhere: 2dp lines with round
 * joins, a 10% area wash, end markers r=4dp with a 2dp surface ring, bars no
 * thicker than 24dp with a 4dp rounded data end and square baseline, a 2dp
 * surface gap between touching fills, hairline solid gridlines, and axis and
 * label text in text colours (never the series colour). Every chart shows a
 * tooltip for the touched mark.
 */

/** Categorical slots for the clear-lamp stack, checked for CVD separation on both surfaces. */
object LampGroupColors {
    val failed = Color(0xFFE5484D)
    val clear = Color(0xFF6E6AE8)
    val fullCombo = Color(0xFF12A594)
    val perfect = Color(0xFFAD8200)
}

data class ChartPoint(val x: Float, val y: Float, val label: String)

data class ChartBar(val label: String, val value: Float, val tooltip: String)

data class StackSegment(val value: Int, val color: Color, val name: String)

data class StackRow(val label: String, val segments: List<StackSegment>) {
    val total: Int get() = segments.sumOf { it.value }
}

private val axisWidth = 40.dp

@Composable
private fun axisStyle() = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Clean tick values (1, 2, 5 x 10^n steps) covering [min]..[max]. */
internal fun niceTicks(min: Float, max: Float, count: Int = 4): List<Float> {
    if (max <= min) return listOf(min)
    val raw = (max - min) / count
    val magnitude = 10f.pow(floor(log10(raw)))
    val step = listOf(1f, 2f, 2.5f, 5f, 10f).map { it * magnitude }.first { it >= raw }
    val start = floor(min / step) * step
    val end = ceil(max / step) * step
    return generateSequence(start) { it + step }.takeWhile { it <= end + step / 2 }.toList()
}

@Composable
private fun Tooltip(text: String?, modifier: Modifier = Modifier) {
    Box(modifier.height(24.dp), contentAlignment = Alignment.CenterStart) {
        if (text != null) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * A single-series line over time. [step] draws a staircase (for values that
 * hold until they change, like a personal best). Touch or drag to inspect.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 160.dp,
    yFormat: (Float) -> String = { it.toInt().toString() },
    yMin: Float? = null,
    yMax: Float? = null,
    step: Boolean = false,
    endLabel: Boolean = true,
) {
    if (points.isEmpty()) return
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = MaterialTheme.colorScheme.onSurface

    val lo = yMin ?: points.minOf { it.y }
    val hi = yMax ?: points.maxOf { it.y }
    val ticks = niceTicks(lo, if (hi == lo) lo + 1 else hi)
    val y0 = yMin ?: ticks.first()
    val y1 = yMax ?: ticks.last()
    val x0 = points.minOf { it.x }
    val x1 = points.maxOf { it.x }.let { if (it == x0) x0 + 1 else it }

    Column(modifier) {
        Tooltip(selected?.let { points[it].label })
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(points) {
                    fun pick(x: Float) {
                        val plotW = size.width - axisWidth.toPx()
                        val target = x0 + (x - axisWidth.toPx()) / plotW * (x1 - x0)
                        selected = points.indices.minByOrNull { abs(points[it].x - target) }
                    }
                    detectTapGestures { pick(it.x) }
                }
                .pointerInput(points) {
                    detectDragGestures(onDragEnd = {}) { change, _ ->
                        val plotW = size.width - axisWidth.toPx()
                        val target = x0 + (change.position.x - axisWidth.toPx()) / plotW * (x1 - x0)
                        selected = points.indices.minByOrNull { abs(points[it].x - target) }
                    }
                },
        ) {
            val left = axisWidth.toPx()
            val top = 8.dp.toPx()
            val plotW = size.width - left - 8.dp.toPx()
            val plotH = size.height - top - 4.dp.toPx()
            fun px(x: Float) = left + (x - x0) / (x1 - x0) * plotW
            fun py(y: Float) = top + plotH - (y - y0) / (y1 - y0).coerceAtLeast(1e-6f) * plotH

            ticks.filter { it in y0..y1 }.forEach { t ->
                val y = py(t)
                drawLine(grid, Offset(left, y), Offset(left + plotW, y), strokeWidth = 1f)
                drawAxisText(measurer, yFormat(t), axis, Offset(0f, y))
            }

            val line = Path()
            points.forEachIndexed { i, p ->
                val x = px(p.x); val y = py(p.y)
                when {
                    i == 0 -> line.moveTo(x, y)
                    step -> { line.lineTo(x, py(points[i - 1].y)); line.lineTo(x, y) }
                    else -> line.lineTo(x, y)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(px(points.last().x), top + plotH)
                lineTo(px(points.first().x), top + plotH)
                close()
            }
            drawPath(area, color.copy(alpha = 0.10f))
            drawPath(line, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

            val last = points.last()
            drawMarker(Offset(px(last.x), py(last.y)), color, surface)
            if (endLabel) {
                val text = measurer.measure(yFormat(last.y), axis.copy(color = ink))
                val tx = (px(last.x) - text.size.width - 6.dp.toPx()).coerceAtLeast(left)
                val ty = (py(last.y) - text.size.height - 6.dp.toPx()).coerceAtLeast(0f)
                drawText(text, topLeft = Offset(tx, ty))
            }
            selected?.let { i ->
                val p = points[i]
                drawLine(grid.copy(alpha = 0.8f), Offset(px(p.x), top), Offset(px(p.x), top + plotH), strokeWidth = 1f)
                drawMarker(Offset(px(p.x), py(p.y)), color, surface)
            }
        }
    }
}

/** Single-series columns, e.g. plays per week. Touch a column for its value. */
@Composable
fun ColumnChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 140.dp,
    xLabels: Pair<String, String>? = null,
) {
    if (bars.isEmpty()) return
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val axis = axisStyle()
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val ticks = niceTicks(0f, bars.maxOf { it.value }.coerceAtLeast(1f), count = 3)
    val top = ticks.last()

    Column(modifier) {
        Tooltip(selected?.let { bars[it].tooltip })
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(bars) {
                    detectTapGestures { pos ->
                        val slot = (size.width - axisWidth.toPx()) / bars.size
                        selected = ((pos.x - axisWidth.toPx()) / slot).toInt().takeIf { it in bars.indices }
                    }
                },
        ) {
            val left = axisWidth.toPx()
            val plotH = size.height - 4.dp.toPx()
            val plotW = size.width - left
            val slot = plotW / bars.size
            val barW = minOf(24.dp.toPx(), slot - 2.dp.toPx()).coerceAtLeast(1f)
            ticks.forEach { t ->
                val y = plotH - t / top * plotH
                drawLine(grid, Offset(left, y), Offset(left + plotW, y), strokeWidth = 1f)
                drawAxisText(measurer, t.toInt().toString(), axis, Offset(0f, y))
            }
            bars.forEachIndexed { i, b ->
                if (b.value <= 0f) return@forEachIndexed
                val h = b.value / top * plotH
                val x = left + i * slot + (slot - barW) / 2
                val alpha = if (selected == null || selected == i) 1f else 0.45f
                drawTopRoundedBar(Offset(x, plotH - h), Size(barW, h), color.copy(alpha = alpha), 4.dp.toPx())
            }
        }
        xLabels?.let { (first, last) ->
            Row(Modifier.fillMaxWidth().padding(start = axisWidth)) {
                Text(first, style = axis, modifier = Modifier.weight(1f))
                Text(last, style = axis)
            }
        }
    }
}

/**
 * Horizontal 100%-style stacks, one row per category, with a legend above.
 * Rows share one scale (the largest total) so lengths compare.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StackedRows(rows: List<StackRow>, legend: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    var selected by remember(rows) { mutableStateOf<Int?>(null) }
    val max = rows.maxOf { it.total }.coerceAtLeast(1)
    val gap = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            legend.forEach { (name, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
                    Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        Tooltip(selected?.let { i -> rows[i].let { r -> "${r.label}: " + r.segments.filter { it.value > 0 }.joinToString(" · ") { "${it.value} ${it.name}" } } })
        rows.forEachIndexed { i, row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.pointerInput(row) { detectTapGestures { selected = i } },
            ) {
                Text(row.label, style = axisStyle(), modifier = Modifier.width(axisWidth))
                Canvas(Modifier.weight(1f).height(16.dp)) {
                    val full = size.width - 36.dp.toPx()
                    var x = 0f
                    val nonEmpty = row.segments.filter { it.value > 0 }
                    nonEmpty.forEachIndexed { k, s ->
                        val w = s.value.toFloat() / max * full
                        val alpha = if (selected == null || selected == i) 1f else 0.45f
                        val isLast = k == nonEmpty.lastIndex
                        if (isLast) drawRightRoundedBar(Offset(x, 0f), Size(w, size.height), s.color.copy(alpha = alpha), 4.dp.toPx())
                        else drawRect(s.color.copy(alpha = alpha), Offset(x, 0f), Size(w, size.height))
                        x += w
                        if (!isLast) {
                            drawRect(gap, Offset(x - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
                        }
                    }
                }
                Text(row.total.toString(), style = axisStyle().copy(color = MaterialTheme.colorScheme.onSurface), modifier = Modifier.width(28.dp))
            }
        }
    }
}

private fun DrawScope.drawAxisText(measurer: TextMeasurer, text: String, style: TextStyle, at: Offset) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(at.x, (at.y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)))
}

private fun DrawScope.drawMarker(center: Offset, color: Color, surface: Color) {
    drawCircle(surface, radius = 6.dp.toPx(), center = center)
    drawCircle(color, radius = 4.dp.toPx(), center = center)
}

private fun DrawScope.drawTopRoundedBar(topLeft: Offset, size: Size, color: Color, radius: Float) {
    val r = minOf(radius, size.width / 2, size.height)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = topLeft.x, top = topLeft.y, right = topLeft.x + size.width, bottom = topLeft.y + size.height,
                topLeftCornerRadius = CornerRadius(r), topRightCornerRadius = CornerRadius(r),
            )
        )
    }
    drawPath(path, color)
}

private fun DrawScope.drawRightRoundedBar(topLeft: Offset, size: Size, color: Color, radius: Float) {
    val r = minOf(radius, size.height / 2, size.width)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = topLeft.x, top = topLeft.y, right = topLeft.x + size.width, bottom = topLeft.y + size.height,
                topRightCornerRadius = CornerRadius(r), bottomRightCornerRadius = CornerRadius(r),
            )
        )
    }
    drawPath(path, color)
}
