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

    var heatmapHorizontal: Boolean
        get() = prefs.getBoolean("heatmapHorizontal", false)
        set(v) = prefs.edit().putBoolean("heatmapHorizontal", v).apply()

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
    var categories by mutableStateOf<List<String>>(emptyList())
        private set

    private val titleCache = java.util.concurrent.ConcurrentHashMap<String, TitleInfo>()
    private val episodeCache = java.util.concurrent.ConcurrentHashMap<String, List<Episode>>()
    private val statusCache = java.util.concurrent.ConcurrentHashMap<String, ShowStatus>()

    fun init(ctx: Context) {
        Settings.init(ctx)
        file = File(ctx.filesDir, "list.json")
        runCatching {
            val text = file.readText()
            items = parse(text)
            categories = parseCategories(text)
        }
    }

    private fun parse(text: String): List<ListItem> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("items") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { runCatching { ListItem.fromJson(it) }.getOrNull() }
        }
    }

    private fun parseCategories(text: String): List<String> {
        val a = JSONObject(text).optJSONArray("categories") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } }.distinct()
    }

    fun exportJson(): String {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        return JSONObject().put("app", "ImdbCheck").put("version", 2)
            .put("categories", JSONArray(categories))
            .put("items", arr).toString(2)
    }

    /** Fügt importierte Einträge hinzu (vorhandene bleiben). Liefert die Anzahl neuer Einträge. */
    fun importJson(text: String): Int {
        val incoming = parse(text)
        val have = items.map { it.id }.toSet()
        val fresh = incoming.filter { it.id !in have }
        val cats = (categories + parseCategories(text) + incoming.flatMap { it.categories }).distinct()
        save(items + fresh, cats)
        return fresh.size
    }

    private fun save(list: List<ListItem>, cats: List<String> = categories) {
        items = list
        categories = cats
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

    // ---------- Kategorien ----------

    /** Legt eine Kategorie an (falls neu) und liefert ihren Namen. */
    fun addCategory(name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return null
        categories.firstOrNull { it.equals(n, ignoreCase = true) }?.let { return it }
        save(items, categories + n)
        return n
    }

    fun renameCategory(old: String, new: String) {
        val n = new.trim()
        if (n.isEmpty() || n == old) return
        val target = categories.firstOrNull { it.equals(n, ignoreCase = true) && it != old } ?: n
        save(
            items.map { i -> if (old in i.categories) i.copy(categories = (i.categories.map { if (it == old) target else it }).distinct()) else i },
            categories.map { if (it == old) target else it }.distinct(),
        )
    }

    fun deleteCategory(name: String) = save(
        items.map { if (name in it.categories) it.copy(categories = it.categories - name) else it },
        categories - name,
    )

    fun toggleCategory(id: String, cat: String) = save(items.map {
        if (it.id != id) it
        else it.copy(categories = if (cat in it.categories) it.categories - cat else it.categories + cat)
    })

    private fun applyStatus(id: String, st: ShowStatus) {
        if (!contains(id)) return
        save(items.map { if (it.id == id) it.copy(status = st.status, next = st.next) else it })
    }

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
    suspend fun addSuggestion(s: Suggestion, cats: List<String> = emptyList()) {
        val now = System.currentTimeMillis()
        add(
            ListItem(
                id = s.id, title = s.title, yearText = s.yearText, year = s.year, kind = s.kind,
                poster = s.poster, rating = null, votes = null, addedAt = now, updatedAt = 0L,
                categories = cats,
            )
        )
        runCatching { title(s.id, force = true, minimal = true) }
        if (s.isSeries) runCatching { showStatus(s.id, force = true) }
    }

    fun addInfo(info: TitleInfo, cats: List<String> = emptyList()) {
        val now = System.currentTimeMillis()
        add(
            ListItem(
                id = info.id, title = info.title, yearText = info.yearText, year = info.year, kind = info.kind,
                poster = info.poster, rating = info.rating, votes = info.votes, addedAt = now, updatedAt = now,
                categories = cats, status = statusCache[info.id]?.status, next = statusCache[info.id]?.next,
            )
        )
    }

    /** Serienstatus + nächste Folge (TVmaze). */
    suspend fun showStatus(id: String, force: Boolean = false): ShowStatus = withContext(Dispatchers.IO) {
        if (!force) statusCache[id]?.let { return@withContext it }
        val st = TvMaze.status(id)
        statusCache[id] = st
        withContext(Dispatchers.Main) { applyStatus(id, st) }
        st
    }

    /** Aktualisiert die Wertungen aller Einträge. Liefert die Anzahl der Fehler. */
    suspend fun refreshAll(onProgress: (done: Int, total: Int) -> Unit): Int {
        val ids = items.map { it.id }
        var errors = 0
        ids.forEachIndexed { i, id ->
            onProgress(i, ids.size)
            try { title(id, force = true, minimal = true) } catch (_: Exception) { errors++ }
            val item = items.firstOrNull { it.id == id }
            // Beendete Serien brauchen keinen neuen Sendetermin
            if (item != null && item.isSeries && item.status != "Ended") {
                runCatching { showStatus(id, force = true) }
            }
            delay(150)
        }
        onProgress(ids.size, ids.size)
        if (errors < ids.size) Settings.lastRefresh = System.currentTimeMillis()
        return errors
    }
}
