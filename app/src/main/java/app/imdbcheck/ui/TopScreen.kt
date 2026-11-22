package app.imdbcheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.imdbcheck.Repo
import app.imdbcheck.Screen
import app.imdbcheck.Seed
import app.imdbcheck.formatVotes
import kotlinx.coroutines.launch

/** Ladezustand (überlebt Tab-Wechsel). */
private object TopState {
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

private fun loadTop(force: Boolean) {
    if (TopState.loading) return
    TopState.loading = true
    TopState.error = null
    Repo.appScope.launch {
        try {
            Repo.loadTop(force)
        } catch (e: Exception) {
            TopState.error = "Top-Liste konnte nicht geladen werden: ${e.message ?: "Netzwerkfehler"}"
        } finally {
            TopState.loading = false
        }
    }
}

@Composable
fun TopScreen(onOpen: (Screen) -> Unit) {
    LaunchedEffect(Unit) { loadTop(force = false) }
    val top = Repo.top

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Top 50 Serien", style = MaterialTheme.typography.titleLarge, color = Cine.Text)
                Text(
                    "Die bestbewerteten Serien auf IMDb",
                    color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (TopState.loading) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Cine.Gold)
                }
            } else {
                IconButton(onClick = { loadTop(force = true) }) {
                    Icon(Icons.Filled.Refresh, "Neu laden", tint = Cine.Gold)
                }
            }
        }
        TopState.error?.let { Hint(it, error = true) }
        if (top.isEmpty() && TopState.loading) Hint("Wird geladen …")

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(top, key = { _, t -> t.id }) { i, t ->
                val inList = Repo.items.any { it.id == t.id }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Cine.Panel.copy(alpha = 0.9f))
                        .clickable { onOpen(Screen.Detail(t.id, Seed(t.title, t.poster, t.kind, t.yearText))) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${i + 1}",
                        color = if (i < 3) Cine.Gold else Cine.TextDim,
                        fontSize = if (i < 3) 20.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(34.dp),
                    )
                    Poster(t.poster, 46.dp, title = t.title)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            t.title, style = MaterialTheme.typography.titleSmall, color = Cine.Text,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindChip(t.kind)
                            t.yearText?.let {
                                Spacer(Modifier.width(8.dp))
                                Text(it, color = Cine.TextDim, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text("${formatVotes(t.votes)} Stimmen", color = Cine.TextDim, style = MaterialTheme.typography.bodySmall)
                    }
                    RatingTile(t.rating, size = 42.dp)
                    IconButton(onClick = {
                        if (inList) {
                            Repo.remove(t.id)
                        } else {
                            Repo.addInfo(t, listOfNotNull(ListUi.category?.takeIf { it in Repo.categories }))
                            Repo.appScope.launch { runCatching { Repo.showStatus(t.id, force = true) } }
                        }
                    }) {
                        if (inList) Icon(Icons.Filled.Check, "In der Liste", tint = Cine.Gold)
                        else Icon(Icons.Filled.Add, "Zur Liste", tint = Cine.Text)
                    }
                }
            }
        }
    }
}
