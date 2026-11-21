package app.imdbcheck.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.imdbcheck.ListItem
import app.imdbcheck.Repo
import app.imdbcheck.Screen
import app.imdbcheck.Seed
import app.imdbcheck.Settings
import app.imdbcheck.ShowStatus
import app.imdbcheck.formatRating
import app.imdbcheck.formatVotes
import kotlinx.coroutines.launch

private val SORTS = listOf(
    "rating" to "Wertung",
    "title" to "Titel",
    "added" to "Zuletzt hinzugefügt",
    "year" to "Jahr",
    "next" to "Nächste Folge",
)
private val TYPES = listOf("Alle", "Filme", "Serien")
private val WATCHED = listOf("Alle", "Gesehen", "Nicht gesehen")

/** Filter der Liste – bleiben beim Tab-Wechsel erhalten. */
object ListUi {
    var category by mutableStateOf<String?>(null)
    var type by mutableStateOf(0)       // 0 alle, 1 Filme, 2 Serien
    var watched by mutableStateOf(0)    // 0 alle, 1 gesehen, 2 nicht gesehen
}

/** Laufende Aktualisierung (überlebt Tab-Wechsel). */
private object RefreshState {
    var running by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var message by mutableStateOf<String?>(null)
}

@Composable
fun ListScreen(onOpen: (Screen) -> Unit, onSearch: () -> Unit) {
    var sort by remember { mutableStateOf(Settings.sort) }
    var sortMenu by remember { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var watchedMenu by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<String?>(null) }
    var newCategory by remember { mutableStateOf(false) }
    var editCategory by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (RefreshState.running || Repo.items.isEmpty()) return
        RefreshState.running = true
        RefreshState.message = null
        Repo.appScope.launch {
            val errors = Repo.refreshAll { done, total -> RefreshState.progress = if (total == 0) 1f else done / total.toFloat() }
            RefreshState.running = false
            RefreshState.message =
                if (errors == 0) "Wertungen und Sendetermine aktualisiert." else "$errors Titel konnten nicht aktualisiert werden."
        }
    }

    // Automatisch aktualisieren, wenn die letzte Aktualisierung älter als 12 Stunden ist
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - Settings.lastRefresh > 12 * 3600_000L) refresh()
    }

    val all = Repo.items
    val cat = ListUi.category?.takeIf { it in Repo.categories }
    val type = ListUi.type
    val watched = ListUi.watched
    val shown = remember(all, cat, type, watched, sort) {
        val f = all.filter { i ->
            (cat == null || cat in i.categories) &&
                (when (type) { 1 -> !i.isSeries; 2 -> i.isSeries; else -> true }) &&
                (when (watched) { 1 -> i.watched; 2 -> !i.watched; else -> true })
        }
        when (sort) {
            "title" -> f.sortedBy { it.title.lowercase() }
            "added" -> f.sortedByDescending { it.addedAt }
            "year" -> f.sortedByDescending { it.year ?: 0 }
            "next" -> f.sortedWith(
                compareBy<ListItem> { it.upcoming?.airMillis ?: Long.MAX_VALUE }.thenByDescending { it.rating ?: -1.0 }
            )
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
                Icon(Icons.Filled.Refresh, "Aktualisieren", tint = if (RefreshState.running) Cine.TextDim else Cine.Gold)
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
            Text(
                it, color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        // ---------- Kategorien ----------
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryChip("Alle (${all.size})", selected = cat == null, onClick = { ListUi.category = null })
            Repo.categories.forEach { c ->
                val n = all.count { c in it.categories }
                CategoryChip(
                    "$c ($n)", selected = cat == c,
                    onClick = { ListUi.category = if (cat == c) null else c },
                    onLongClick = { editCategory = c },
                )
            }
            CategoryChip("+ Kategorie", selected = false, dashed = true, onClick = { newCategory = true })
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
            // ---------- Filter + Sortierung ----------
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MenuButton(
                    if (type == 0) "Typ" else TYPES[type], active = type != 0,
                    expanded = typeMenu, onExpand = { typeMenu = it },
                ) {
                    TYPES.forEachIndexed { i, l ->
                        DropdownMenuItem(text = { Text(l) }, onClick = { ListUi.type = i; typeMenu = false })
                    }
                }
                MenuButton(
                    if (watched == 0) "Gesehen?" else WATCHED[watched], active = watched != 0,
                    expanded = watchedMenu, onExpand = { watchedMenu = it },
                ) {
                    WATCHED.forEachIndexed { i, l ->
                        DropdownMenuItem(text = { Text(l) }, onClick = { ListUi.watched = i; watchedMenu = false })
                    }
                }
                MenuButton(
                    "Sortierung: " + SORTS.first { it.first == sort }.second, active = false,
                    expanded = sortMenu, onExpand = { sortMenu = it },
                ) {
                    SORTS.forEach { (k, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { sort = k; Settings.sort = k; sortMenu = false })
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
                if (shown.isEmpty()) item { Hint("Keine Titel für diese Filter.") }
                items(shown, key = { it.id }) { item ->
                    ListRow(
                        item,
                        onClick = { onOpen(Screen.Detail(item.id, Seed(item.title, item.poster, item.kind, item.yearText))) },
                        onLongClick = { actionsFor = item.id },
                    )
                }
                item {
                    Text(
                        "Lange tippen für Kategorien, Gesehen und Entfernen",
                        color = Cine.TextDim.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    actionsFor?.let { id ->
        val item = Repo.items.firstOrNull { it.id == id }
        if (item != null) ItemActionsDialog(item, onDismiss = { actionsFor = null })
    }
    if (newCategory) {
        NameDialog("Neue Kategorie", confirm = "Anlegen", onDismiss = { newCategory = false }) { name ->
            Repo.addCategory(name)?.let { ListUi.category = it }
            newCategory = false
        }
    }
    editCategory?.let { c -> CategoryEditDialog(c, onDismiss = { editCategory = null }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(item: ListItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Cine.Panel.copy(alpha = 0.9f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                    Icon(Icons.Filled.CheckCircle, "Gesehen", tint = Cine.Gold, modifier = Modifier.size(16.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindChip(item.kind)
                item.yearText?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = Cine.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                Text("${formatVotes(item.votes)} Stimmen", color = Cine.TextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            val next = item.upcoming
            if (item.isSeries && next != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Cine.Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "${next.code} am ${next.shortDate()} (${next.relativeText()})",
                        color = Cine.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                }
            } else if (item.isSeries && item.status != null && item.status != "Running") {
                Text(ShowStatus(item.status, null).statusText ?: "", color = Cine.TextDim, fontSize = 12.sp)
            }
            if (item.categories.isNotEmpty()) {
                Text(
                    item.categories.joinToString(" · "),
                    color = Color(0xFF9EC9FF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        RatingTile(item.rating)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    dashed: Boolean = false,
) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        label,
        color = if (selected) Cine.Night else if (dashed) Cine.Gold else Cine.Text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Cine.Gold else Cine.PanelHigh)
            .border(1.dp, if (selected) Cine.Gold else if (dashed) Cine.Gold.copy(alpha = 0.5f) else Cine.Line, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun MenuButton(
    label: String,
    active: Boolean,
    expanded: Boolean,
    onExpand: (Boolean) -> Unit,
    menuItems: @Composable () -> Unit,
) {
    Box {
        TextButton(onClick = { onExpand(true) }) {
            Text(label, color = if (active) Cine.Gold else Cine.Text, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            Icon(Icons.Filled.ArrowDropDown, null, tint = if (active) Cine.Gold else Cine.Text)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpand(false) }) { menuItems() }
    }
}

/** Kategorien, Gesehen und Entfernen für einen Eintrag. */
@Composable
fun ItemActionsDialog(item: ListItem, onDismiss: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CategoryPicker(item.categories) { c -> Repo.toggleCategory(item.id, c) }
                CategoryChip(
                    if (item.watched) "✓ Gesehen" else "Als gesehen markieren",
                    selected = item.watched,
                    onClick = { Repo.setWatched(item.id, !item.watched) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fertig") } },
        dismissButton = {
            TextButton(onClick = {
                if (confirmDelete) {
                    Repo.remove(item.id); onDismiss()
                } else {
                    confirmDelete = true
                }
            }) { Text(if (confirmDelete) "Wirklich entfernen" else "Entfernen", color = Cine.Red) }
        },
        containerColor = Cine.Panel,
    )
}

/** Kategorien an-/abwählen, inkl. „+ Neu“. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(selected: List<String>, onToggle: (String) -> Unit) {
    var create by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Kategorien", color = Cine.TextDim, style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Repo.categories.forEach { c ->
                CategoryChip(c, selected = c in selected, onClick = { onToggle(c) })
            }
            CategoryChip("+ Neu", selected = false, dashed = true, onClick = { create = true })
        }
    }
    if (create) {
        NameDialog("Neue Kategorie", confirm = "Anlegen", onDismiss = { create = false }) { name ->
            Repo.addCategory(name)?.let { c -> if (c !in selected) onToggle(c) }
            create = false
        }
    }
}

@Composable
private fun CategoryEditDialog(name: String, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(name) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kategorie bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NameField(text) { text = it }
                if (confirmDelete) {
                    Text(
                        "Die Kategorie wird gelöscht. Die Titel bleiben in deiner Liste.",
                        color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    Repo.renameCategory(name, text)
                    if (ListUi.category == name) {
                        ListUi.category = Repo.categories.firstOrNull { it.equals(text.trim(), ignoreCase = true) }
                    }
                    onDismiss()
                },
                enabled = text.isNotBlank(),
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (confirmDelete) {
                    Repo.deleteCategory(name); onDismiss()
                } else {
                    confirmDelete = true
                }
            }) { Text(if (confirmDelete) "Wirklich löschen" else "Löschen", color = Cine.Red) }
        },
        containerColor = Cine.Panel,
    )
}

@Composable
fun NameDialog(title: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NameField(text) { text = it } },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
        containerColor = Cine.Panel,
    )
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("z. B. Anime") },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cine.Gold, unfocusedBorderColor = Cine.Line,
            focusedTextColor = Cine.Text, unfocusedTextColor = Cine.Text, cursorColor = Cine.Gold,
            focusedPlaceholderColor = Cine.TextDim, unfocusedPlaceholderColor = Cine.TextDim,
        ),
    )
}
