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
 * Heatmap wie bei seriesgraph. Senkrecht: Spalten = Staffeln, Zeilen = Folgen.
 * Waagerecht: Zeilen = Staffeln, Spalten = Folgen. Zusätzlich der Durchschnitt je Staffel (Ø).
 */
@Composable
fun Heatmap(episodes: List<Episode>, selected: Episode?, horizontal: Boolean, onSelect: (Episode) -> Unit) {
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    val maxEp = remember(episodes) { episodes.maxOfOrNull { it.number } ?: 0 }
    val byKey = remember(episodes) { episodes.associateBy { it.season to it.number } }
    val avgs = remember(episodes) { seasons.associateWith { s -> seasonAverage(episodes.filter { it.season == s }) } }
    val tm = rememberTextMeasurer()

    val cellW = 44.dp; val cellH = 32.dp; val gap = 3.dp; val avgGap = 8.dp
    val labelW = if (horizontal) 40.dp else 38.dp
    val headH = 26.dp
    // Senkrecht: Spalten = Staffeln, Zeilen = Folgen (+ Ø-Zeile)
    // Waagerecht: Zeilen = Staffeln, Spalten = Folgen (+ Ø-Spalte)
    val cols = if (horizontal) maxEp else seasons.size
    val rows = if (horizontal) seasons.size else maxEp
    val width = labelW + (cellW + gap) * (cols + if (horizontal) 1 else 0) + if (horizontal) avgGap else 0.dp
    val height = headH + (cellH + gap) * (rows + if (horizontal) 0 else 1) + if (horizontal) 0.dp else avgGap

    /** Folge an Zeile/Spalte (beides 0-basiert) */
    fun epAt(row: Int, col: Int): Episode? =
        if (horizontal) seasons.getOrNull(row)?.let { byKey[it to col + 1] }
        else seasons.getOrNull(col)?.let { byKey[it to row + 1] }

    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Canvas(
            Modifier
                .size(width, height)
                .pointerInput(episodes, horizontal) {
                    detectTapGestures { p ->
                        val cw = (cellW + gap).toPx(); val ch = (cellH + gap).toPx()
                        val col = floor((p.x - labelW.toPx()) / cw).toInt()
                        val row = floor((p.y - headH.toPx()) / ch).toInt()
                        if (col in 0 until cols && row in 0 until rows) epAt(row, col)?.let(onSelect)
                    }
                }
        ) {
            val cw = cellW.toPx(); val ch = cellH.toPx(); val g = gap.toPx()
            val lw = labelW.toPx(); val hh = headH.toPx()
            val headStyle = TextStyle(color = Cine.TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            val avgHead = headStyle.copy(color = Cine.Gold, fontSize = 14.sp)
            val cellStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val r = CornerRadius(6.dp.toPx())

            fun cell(x: Float, y: Float, rating: Double?, highlight: Boolean, avg: Boolean) {
                drawRoundRect(Cine.rating(rating), Offset(x, y), Size(cw, ch), r)
                if (avg) drawRoundRect(Cine.Gold, Offset(x, y), Size(cw, ch), r, style = Stroke(1.5f * density))
                if (highlight) drawRoundRect(Color.White, Offset(x, y), Size(cw, ch), r, style = Stroke(2.5f * density))
                centeredText(tm, formatRating(rating), Offset(x + cw / 2, y + ch / 2), cellStyle.copy(color = Cine.onRating(rating)))
            }

            // Kopfzeile und Zeilenbeschriftung
            for (c in 0 until cols) {
                val label = if (horizontal) "E${c + 1}" else "S${seasons[c]}"
                centeredText(tm, label, Offset(lw + c * (cw + g) + cw / 2, hh / 2), headStyle)
            }
            for (rr in 0 until rows) {
                val label = if (horizontal) "S${seasons[rr]}" else "E${rr + 1}"
                centeredText(tm, label, Offset(lw / 2, hh + rr * (ch + g) + ch / 2), headStyle)
            }
            // Zellen
            for (rr in 0 until rows) for (c in 0 until cols) {
                val ep = epAt(rr, c) ?: continue
                cell(lw + c * (cw + g), hh + rr * (ch + g), ep.rating, ep == selected, avg = false)
            }
            // Durchschnitt
            if (horizontal) {
                val x = lw + cols * (cw + g) + avgGap.toPx()
                centeredText(tm, "Ø", Offset(x + cw / 2, hh / 2), avgHead)
                seasons.forEachIndexed { rr, s -> cell(x, hh + rr * (ch + g), avgs[s], false, avg = true) }
            } else {
                val y = hh + rows * (ch + g) + avgGap.toPx()
                centeredText(tm, "Ø", Offset(lw / 2, y + ch / 2), avgHead)
                seasons.forEachIndexed { c, s -> cell(lw + c * (cw + g), y, avgs[s], false, avg = true) }
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
