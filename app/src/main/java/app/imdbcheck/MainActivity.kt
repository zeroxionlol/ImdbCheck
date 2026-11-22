package app.imdbcheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.imdbcheck.ui.Cine
import app.imdbcheck.ui.CineTheme
import app.imdbcheck.ui.DetailScreen
import app.imdbcheck.ui.ListScreen
import app.imdbcheck.ui.SearchScreen
import app.imdbcheck.ui.SearchState
import app.imdbcheck.ui.SettingsScreen
import app.imdbcheck.ui.TopScreen

/** Vorab bekannte Daten, damit die Detailseite sofort etwas zeigt. */
data class Seed(val title: String, val poster: String?, val kind: String, val yearText: String?)

sealed interface Screen {
    data class Detail(val id: String, val seed: Seed?) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(applicationContext)
        setContent { CineTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(if (Repo.items.isEmpty()) 0 else 2) }
    val stack = remember { mutableStateListOf<Screen>() }
    val search = remember { SearchState() }
    val open: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.isNotEmpty()) { back() }

    val top = stack.lastOrNull()
    when (top) {
        is Screen.Detail -> DetailScreen(top.id, top.seed, onBack = back, onOpen = open)
        Screen.Settings -> SettingsScreen(onBack = back)
        null -> Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = Cine.Deep.copy(alpha = 0.97f)) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Cine.Night,
                        indicatorColor = Cine.Gold,
                        selectedTextColor = Cine.Gold,
                        unselectedIconColor = Cine.TextDim,
                        unselectedTextColor = Cine.TextDim,
                    )
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 },
                        icon = { Icon(Icons.Filled.Search, null) }, label = { Text("Suche") }, colors = colors,
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 },
                        icon = { Icon(Icons.Filled.Star, null) }, label = { Text("Top 50") }, colors = colors,
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                        label = { Text("Meine Liste (${Repo.items.size})") }, colors = colors,
                    )
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when (tab) {
                    0 -> SearchScreen(search, onOpen = open)
                    1 -> TopScreen(onOpen = open)
                    else -> ListScreen(onOpen = open, onSearch = { tab = 0 })
                }
            }
        }
    }
}
