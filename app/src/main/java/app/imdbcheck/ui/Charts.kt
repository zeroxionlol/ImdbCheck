package app.imdbcheck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.imdbcheck.Episode
import app.imdbcheck.formatRating
import kotlin.math.ceil
import kotlin.math.floor

private fun DrawScope.centeredText(
    tm: TextMeasurer, text: String, center: Offset, style: TextStyle,
) {
    val l = tm.measure(text, style)
    drawText(l, topLeft = Offset(center.x - l.size.width / 2f, center.y - l.size.height / 2f))
}

fun seasonAverage(eps: List<Episode>): Double? {
    val r = eps.mapNotNull { it.rating }
    return if (r.isEmpty()) null else r.average()
}

/**
 * Heatmap wie bei seriesgraph: Spalten = Staffeln, Zeilen = Episoden, Farbe = Wertung.
 * Letzte Zeile: Durchschnitt je Staffel.
 */
@Composable
fun Heatmap(episodes: List<Episode>, selected: Episode?, onSelect: (Episode) -> Unit) {
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    val maxEp = remember(episodes) { episodes.maxOfOrNull { it.number } ?: 0 }
    val byKey = remember(episodes) { episodes.associateBy { it.season to it.number } }
    val avgs = remember(episodes) { seasons.associateWith { s -> seasonAverage(episodes.filter { it.season == s }) } }
    val tm = rememberTextMeasurer()
    val d = LocalDensity.current

    val cellW = 44.dp; val cellH = 32.dp; val gap = 3.dp; val labelW = 38.dp; val headH = 26.dp; val avgGap = 8.dp
    val width = labelW + (cellW + gap) * seasons.size
    val height = headH + (cellH + gap) * (maxEp + 1) + avgGap

    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Canvas(
            Modifier
                .size(width, height)
                .pointerInput(episodes) {
                    detectTapGestures { p ->
                        val cw = (cellW + gap).toPx(); val ch = (cellH + gap).toPx()
                        val col = floor((p.x - labelW.toPx()) / cw).toInt()
                        val row = floor((p.y - headH.toPx()) / ch).toInt()
                        if (col in seasons.indices && row in 0 until maxEp) {
                            byKey[seasons[col] to row + 1]?.let(onSelect)
                        }
                    }
                }
        ) {
            val cw = cellW.toPx(); val ch = cellH.toPx(); val g = gap.toPx()
            val lw = labelW.toPx(); val hh = headH.toPx()
            val headStyle = TextStyle(color = Cine.TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val cellStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val r = CornerRadius(with(d) { 6.dp.toPx() })

            seasons.forEachIndexed { ci, s ->
                centeredText(tm, "S$s", Offset(lw + ci * (cw + g) + cw / 2, hh / 2), headStyle)
            }
            for (row in 0 until maxEp) {
                val y = hh + row * (ch + g)
                centeredText(tm, "E${row + 1}", Offset(lw / 2, y + ch / 2), headStyle)
                seasons.forEachIndexed { ci, s ->
                    val ep = byKey[s to row + 1] ?: return@forEachIndexed
                    val x = lw + ci * (cw + g)
                    drawRoundRect(Cine.rating(ep.rating), Offset(x, y), Size(cw, ch), r)
                    if (ep == selected) {
                        drawRoundRect(Color.White, Offset(x, y), Size(cw, ch), r, style = Stroke(2.5f * density))
                    }
                    centeredText(
                        tm, formatRating(ep.rating), Offset(x + cw / 2, y + ch / 2),
                        cellStyle.copy(color = Cine.onRating(ep.rating)),
                    )
                }
            }
            // Durchschnitt
            val y = hh + maxEp * (ch + g) + avgGap.toPx()
            centeredText(tm, "Ø", Offset(lw / 2, y + ch / 2), headStyle.copy(color = Cine.Gold, fontSize = 14.sp))
            seasons.forEachIndexed { ci, s ->
                val a = avgs[s]
                val x = lw + ci * (cw + g)
                drawRoundRect(Cine.rating(a), Offset(x, y), Size(cw, ch), r)
                drawRoundRect(Cine.Gold, Offset(x, y), Size(cw, ch), r, style = Stroke(1.5f * density))
                centeredText(tm, formatRating(a), Offset(x + cw / 2, y + ch / 2), cellStyle.copy(color = Cine.onRating(a)))
            }
        }
    }
}

/** Verlauf aller Episoden: Punkte je Staffel + Trendlinie je Staffel. */
@Composable
fun EpisodeGraph(episodes: List<Episode>, selected: Episode?, onSelect: (Episode) -> Unit) {
    val rated = remember(episodes) { episodes.filter { it.rating != null } }
    if (rated.isEmpty()) return
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    val tm = rememberTextMeasurer()

    val yMin = remember(rated) { (floor(rated.minOf { it.rating!! }) - 0.5).coerceIn(0.0, 9.0).let { floor(it) } }
    val yMax = 10.0

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val axisW = 30.dp; val step = 14.dp; val chartH = 230.dp; val bottomH = 24.dp; val padR = 12.dp
        val needed = axisW + step * (episodes.size + seasons.size) + padR
        val width = if (needed > maxWidth) needed else maxWidth
        // x-Position je Episode (mit kleiner Lücke zwischen Staffeln)
        val xIndex = remember(episodes) {
            var idx = 0f
            var last = -1
            episodes.map { e ->
                if (last != -1 && e.season != last) idx += 1f
                last = e.season
                idx.also { idx += 1f }
            }
        }
        val slots = (xIndex.lastOrNull() ?: 0f) + 1f

        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(
                Modifier
                    .size(width, chartH + bottomH)
                    .pointerInput(episodes, width) {
                        detectTapGestures { p ->
                            val plotW = size.width - axisW.toPx() - padR.toPx()
                            val dx = plotW / slots
                            var best = -1; var bestD = Float.MAX_VALUE
                            episodes.forEachIndexed { i, e ->
                                if (e.rating == null) return@forEachIndexed
                                val x = axisW.toPx() + dx * (xIndex[i] + 0.5f)
                                val dist = kotlin.math.abs(x - p.x)
                                if (dist < bestD) { bestD = dist; best = i }
                            }
                            if (best >= 0 && bestD < dx * 2 + 20) onSelect(episodes[best])
                        }
                    }
            ) {
                val aw = axisW.toPx(); val ch = chartH.toPx(); val top = 10.dp.toPx()
                val plotW = size.width - aw - padR.toPx()
                val dx = plotW / slots
                fun yOf(r: Double) = top + ((yMax - r) / (yMax - yMin)).toFloat() * (ch - top)
                fun xOf(i: Int) = aw + dx * (xIndex[i] + 0.5f)

                // Gitter
                val label = TextStyle(color = Cine.TextDim, fontSize = 11.sp)
                var v = ceil(yMin)
                while (v <= yMax) {
                    val y = yOf(v)
                    drawLine(Cine.Line.copy(alpha = 0.6f), Offset(aw, y), Offset(size.width - padR.toPx(), y), 1f)
                    centeredText(tm, v.toInt().toString(), Offset(aw / 2, y), label)
                    v += 1.0
                }

                seasons.forEachIndexed { si, s ->
                    val color = Cine.seasons[si % Cine.seasons.size]
                    val idx = episodes.indices.filter { episodes[it].season == s }
                    val pts = idx.filter { episodes[it].rating != null }
                    // Verbindungslinie
                    for (k in 1 until pts.size) {
                        drawLine(
                            color.copy(alpha = 0.45f),
                            Offset(xOf(pts[k - 1]), yOf(episodes[pts[k - 1]].rating!!)),
                            Offset(xOf(pts[k]), yOf(episodes[pts[k]].rating!!)),
                            1.5f * density,
                        )
                    }
                    // Trendlinie (lineare Regression)
                    if (pts.size >= 2) {
                        val xs = pts.map { xOf(it).toDouble() }; val ys = pts.map { episodes[it].rating!! }
                        val mx = xs.average(); val my = ys.average()
                        val den = xs.sumOf { (it - mx) * (it - mx) }
                        val slope = if (den == 0.0) 0.0 else xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
                        val x1 = xs.first(); val x2 = xs.last()
                        val y1 = (my + slope * (x1 - mx)).coerceIn(yMin, yMax)
                        val y2 = (my + slope * (x2 - mx)).coerceIn(yMin, yMax)
                        drawLine(
                            color, Offset(x1.toFloat(), yOf(y1)), Offset(x2.toFloat(), yOf(y2)), 3f * density,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
                        )
                    }
                    pts.forEach { i ->
                        val c = Offset(xOf(i), yOf(episodes[i].rating!!))
                        drawCircle(color, 4.dp.toPx(), c)
                        if (episodes[i] == selected) drawCircle(Color.White, 7.dp.toPx(), c, style = Stroke(2f * density))
                    }
                    if (idx.isNotEmpty()) {
                        val cx = (xOf(idx.first()) + xOf(idx.last())) / 2
                        centeredText(tm, "S$s", Offset(cx, ch + bottomH.toPx() / 2), label.copy(color = color, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}
