package app.imdbcheck

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Settings {
    private lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var omdbKey: String
        get() = prefs.getString("omdbKey", "") ?: ""
        set(v) = prefs.edit().putString("omdbKey", v.trim()).apply()

    var germanTitles: Boolean
        get() = prefs.getBoolean("germanTitles", true)
        set(v) = prefs.edit().putBoolean("germanTitles", v).apply()

    var lastRefresh: Long
        get() = prefs.getLong("lastRefresh", 0L)
        set(v) = prefs.edit().putLong("lastRefresh", v).apply()

    var sort: String
        get() = prefs.getString("sort", "rating") ?: "rating"
        set(v) = prefs.edit().putString("sort", v).apply()
}

/** Hält „Meine Liste“ (als JSON-Datei auf dem Handy) und einen kleinen Speicher für Details. */
object Repo {
    /** Für Arbeiten, die einen Bildschirmwechsel überleben sollen (Aktualisieren, Hinzufügen). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var file: File
    var items by mutableStateOf<List<ListItem>>(emptyList())
        private set

    private val titleCache = java.util.concurrent.ConcurrentHashMap<String, TitleInfo>()
    private val episodeCache = java.util.concurrent.ConcurrentHashMap<String, List<Episode>>()

    fun init(ctx: Context) {
        Settings.init(ctx)
        file = File(ctx.filesDir, "list.json")
        items = runCatching { parse(file.readText()) }.getOrDefault(emptyList())
    }

    private fun parse(text: String): List<ListItem> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { runCatching { ListItem.fromJson(it) }.getOrNull() }
        }
    }

    fun exportJson(): String {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        return JSONObject().put("app", "ImdbCheck").put("version", 1).put("items", arr).toString(2)
    }

    /** Fügt importierte Einträge hinzu (vorhandene bleiben). Liefert die Anzahl neuer Einträge. */
    fun importJson(text: String): Int {
        val incoming = parse(text)
        val have = items.map { it.id }.toSet()
        val fresh = incoming.filter { it.id !in have }
        save(items + fresh)
        return fresh.size
    }

    private fun save(list: List<ListItem>) {
        items = list
        runCatching {
            val tmp = File(file.parentFile, "list.json.tmp")
            tmp.writeText(exportJson())
            tmp.renameTo(file)
        }
    }

    fun contains(id: String) = items.any { it.id == id }

    fun add(item: ListItem) {
        if (contains(item.id)) return
        save(items + item)
    }

    fun remove(id: String) = save(items.filterNot { it.id == id })

    fun setWatched(id: String, watched: Boolean) =
        save(items.map { if (it.id == id) it.copy(watched = watched) else it })

    private fun applyInfo(info: TitleInfo) {
        if (!contains(info.id)) return
        val now = System.currentTimeMillis()
        save(items.map {
            if (it.id != info.id) it else it.copy(
                title = info.title,
                yearText = info.yearText ?: it.yearText,
                year = info.year ?: it.year,
                kind = info.kind,
                poster = info.poster ?: it.poster,
                rating = info.rating ?: it.rating,
                votes = info.votes ?: it.votes,
                updatedAt = now,
            )
        })
    }

    // ---------- Netz ----------

    /** Titel-Details: erst IMDb, bei Fehler OMDb (falls Key eingetragen). */
    suspend fun title(id: String, force: Boolean = false, minimal: Boolean = false): TitleInfo =
        withContext(Dispatchers.IO) {
            if (!force) titleCache[id]?.let { return@withContext it }
            val info = try {
                Imdb.title(id, minimal)
            } catch (e: Exception) {
                if (Settings.omdbKey.isBlank()) throw e
                Omdb.title(id)
            }
            if (!minimal) titleCache[id] = info
            withContext(Dispatchers.Main) { applyInfo(info) }
            info
        }

    suspend fun episodes(id: String, force: Boolean = false, onProgress: (Int) -> Unit = {}): List<Episode> =
        withContext(Dispatchers.IO) {
            if (!force) episodeCache[id]?.let { return@withContext it }
            val eps = try {
                Imdb.episodes(id, onProgress)
            } catch (e: Exception) {
                if (Settings.omdbKey.isBlank()) throw e
                Omdb.episodes(id, onProgress)
            }
            val sorted = eps.distinctBy { it.season to it.number }.sortedWith(compareBy({ it.season }, { it.number }))
            episodeCache[id] = sorted
            sorted
        }

    suspend fun suggest(q: String): List<Suggestion> = withContext(Dispatchers.IO) { Imdb.suggest(q) }

    /** Fügt einen Vorschlag hinzu und holt direkt die Wertung nach. */
    suspend fun addSuggestion(s: Suggestion) {
        val now = System.currentTimeMillis()
        add(
            ListItem(
                id = s.id, title = s.title, yearText = s.yearText, year = s.year, kind = s.kind,
                poster = s.poster, rating = null, votes = null, addedAt = now, updatedAt = 0L,
            )
        )
        runCatching { title(s.id, force = true, minimal = true) }
    }

    fun addInfo(info: TitleInfo) {
        val now = System.currentTimeMillis()
        add(
            ListItem(
                id = info.id, title = info.title, yearText = info.yearText, year = info.year, kind = info.kind,
                poster = info.poster, rating = info.rating, votes = info.votes, addedAt = now, updatedAt = now,
            )
        )
    }

    /** Aktualisiert die Wertungen aller Einträge. Liefert die Anzahl der Fehler. */
    suspend fun refreshAll(onProgress: (done: Int, total: Int) -> Unit): Int {
        val ids = items.map { it.id }
        var errors = 0
        ids.forEachIndexed { i, id ->
            onProgress(i, ids.size)
            try { title(id, force = true, minimal = true) } catch (_: Exception) { errors++ }
            delay(150)
        }
        onProgress(ids.size, ids.size)
        if (errors < ids.size) Settings.lastRefresh = System.currentTimeMillis()
        return errors
    }
}
