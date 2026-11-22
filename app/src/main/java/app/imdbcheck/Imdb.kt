package app.imdbcheck

import org.json.JSONObject
import java.io.IOException

/**
 * Datenquellen:
 *  - IMDb-Schnellsuche (dieselbe, die imdb.com für Suchvorschläge nutzt)
 *  - IMDb-GraphQL (öffentlicher Endpunkt der IMDb-Webseite) für Wertungen und Episoden
 *  - optional OMDb (eigener kostenloser API-Key) als Ersatz, falls IMDb mal blockt
 */
object Imdb {

    // ===================== Suche =====================

    fun suggest(query: String): List<Suggestion> {
        val q = query.trim().lowercase()
        if (q.length < 3) return emptyList()
        val first = q.first().let { if (it.isLetterOrDigit() && it.code < 128) it else 'x' }
        val url = "https://v3.sg.media-imdb.com/suggestion/$first/${Http.encPath(q)}.json?includeVideos=0"
        val root = JSONObject(Http.get(url))
        val out = mutableListOf<Suggestion>()
        root.arr("d")?.forEachObj { o ->
            val id = o.optString("id")
            if (!id.startsWith("tt")) return@forEachObj
            val kind = o.optStr("qid") ?: "movie"
            if (kind == "tvEpisode" || kind == "videoGame" || kind == "podcastSeries" || kind == "podcastEpisode") return@forEachObj
            out += Suggestion(
                id = id,
                title = o.optString("l"),
                year = o.optIntOrNull("y"),
                yearText = (o.optStr("yr") ?: o.optIntOrNull("y")?.toString())?.replace("-", "–"),
                kind = kind,
                cast = o.optStr("s"),
                poster = o.obj("i")?.optStr("imageUrl"),
            )
        }
        return out
    }

    // ===================== GraphQL =====================

    private val ENDPOINTS = listOf("https://api.graphql.imdb.com/", "https://caching.graphql.imdb.com/")

    private fun headers(): Map<String, String> {
        val h = mutableMapOf(
            "Origin" to "https://www.imdb.com",
            "Referer" to "https://www.imdb.com/",
            "x-imdb-client-name" to "imdb-web-next-localized",
        )
        if (Settings.germanTitles) {
            h["x-imdb-user-country"] = "DE"
            h["x-imdb-user-language"] = "de-DE"
        }
        return h
    }

    /** Führt eine Abfrage aus und liefert `data` – probiert beide Endpunkte. */
    private fun gql(query: String, valid: (JSONObject) -> Boolean = { it.obj("title") != null }): JSONObject {
        val body = JSONObject().put("query", query).toString()
        var last: Exception? = null
        for (ep in ENDPOINTS) {
            try {
                val res = JSONObject(Http.postJson(ep, body, headers()))
                val data = res.obj("data")
                val errors = res.arr("errors")
                if (data != null && valid(data)) return data
                val msg = errors?.optJSONObject(0)?.optString("message") ?: "keine Daten"
                last = IOException("IMDb: $msg")
                // Schema-/Abfragefehler sind auf dem zweiten Endpunkt identisch → nicht wiederholen
                if (errors != null) break
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("IMDb nicht erreichbar")
    }

    private fun firstSuccess(queries: List<String>): JSONObject {
        var last: Exception? = null
        for (q in queries) {
            try { return gql(q) } catch (e: Exception) { last = e }
        }
        throw last ?: IOException("IMDb nicht erreichbar")
    }

    private const val BASE_FIELDS = """
        id
        titleText { text }
        originalTitleText { text }
        titleType { id }
        releaseYear { year endYear }
        ratingsSummary { aggregateRating voteCount }
        primaryImage { url }
    """

    fun title(id: String, minimal: Boolean = false): TitleInfo {
        val full = """{ title(id: "$id") { $BASE_FIELDS
            plot { plotText { plainText } }
            genres { genres { text } }
            runtime { seconds }
        } }"""
        val min = """{ title(id: "$id") { $BASE_FIELDS } }"""
        val data = firstSuccess(if (minimal) listOf(min) else listOf(full, min))
        return parseTitle(data.getJSONObject("title"), id)
    }

    // ---------- Top-Serien ----------

    /** Top-Serien laut IMDb (Top 250 TV), die ersten [count] Plätze. */
    fun topSeries(count: Int = 50): List<TitleInfo> {
        val attempts = listOf<() -> List<TitleInfo>>(
            {
                val q = """{ chartTitles(first: $count, chart: { chartType: TOP_RATED_TV_SHOWS }) {
                    edges { currentRank node { $BASE_FIELDS } } } }"""
                edgesToTitles(gql(q) { it.obj("chartTitles") != null }.getJSONObject("chartTitles"))
            },
            {
                val q = """{ titleChartRankings(first: $count, input: { rankingsChartType: TOP_250_TV }) {
                    edges { node { rank item { $BASE_FIELDS } } } } }"""
                edgesToTitles(gql(q) { it.obj("titleChartRankings") != null }.getJSONObject("titleChartRankings"))
            },
            { topSeriesFromPage() },
        )
        var last: Exception? = null
        for (a in attempts) {
            try {
                val list = a()
                if (list.size >= 10) return list.take(count)
                last = IOException("Liste unvollständig")
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Top-Liste nicht verfügbar")
    }

    /** Kanten einer Chart-Abfrage in Titel umwandeln (beide bekannten Formen). */
    private fun edgesToTitles(conn: JSONObject): List<TitleInfo> {
        val out = mutableListOf<Pair<Int, TitleInfo>>()
        conn.arr("edges")?.forEachObj { e ->
            val node = e.obj("node") ?: return@forEachObj
            val t = node.obj("item") ?: node
            val id = t.optStr("id") ?: return@forEachObj
            val rank = e.optIntOrNull("currentRank") ?: node.optIntOrNull("rank") ?: (out.size + 1)
            out += rank to parseTitle(t, id)
        }
        return out.sortedBy { it.first }.map { it.second }
    }

    /** Ersatz: Chart-Seite von imdb.com lesen (eingebettete Seitendaten bzw. JSON-LD). */
    private fun topSeriesFromPage(): List<TitleInfo> {
        val html = Http.get("https://www.imdb.com/chart/toptv/", mapOf("Accept" to "text/html"))
        // 1) __NEXT_DATA__ mit chartTitles
        Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)?.let { m ->
            runCatching {
                findChart(JSONObject(m.groupValues[1]))?.let { conn ->
                    val list = edgesToTitles(conn)
                    if (list.isNotEmpty()) return list
                }
            }
        }
        // 2) JSON-LD ItemList
        val out = mutableListOf<TitleInfo>()
        Regex("""<script type="application/ld\+json"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).findAll(html).forEach { m ->
            val o = runCatching { JSONObject(m.groupValues[1]) }.getOrNull() ?: return@forEach
            o.arr("itemListElement")?.forEachObj { el ->
                val item = el.obj("item") ?: el
                val id = Regex("tt\\d+").find(item.optString("url"))?.value ?: return@forEachObj
                out += TitleInfo(
                    id = id,
                    title = android.text.Html.fromHtml(item.optString("name"), 0).toString(),
                    originalTitle = item.optStr("alternateName"),
                    kind = "tvSeries",
                    year = null, endYear = null,
                    rating = item.obj("aggregateRating")?.optDoubleOrNull("ratingValue"),
                    votes = item.obj("aggregateRating")?.optIntOrNull("ratingCount"),
                    poster = item.optStr("image"),
                    plot = item.optStr("description"),
                    genres = item.optStr("genre")?.split(",")?.map { g -> g.trim() } ?: emptyList(),
                    runtimeMin = null,
                )
            }
        }
        return out
    }

    /** Sucht rekursiv nach einem Objekt „chartTitles“ mit „edges“. */
    private fun findChart(o: Any?, depth: Int = 0): JSONObject? {
        if (depth > 12) return null
        when (o) {
            is JSONObject -> {
                o.optJSONObject("chartTitles")?.takeIf { it.has("edges") }?.let { return it }
                o.optJSONObject("titleChartRankings")?.takeIf { it.has("edges") }?.let { return it }
                val keys = o.keys()
                while (keys.hasNext()) findChart(o.opt(keys.next()), depth + 1)?.let { return it }
            }
            is org.json.JSONArray -> for (i in 0 until o.length()) findChart(o.opt(i), depth + 1)?.let { return it }
        }
        return null
    }

    private fun parseTitle(t: JSONObject, id: String): TitleInfo {
        val genres = mutableListOf<String>()
        t.obj("genres")?.arr("genres")?.forEachObj { g -> g.optStr("text")?.let(genres::add) }
        val title = t.obj("titleText")?.optStr("text") ?: id
        val orig = t.obj("originalTitleText")?.optStr("text")
        return TitleInfo(
            id = id,
            title = title,
            originalTitle = orig?.takeIf { it != title },
            kind = t.obj("titleType")?.optStr("id") ?: "movie",
            year = t.obj("releaseYear")?.optIntOrNull("year"),
            endYear = t.obj("releaseYear")?.optIntOrNull("endYear"),
            rating = t.obj("ratingsSummary")?.optDoubleOrNull("aggregateRating"),
            votes = t.obj("ratingsSummary")?.optIntOrNull("voteCount"),
            poster = t.obj("primaryImage")?.optStr("url"),
            plot = t.obj("plot")?.obj("plotText")?.optStr("plainText"),
            genres = genres,
            runtimeMin = t.obj("runtime")?.optIntOrNull("seconds")?.let { it / 60 },
        )
    }

    // ---------- Episoden ----------

    private const val EP_NUM_A = "series { episodeNumber { episodeNumber seasonNumber } }"
    private const val EP_NUM_B =
        "series { displayableEpisodeNumber { displayableSeason { text } episodeNumber { text } } }"

    private fun episodesQuery(id: String, numFields: String, first: Int, after: String?): String {
        val afterArg = if (after != null) """, after: "$after"""" else ""
        return """{ title(id: "$id") { episodes { episodes(first: $first$afterArg) {
            edges { node {
                id
                titleText { text }
                $numFields
                ratingsSummary { aggregateRating voteCount }
                releaseDate { year month day }
            } }
            pageInfo { hasNextPage endCursor }
        } } } }"""
    }

    fun episodes(id: String, onProgress: (Int) -> Unit = {}): List<Episode> {
        // Variante finden, die das aktuelle Schema akzeptiert
        val variants = listOf(EP_NUM_A to 250, EP_NUM_B to 250, EP_NUM_A to 50, EP_NUM_B to 50)
        var last: Exception? = null
        for ((fields, first) in variants) {
            try {
                return fetchAllEpisodes(id, fields, first, onProgress)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Episoden nicht verfügbar")
    }

    private fun fetchAllEpisodes(id: String, fields: String, first: Int, onProgress: (Int) -> Unit): List<Episode> {
        val out = mutableListOf<Episode>()
        var after: String? = null
        var pages = 0
        do {
            val data = gql(episodesQuery(id, fields, first, after))
            val conn = data.getJSONObject("title").obj("episodes")?.obj("episodes")
                ?: return out
            conn.arr("edges")?.forEachObj { e -> e.obj("node")?.let { parseEpisode(it) }?.let(out::add) }
            onProgress(out.size)
            val pi = conn.obj("pageInfo")
            after = if (pi?.optBoolean("hasNextPage") == true) pi.optStr("endCursor") else null
            pages++
        } while (after != null && pages < 40)
        return out
    }

    private fun parseEpisode(n: JSONObject): Episode? {
        val s = n.obj("series") ?: return null
        var season: Int? = null
        var number: Int? = null
        s.obj("episodeNumber")?.let {
            season = it.optIntOrNull("seasonNumber")
            number = it.optIntOrNull("episodeNumber")
        }
        s.obj("displayableEpisodeNumber")?.let {
            season = season ?: it.obj("displayableSeason")?.optStr("text")?.toIntOrNull()
            number = number ?: it.obj("episodeNumber")?.optStr("text")?.toIntOrNull()
        }
        val se = season ?: return null
        val ep = number ?: return null
        if (se <= 0 || ep <= 0) return null
        val rd = n.obj("releaseDate")
        val date = rd?.optIntOrNull("year")?.let { y ->
            val m = rd.optIntOrNull("month"); val d = rd.optIntOrNull("day")
            when {
                m != null && d != null -> "%02d.%02d.%d".format(d, m, y)
                m != null -> "%02d/%d".format(m, y)
                else -> "$y"
            }
        }
        return Episode(
            id = n.optString("id"),
            season = se,
            number = ep,
            title = n.obj("titleText")?.optStr("text") ?: "Episode $ep",
            rating = n.obj("ratingsSummary")?.optDoubleOrNull("aggregateRating"),
            votes = n.obj("ratingsSummary")?.optIntOrNull("voteCount"),
            date = date,
        )
    }
}

/** Ersatzquelle OMDb (www.omdbapi.com) – liefert ebenfalls die IMDb-Wertungen. Nur mit eigenem Key. */
object Omdb {
    private fun call(params: String): JSONObject {
        val key = Settings.omdbKey.trim()
        if (key.isEmpty()) throw IOException("Kein OMDb-Key")
        val o = JSONObject(Http.get("https://www.omdbapi.com/?apikey=${Http.enc(key)}&$params"))
        if (o.optString("Response") != "True") throw IOException("OMDb: " + o.optString("Error", "Fehler"))
        return o
    }

    private fun num(s: String?): Double? = s?.replace(",", "")?.toDoubleOrNull()

    fun title(id: String): TitleInfo {
        val o = call("i=$id&plot=short")
        val type = when (o.optString("Type")) {
            "series" -> "tvSeries"
            "episode" -> "tvEpisode"
            "game" -> "videoGame"
            else -> "movie"
        }
        val years = o.optString("Year").split("–", "-").mapNotNull { it.trim().toIntOrNull() }
        return TitleInfo(
            id = id,
            title = o.optString("Title"),
            originalTitle = null,
            kind = type,
            year = years.getOrNull(0),
            endYear = years.getOrNull(1),
            rating = num(o.optString("imdbRating")),
            votes = num(o.optString("imdbVotes"))?.toInt(),
            poster = o.optString("Poster").takeIf { it.startsWith("http") },
            plot = o.optString("Plot").takeIf { it.isNotBlank() && it != "N/A" },
            genres = o.optString("Genre").split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "N/A" },
            runtimeMin = o.optString("Runtime").substringBefore(" ").toIntOrNull(),
        )
    }

    fun episodes(id: String, onProgress: (Int) -> Unit = {}): List<Episode> {
        val total = call("i=$id").optString("totalSeasons").toIntOrNull() ?: 1
        val out = mutableListOf<Episode>()
        for (s in 1..total.coerceAtMost(60)) {
            val o = runCatching { call("i=$id&Season=$s") }.getOrNull() ?: continue
            o.arr("Episodes")?.forEachObj { e ->
                val nr = e.optString("Episode").toIntOrNull() ?: return@forEachObj
                out += Episode(
                    id = e.optString("imdbID"),
                    season = s,
                    number = nr,
                    title = e.optString("Title"),
                    rating = num(e.optString("imdbRating")),
                    votes = null,
                    date = e.optString("Released").takeIf { it != "N/A" },
                )
            }
            onProgress(out.size)
        }
        return out
    }
}

/** TVmaze (api.tvmaze.com, frei nutzbar ohne Key): Status der Serie und Termin der nächsten Folge. */
object TvMaze {
    fun status(imdbId: String): ShowStatus {
        val show = try {
            JSONObject(Http.get("https://api.tvmaze.com/lookup/shows?imdb=$imdbId"))
        } catch (e: IOException) {
            if (e.message?.startsWith("HTTP 404") == true) return ShowStatus(null, null) // Serie bei TVmaze unbekannt
            throw e
        }
        val tvId = show.optInt("id")
        val status = show.optStr("status")
        if (tvId == 0) return ShowStatus(status, null)
        val full = JSONObject(Http.get("https://api.tvmaze.com/shows/$tvId?embed=nextepisode"))
        val n = full.obj("_embedded")?.obj("nextepisode") ?: return ShowStatus(full.optStr("status") ?: status, null)
        val stamp = n.optStr("airstamp")
        val airtime = n.optStr("airtime")
        val millis = stamp?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            ?: n.optStr("airdate")?.let {
                runCatching {
                    java.time.LocalDate.parse(it).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrNull()
            }
            ?: return ShowStatus(full.optStr("status") ?: status, null)
        return ShowStatus(
            full.optStr("status") ?: status,
            NextEpisode(
                season = n.optInt("season"),
                number = n.optInt("number"),
                title = n.optStr("name"),
                airMillis = millis,
                hasTime = stamp != null && !airtime.isNullOrBlank(),
            ),
        )
    }
}
