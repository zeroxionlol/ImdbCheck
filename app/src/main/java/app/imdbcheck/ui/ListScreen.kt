package app.imdbcheck.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.imdbcheck.ListItem
import app.imdbcheck.Repo
import app.imdbcheck.Screen
import app.imdbcheck.Seed
import app.imdbcheck.Settings
import app.imdbcheck.formatRating
import app.imdbcheck.formatVotes
import kotlinx.coroutines.launch

private val SORTS = listOf(
    "rating" to "Wertung",
    "title" to "Titel",
    "added" to "Zuletzt hinzugefügt",
    "year" to "Jahr",
)

/** Laufende Aktualisierung (überlebt Tab-Wechsel). */
private object RefreshState {
    var running by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var message by mutableStateOf<String?>(null)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListScreen(onOpen: (Screen) -> Unit, onSearch: () -> Unit) {
    var filter by remember { mutableStateOf(0) } // 0 alle, 1 Filme, 2 Serien
    var sort by remember { mutableStateOf(Settings.sort) }
    var sortMenu by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ListItem?>(null) }

    fun refresh() {
        if (RefreshState.running || Repo.items.isEmpty()) return
        RefreshState.running = true
        RefreshState.message = null
        Repo.appScope.launch {
            val errors = Repo.refreshAll { done, total -> RefreshState.progress = if (total == 0) 1f else done / total.toFloat() }
            RefreshState.running = false
            RefreshState.message = if (errors == 0) "Wertungen aktualisiert." else "$errors Titel konnten nicht aktualisiert werden."
        }
    }

    // Automatisch aktualisieren, wenn die letzte Aktualisierung älter als 12 Stunden ist
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - Settings.lastRefresh > 12 * 3600_000L) refresh()
    }

    val all = Repo.items
    val shown = remember(all, filter, sort) {
        val f = when (filter) {
            1 -> all.filter { !it.isSeries }
            2 -> all.filter { it.isSeries }
            else -> all
        }
        when (sort) {
            "title" -> f.sortedBy { it.title.lowercase() }
            "added" -> f.sortedByDescending { it.addedAt }
            "year" -> f.sortedByDescending { it.year ?: 0 }
            else -> f.sortedWith(compareByDescending<ListItem> { it.rating ?: -1.0 }.thenByDescending { it.votes ?: 0 })
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Meine Liste", style = MaterialTheme.typography.titleLarge, color = Cine.Text, modifier = Modifier.weight(1f))
            IconButton(onClick = { refresh() }, enabled = !RefreshState.running) {
                Icon(Icons.Filled.Refresh, "Wertungen aktualisieren", tint = if (RefreshState.running) Cine.TextDim else Cine.Gold)
            }
            IconButton(onClick = { onOpen(Screen.Settings) }) {
                Icon(Icons.Filled.Settings, "Einstellungen", tint = Cine.TextDim)
            }
        }
        if (RefreshState.running) {
            LinearProgressIndicator(
                progress = { RefreshState.progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = Cine.Gold,
                trackColor = Cine.PanelHigh,
            )
        }
        RefreshState.message?.let {
            Text(it, color = Cine.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }

        if (all.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Deine Liste ist noch leer.", style = MaterialTheme.typography.titleMedium, color = Cine.Text)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Suche einen Film oder eine Serie und tippe auf +, um ihn mit IMDb-Wertung zu speichern.",
                    color = Cine.TextDim, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSearch) {
                    Icon(Icons.Filled.Search, null); Spacer(Modifier.width(6.dp)); Text("Zur Suche")
                }
            }
        } else {

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("Alle", "Filme", "Serien").forEachIndexed { i, label ->
                FilterChip(
                    selected = filter == i, onClick = { filter = i }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cine.Gold, selectedLabelColor = Cine.Night,
                        labelColor = Cine.Text,
                    ),
                )
            }
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Text(SORTS.first { it.first == sort }.second, color = Cine.Text)
                    Icon(Icons.Filled.ArrowDropDown, null, tint = Cine.Text)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SORTS.forEach { (k, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            sort = k; Settings.sort = k; sortMenu = false
                        })
                    }
                }
            }
        }

        val rated = shown.mapNotNull { it.rating }
        Text(
            "${shown.size} Titel" + if (rated.isNotEmpty()) " · Ø ${formatRating(rated.average())}" else "",
            color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(shown, key = { it.id }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Cine.Panel.copy(alpha = 0.9f))
                        .combinedClickable(
                            onClick = { onOpen(Screen.Detail(item.id, Seed(item.title, item.poster, item.kind, item.yearText))) },
                            onLongClick = { toDelete = item },
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Poster(item.poster, 52.dp, title = item.title)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.title, style = MaterialTheme.typography.titleSmall, color = Cine.Text,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                            )
                            if (item.watched) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.CheckCircle, "Gesehen", tint = Cine.Gold, modifier = Modifier.height(16.dp))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindChip(item.kind)
                            item.yearText?.let {
                                Spacer(Modifier.width(8.dp))
                                Text(it, color = Cine.TextDim, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            "${formatVotes(item.votes)} Stimmen",
                            color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    RatingTile(item.rating)
                }
            }
            item {
                Text(
                    "Lange tippen zum Entfernen",
                    color = Cine.TextDim.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        } // else
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Entfernen?") },
            text = { Text("„${item.title}“ aus deiner Liste entfernen?") },
            confirmButton = { TextButton(onClick = { Repo.remove(item.id); toDelete = null }) { Text("Entfernen") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Abbrechen") } },
            containerColor = Cine.Panel,
        )
    }
}
