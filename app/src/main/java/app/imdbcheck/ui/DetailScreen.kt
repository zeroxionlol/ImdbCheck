package app.imdbcheck.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.imdbcheck.Episode
import app.imdbcheck.Repo
import app.imdbcheck.Screen
import app.imdbcheck.Seed
import app.imdbcheck.TitleInfo
import app.imdbcheck.formatRating
import app.imdbcheck.formatVotes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(id: String, seed: Seed?, onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf<TitleInfo?>(null) }
    var infoError by remember { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>?>(null) }
    var epLoading by remember { mutableStateOf(false) }
    var epCount by remember { mutableIntStateOf(0) }
    var epError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Episode?>(null) }
    var view by rememberSaveable { mutableIntStateOf(0) } // 0 Heatmap, 1 Verlauf
    var reload by remember { mutableIntStateOf(0) }
    var show by remember { mutableStateOf<app.imdbcheck.ShowStatus?>(null) }
    var horizontal by remember { mutableStateOf(app.imdbcheck.Settings.heatmapHorizontal) }

    val listItem = Repo.items.firstOrNull { it.id == id }
    val inList = listItem != null
    val kind = info?.kind ?: seed?.kind ?: listItem?.kind ?: "movie"
    val isSeries = app.imdbcheck.Kinds.isSeries(kind)

    LaunchedEffect(id, reload) {
        infoError = null
        try {
            info = Repo.title(id, force = reload > 0)
        } catch (e: Exception) {
            infoError = "IMDb-Daten konnten nicht geladen werden: ${e.message ?: "Netzwerkfehler"}"
        }
    }
    LaunchedEffect(id, isSeries, reload) {
        if (!isSeries) return@LaunchedEffect
        show = runCatching { Repo.showStatus(id, force = reload > 0) }.getOrNull()
    }
    LaunchedEffect(id, isSeries, reload) {
        if (!isSeries) return@LaunchedEffect
        epLoading = true; epError = null; epCount = 0
        try {
            episodes = Repo.episodes(id, force = reload > 0) { n -> epCount = n }
        } catch (e: Exception) {
            epError = "Episoden konnten nicht geladen werden: ${e.message ?: "Netzwerkfehler"}"
        } finally {
            epLoading = false
        }
    }

    val title = info?.title ?: seed?.title ?: listItem?.title ?: id

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "Neu laden") }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "$title – IMDb ${formatRating(info?.rating)}\nhttps://www.imdb.com/title/$id/")
                        }
                        ctx.startActivity(Intent.createChooser(send, "Teilen"))
                    }) { Icon(Icons.Filled.Share, "Teilen") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Cine.Deep.copy(alpha = 0.95f),
                    titleContentColor = Cine.Text,
                    navigationIconContentColor = Cine.Text,
                    actionIconContentColor = Cine.Text,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- Kopf ----------
            Row {
                Poster(info?.poster ?: seed?.poster ?: listItem?.poster, 112.dp, title = title)
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Cine.Text)
                    info?.originalTitle?.let { Text(it, color = Cine.TextDim, style = MaterialTheme.typography.bodySmall) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KindChip(kind)
                        val y = info?.yearText ?: seed?.yearText
                        val rt = info?.runtimeMin?.takeIf { it > 0 }?.let { " · ${it} Min." } ?: ""
                        if (y != null || rt.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text((y ?: "") + rt, color = Cine.TextDim, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (info == null && infoError == null) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = Cine.Gold)
                    } else {
                        StarRating(info?.rating ?: listItem?.rating, big = true)
                        Text(
                            "${formatVotes(info?.votes ?: listItem?.votes)} Stimmen auf IMDb",
                            color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            infoError?.let { Text(it, color = Cine.Red, style = MaterialTheme.typography.bodyMedium) }

            // ---------- Nächste Folge ----------
            if (isSeries) {
                val st = show
                val next = st?.next?.takeIf { it.airMillis > System.currentTimeMillis() - 24 * 3600_000L }
                    ?: listItem?.upcoming
                val stText = st?.statusText ?: listItem?.status?.let { app.imdbcheck.ShowStatus(it, null).statusText }
                if (next != null) {
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Cine.Gold, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Nächste Folge · ${next.relativeText()}", color = Cine.Gold, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    next.code + (next.title?.let { " – $it" } ?: ""),
                                    color = Cine.Text, style = MaterialTheme.typography.titleSmall,
                                )
                                Text(next.whenText(), color = Cine.TextDim, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else if (stText != null) {
                    Text(
                        when (st?.status ?: listItem?.status) {
                            "Ended" -> "Serie ist abgeschlossen."
                            "Running" -> "Läuft noch – nächster Sendetermin ist noch nicht bekannt."
                            else -> "Status: $stText"
                        },
                        color = Cine.TextDim, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // ---------- Liste ----------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (inList) {
                    OutlinedButton(onClick = { Repo.remove(id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Check, null, tint = Cine.Gold); Spacer(Modifier.width(6.dp))
                        Text("In meiner Liste", color = Cine.Text)
                    }
                    FilterChip(
                        selected = listItem?.watched == true,
                        onClick = { Repo.setWatched(id, listItem?.watched != true) },
                        label = { Text("Gesehen") },
                        leadingIcon = if (listItem?.watched == true) {
                            { Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Cine.Gold, selectedLabelColor = Cine.Night,
                            selectedLeadingIconColor = Cine.Night, labelColor = Cine.Text,
                        ),
                    )
                } else {
                    Button(
                        onClick = {
                            val i = info
                            val cats = listOfNotNull(ListUi.category?.takeIf { it in Repo.categories })
                            if (i != null) {
                                Repo.addInfo(i, cats)
                            } else if (seed != null) {
                                Repo.appScope.launch {
                                    Repo.addSuggestion(app.imdbcheck.Suggestion(id, seed.title, null, seed.yearText, seed.kind, null, seed.poster), cats)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Cine.Gold, contentColor = Cine.Night),
                    ) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Zu meiner Liste hinzufügen")
                    }
                }
            }

            // ---------- Kategorien ----------
            if (inList) {
                CategoryPicker(listItem?.categories ?: emptyList()) { c -> Repo.toggleCategory(id, c) }
            }

            // ---------- Infos ----------
            info?.let { i ->
                if (i.genres.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        i.genres.forEach { g ->
                            Text(
                                g, color = Cine.Text, fontSize = 12.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Cine.PanelHigh)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                i.plot?.let { Text(it, color = Cine.Text.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium) }
            }

            Text(
                "Auf IMDb öffnen",
                color = Cine.Gold,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.imdb.com/title/$id/")))
                }.padding(vertical = 2.dp),
            )

            // ---------- Episoden (wie seriesgraph) ----------
            if (isSeries) {
                val eps = episodes
                when {
                    epLoading && eps == null -> Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Cine.Gold)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (epCount > 0) "Episoden werden geladen … ($epCount)" else "Episoden werden geladen …",
                                color = Cine.TextDim,
                            )
                        }
                    }
                    epError != null && eps == null -> Text(epError!!, color = Cine.Red, style = MaterialTheme.typography.bodyMedium)
                    eps != null && eps.isEmpty() -> Text("Keine Episoden gefunden.", color = Cine.TextDim)
                    eps != null -> EpisodeSection(
                        eps, selected, view, horizontal,
                        onView = { view = it },
                        onHorizontal = { horizontal = it; app.imdbcheck.Settings.heatmapHorizontal = it },
                        onSelect = { selected = it },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodeSection(
    eps: List<Episode>,
    selected: Episode?,
    view: Int,
    horizontal: Boolean,
    onView: (Int) -> Unit,
    onHorizontal: (Boolean) -> Unit,
    onSelect: (Episode) -> Unit,
) {
    val rated = remember(eps) { eps.filter { it.rating != null } }
    val seasons = remember(eps) { eps.groupBy { it.season }.toSortedMap() }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Episoden-Wertungen", Modifier.weight(1f))
            Text("${seasons.size} Staffeln · ${eps.size} Folgen", color = Cine.TextDim, style = MaterialTheme.typography.bodySmall)
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Heatmap", "Verlauf").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = view == i,
                    onClick = { onView(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Cine.Gold, activeContentColor = Cine.Night,
                        inactiveContainerColor = Cine.PanelHigh, inactiveContentColor = Cine.Text,
                    ),
                ) { Text(label) }
            }
        }
        if (view == 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ausrichtung", color = Cine.TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                CategoryChip("Senkrecht", selected = !horizontal, onClick = { onHorizontal(false) })
                Spacer(Modifier.width(6.dp))
                CategoryChip("Waagerecht", selected = horizontal, onClick = { onHorizontal(true) })
            }
            Heatmap(eps, selected, horizontal, onSelect)
        } else {
            EpisodeGraph(eps, selected, onSelect)
        }

        if (view == 0) Legend()
        Text("Tippe auf eine Folge für Details.", color = Cine.TextDim, style = MaterialTheme.typography.bodySmall)
    }

    selected?.let { e -> EpisodeCard(e) }

    // Durchschnitt je Staffel
    Panel {
        SectionTitle("Staffeln im Schnitt")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            seasons.forEach { (s, list) ->
                val avg = seasonAverage(list)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RatingTile(avg, size = 48.dp)
                    Text("S$s", color = Cine.TextDim, fontSize = 12.sp)
                }
            }
        }
        val best = seasons.mapNotNull { (s, l) -> seasonAverage(l)?.let { s to it } }.maxByOrNull { it.second }
        best?.let { Text("Beste Staffel: ${it.first} (Ø ${formatRating(it.second)})", color = Cine.Text, style = MaterialTheme.typography.bodyMedium) }
    }

    if (rated.size >= 3) {
        val top = rated.sortedWith(compareByDescending<Episode> { it.rating }.thenByDescending { it.votes ?: 0 }).take(5)
        val flop = rated.sortedWith(compareBy<Episode> { it.rating }.thenByDescending { it.votes ?: 0 }).take(5)
        EpisodeList("Top 5 Folgen", top, onSelect)
        EpisodeList("Schwächste Folgen", flop, onSelect)
    }
}

@Composable
private fun Legend() {
    val steps = listOf(9.5 to "≥ 9", 8.5 to "8–9", 7.5 to "7–8", 6.5 to "6–7", 5.5 to "5–6", 4.0 to "< 5")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        steps.forEach { (r, label) ->
            Text(
                label,
                color = Cine.onRating(r),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Cine.rating(r))
                    .padding(vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun EpisodeCard(e: Episode) {
    val ctx = LocalContext.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RatingTile(e.rating, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(e.code, color = Cine.Gold, style = MaterialTheme.typography.labelLarge)
                Text(e.title, color = Cine.Text, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(e.date, e.votes?.let { "${formatVotes(it)} Stimmen" }).joinToString(" · "),
                    color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (e.id.startsWith("tt")) {
            Text(
                "Folge auf IMDb öffnen",
                color = Cine.Gold, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.imdb.com/title/${e.id}/")))
                },
            )
        }
    }
}

@Composable
private fun EpisodeList(title: String, eps: List<Episode>, onSelect: (Episode) -> Unit) {
    Panel {
        SectionTitle(title)
        eps.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onSelect(e) }.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RatingTile(e.rating, size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Text(e.code, color = Cine.Gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(62.dp))
                Text(e.title, color = Cine.Text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
