package app.imdbcheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.imdbcheck.Kinds
import app.imdbcheck.formatRating
import app.imdbcheck.posterUrl
import coil.compose.SubcomposeAsyncImage

@Composable
fun Poster(url: String?, width: Dp, modifier: Modifier = Modifier, title: String = "") {
    val h = width * 1.48f
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(width, h)
            .clip(shape)
            .background(Cine.PanelHigh),
        contentAlignment = Alignment.Center,
    ) {
        val fallback: @Composable () -> Unit = {
            Text(
                title.take(1).uppercase().ifEmpty { "?" },
                color = Cine.TextDim,
                fontSize = (width.value / 2.6f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
        val src = posterUrl(url, (width.value * 2.5f).toInt().coerceIn(120, 600))
        if (src == null) fallback() else SubcomposeAsyncImage(
            model = src,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width, h),
            loading = { fallback() },
            error = { fallback() },
        )
    }
}

/** Gelber Stern + Wertung. */
@Composable
fun StarRating(rating: Double?, big: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, tint = Cine.Gold, modifier = Modifier.size(if (big) 30.dp else 18.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            formatRating(rating),
            color = Cine.Text,
            fontWeight = FontWeight.Bold,
            fontSize = if (big) 28.sp else 16.sp,
        )
        if (big && rating != null) Text(" /10", color = Cine.TextDim, fontSize = 15.sp)
    }
}

/** Farbige Wertungs-Kachel (Farbskala wie Heatmap). */
@Composable
fun RatingTile(rating: Double?, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Cine.rating(rating)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            formatRating(rating),
            color = Cine.onRating(rating),
            fontWeight = FontWeight.Bold,
            fontSize = (size.value / 2.9f).sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun KindChip(kind: String, modifier: Modifier = Modifier) {
    val series = Kinds.isSeries(kind)
    val c = if (series) Color(0xFF9EC9FF) else Cine.Gold
    Text(
        Kinds.label(kind),
        color = c,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cine.Panel.copy(alpha = 0.92f))
            .border(1.dp, Cine.Line.copy(alpha = 0.6f), shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = Cine.Text, modifier = modifier)
}
