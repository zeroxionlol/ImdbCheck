package app.imdbcheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.imdbcheck.Repo
import app.imdbcheck.Screen
import app.imdbcheck.Seed
import app.imdbcheck.Suggestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bleibt erhalten, wenn man in die Details und zurück wechselt. */
class SearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Suggestion>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var autoFocused = false
}

@Composable
fun SearchScreen(state: SearchState, onOpen: (Screen) -> Unit) {
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.query) {
        val q = state.query.trim()
        if (q.length < 3) {
            state.results = emptyList(); state.loading = false; state.error = null
            return@LaunchedEffect
        }
        delay(280) // kurz warten, bis weitergetippt wurde
        state.loading = true
        try {
            state.results = Repo.suggest(q)
            state.error = null
        } catch (e: Exception) {
            state.error = "Suche fehlgeschlagen: ${e.message ?: "Netzwerkfehler"}"
        } finally {
            state.loading = false
        }
    }
    LaunchedEffect(Unit) {
        if (!state.autoFocused && state.query.isEmpty()) {
            state.autoFocused = true
            runCatching { focus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Film oder Serie suchen",
            style = MaterialTheme.typography.titleLarge,
            color = Cine.Text,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = { state.query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focus),
            singleLine = true,
            placeholder = { Text("z. B. Breaking Bad, Dune …") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Cine.Gold) },
            trailingIcon = {
                if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else if (state.query.isNotEmpty()) IconButton(onClick = { state.query = "" }) {
                    Icon(Icons.Filled.Clear, "Leeren")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Cine.PanelHigh,
                unfocusedContainerColor = Cine.Panel,
                focusedBorderColor = Cine.Gold,
                unfocusedBorderColor = Cine.Line,
                cursorColor = Cine.Gold,
                focusedTextColor = Cine.Text,
                unfocusedTextColor = Cine.Text,
                focusedPlaceholderColor = Cine.TextDim,
                unfocusedPlaceholderColor = Cine.TextDim,
            ),
        )
        Spacer(Modifier.height(8.dp))

        val q = state.query.trim()
        when {
            q.length < 3 -> Hint(
                if (q.isEmpty()) "Ab 3 Buchstaben erscheinen Vorschläge. Tippe auf einen Titel für Wertung und Episoden-Grafik."
                else "Noch ${3 - q.length} Buchstabe${if (3 - q.length == 1) "" else "n"} …"
            )
            state.error != null -> Hint(state.error!!, error = true)
            !state.loading && state.results.isEmpty() -> Hint("Keine Treffer für „$q“.")
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.results, key = { it.id }) { s ->
                val inList = Repo.items.any { it.id == s.id }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Cine.Panel.copy(alpha = 0.9f))
                        .clickable {
                            keyboard?.hide()
                            onOpen(Screen.Detail(s.id, Seed(s.title, s.poster, s.kind, s.yearText)))
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Poster(s.poster, 50.dp, title = s.title)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            s.title, style = MaterialTheme.typography.titleSmall, color = Cine.Text,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindChip(s.kind)
                            s.yearText?.let {
                                Spacer(Modifier.width(8.dp))
                                Text(it, color = Cine.TextDim, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        s.cast?.let {
                            Text(
                                it, color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (inList) Repo.remove(s.id) else Repo.appScope.launch { Repo.addSuggestion(s) }
                        }
                    ) {
                        if (inList) Icon(Icons.Filled.Check, "In der Liste", tint = Cine.Gold)
                        else Icon(Icons.Filled.Add, "Zur Liste", tint = Cine.Text)
                    }
                }
            }
        }
    }
}

@Composable
fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        color = if (error) Cine.Red else Cine.TextDim,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}
