package app.imdbcheck.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.imdbcheck.BuildConfig
import app.imdbcheck.Repo
import app.imdbcheck.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(Settings.omdbKey) }
    var german by remember { mutableStateOf(Settings.germanTitles) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(Repo.exportJson().toByteArray()) }
        }.isSuccess
        Toast.makeText(ctx, if (ok) "Liste gesichert" else "Sichern fehlgeschlagen", Toast.LENGTH_SHORT).show()
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val msg = runCatching {
            val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
            "${Repo.importJson(text)} Titel importiert"
        }.getOrElse { "Import fehlgeschlagen: ${it.message}" }
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Einstellungen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Cine.Deep.copy(alpha = 0.95f),
                    titleContentColor = Cine.Text,
                    navigationIconContentColor = Cine.Text,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Panel {
                SectionTitle("Titel")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Deutsche Titel bevorzugen", color = Cine.Text)
                        Text(
                            "Zeigt auf der Detailseite den deutschen Verleihtitel, falls IMDb einen kennt.",
                            color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = german,
                        onCheckedChange = { german = it; Settings.germanTitles = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Cine.Gold, checkedThumbColor = Cine.Night),
                    )
                }
            }

            Panel {
                SectionTitle("Ersatzquelle OMDb (optional)")
                Text(
                    "Die App holt alles direkt von IMDb. Falls IMDb einmal nicht antwortet, kann sie die " +
                        "IMDb-Wertungen über OMDb laden. Dafür brauchst du einen kostenlosen Key von omdbapi.com.",
                    color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; Settings.omdbKey = it },
                    label = { Text("OMDb-API-Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cine.Gold, unfocusedBorderColor = Cine.Line,
                        focusedTextColor = Cine.Text, unfocusedTextColor = Cine.Text,
                        focusedLabelColor = Cine.Gold, unfocusedLabelColor = Cine.TextDim, cursorColor = Cine.Gold,
                    ),
                )
                Text(
                    "Key kostenlos anfordern",
                    color = Cine.Gold, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.omdbapi.com/apikey.aspx")))
                    },
                )
            }

            Panel {
                SectionTitle("Datensicherung")
                Text(
                    "Deine Liste liegt nur auf dem Handy. Sichere sie als Datei, z. B. vor einem Handywechsel.",
                    color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { export.launch("imdbcheck-liste.json") }, modifier = Modifier.weight(1f)) {
                        Text("Exportieren", color = Cine.Text)
                    }
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.weight(1f)) {
                        Text("Importieren", color = Cine.Text)
                    }
                }
            }

            Panel {
                SectionTitle("Info")
                val last = Settings.lastRefresh
                Text(
                    "Wertungen zuletzt aktualisiert: " +
                        if (last == 0L) "noch nie" else SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.GERMANY).format(Date(last)),
                    color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Die Liste aktualisiert sich automatisch, wenn die letzte Aktualisierung älter als 12 Stunden ist.",
                    color = Cine.TextDim, style = MaterialTheme.typography.bodySmall,
                )
                Text("Daten: IMDb · Version ${BuildConfig.VERSION_NAME}", color = Cine.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
