package app.imdbcheck

import org.json.JSONArray
import org.json.JSONObject

/** Vorschlag aus der IMDb-Schnellsuche. */
data class Suggestion(
    val id: String,
    val title: String,
    val year: Int?,
    val yearText: String?,
    val kind: String,          // movie, tvSeries, tvMiniSeries, tvMovie, …
    val cast: String?,
    val poster: String?,
) {
    val isSeries get() = Kinds.isSeries(kind)
}

data class TitleInfo(
    val id: String,
    val title: String,
    val originalTitle: String?,
    val kind: String,
    val year: Int?,
    val endYear: Int?,
    val rating: Double?,
    val votes: Int?,
    val poster: String?,
    val plot: String?,
    val genres: List<String>,
    val runtimeMin: Int?,
) {
    val isSeries get() = Kinds.isSeries(kind)
    val yearText: String?
        get() = when {
            year == null -> null
            !isSeries -> "$year"
            endYear == null -> "$year–"
            endYear == year -> "$year"
            else -> "$year–$endYear"
        }
}

data class Episode(
    val id: String,
    val season: Int,
    val number: Int,
    val title: String,
    val rating: Double?,
    val votes: Int?,
    val date: String?,
) {
    val code get() = "S%02dE%02d".format(season, number)
}

/** Eintrag in „Meine Liste“. */
data class ListItem(
    val id: String,
    val title: String,
    val yearText: String?,
    val year: Int?,
    val kind: String,
    val poster: String?,
    val rating: Double?,
    val votes: Int?,
    val addedAt: Long,
    val updatedAt: Long,
    val watched: Boolean = false,
    val categories: List<String> = emptyList(),
    /** TVmaze-Status: Running, Ended, To Be Determined, In Development */
    val status: String? = null,
    val next: NextEpisode? = null,
) {
    val isSeries get() = Kinds.isSeries(kind)
    /** Nächste Folge, sofern sie noch nicht (länger als einen Tag) zurückliegt. */
    val upcoming: NextEpisode?
        get() = next?.takeIf { it.airMillis > System.currentTimeMillis() - 24 * 3600_000L }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title)
        .put("yearText", yearText ?: JSONObject.NULL)
        .put("year", year ?: JSONObject.NULL)
        .put("kind", kind)
        .put("poster", poster ?: JSONObject.NULL)
        .put("rating", rating ?: JSONObject.NULL)
        .put("votes", votes ?: JSONObject.NULL)
        .put("addedAt", addedAt).put("updatedAt", updatedAt)
        .put("watched", watched)
        .put("categories", JSONArray(categories))
        .put("status", status ?: JSONObject.NULL)
        .put("next", next?.toJson() ?: JSONObject.NULL)

    companion object {
        fun fromJson(o: JSONObject) = ListItem(
            id = o.getString("id"),
            title = o.optString("title"),
            yearText = o.optStr("yearText"),
            year = o.optIntOrNull("year"),
            kind = o.optString("kind", "movie"),
            poster = o.optStr("poster"),
            rating = o.optDoubleOrNull("rating"),
            votes = o.optIntOrNull("votes"),
            addedAt = o.optLong("addedAt"),
            updatedAt = o.optLong("updatedAt"),
            watched = o.optBoolean("watched", false),
            categories = o.arr("categories")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } } } ?: emptyList(),
            status = o.optStr("status"),
            next = o.obj("next")?.let { NextEpisode.fromJson(it) },
        )
    }
}

/** Nächste Folge laut TVmaze. */
data class NextEpisode(
    val season: Int,
    val number: Int,
    val title: String?,
    val airMillis: Long,
    val hasTime: Boolean,
) {
    val code get() = "S%02dE%02d".format(season, number)

    fun toJson(): JSONObject = JSONObject()
        .put("season", season).put("number", number)
        .put("title", title ?: JSONObject.NULL)
        .put("air", airMillis).put("hasTime", hasTime)

    /** z. B. „So, 23.11.2026 · 02:00 Uhr“ in der Zeitzone des Handys. */
    fun whenText(): String {
        val zdt = java.time.Instant.ofEpochMilli(airMillis).atZone(java.time.ZoneId.systemDefault())
        val date = java.time.format.DateTimeFormatter.ofPattern("EE, dd.MM.yyyy", java.util.Locale.GERMANY).format(zdt)
        return if (hasTime) date + " · " + java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(zdt) + " Uhr" else date
    }

    /** heute / morgen / in 5 Tagen */
    fun relativeText(): String {
        val zone = java.time.ZoneId.systemDefault()
        val day = java.time.Instant.ofEpochMilli(airMillis).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(zone), day)
        return when {
            days < 0 -> "gelaufen"
            days == 0L -> "heute"
            days == 1L -> "morgen"
            else -> "in $days Tagen"
        }
    }

    fun shortDate(): String {
        val zdt = java.time.Instant.ofEpochMilli(airMillis).atZone(java.time.ZoneId.systemDefault())
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.").format(zdt)
    }

    companion object {
        fun fromJson(o: JSONObject) = NextEpisode(
            season = o.optInt("season"), number = o.optInt("number"), title = o.optStr("title"),
            airMillis = o.optLong("air"), hasTime = o.optBoolean("hasTime", true),
        )
    }
}

data class ShowStatus(val status: String?, val next: NextEpisode?) {
    val statusText: String?
        get() = when (status) {
            "Running" -> "Läuft"
            "Ended" -> "Beendet"
            "To Be Determined" -> "Zukunft offen"
            "In Development" -> "In Entwicklung"
            else -> status
        }
}

object Kinds {
    fun isSeries(kind: String) = kind == "tvSeries" || kind == "tvMiniSeries"

    fun label(kind: String): String = when (kind) {
        "movie" -> "Film"
        "tvSeries" -> "Serie"
        "tvMiniSeries" -> "Miniserie"
        "tvMovie" -> "TV-Film"
        "tvSpecial" -> "TV-Special"
        "tvShort", "short" -> "Kurzfilm"
        "videoGame" -> "Spiel"
        "video" -> "Video"
        "tvEpisode" -> "Episode"
        else -> kind
    }
}

// ---------- kleine JSON-Helfer ----------

fun JSONObject.optStr(key: String): String? =
    if (isNull(key)) null else optString(key, "").ifBlank { null }

fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else (opt(key) as? Number)?.toInt() ?: optString(key).toIntOrNull()

fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else (opt(key) as? Number)?.toDouble() ?: optString(key).toDoubleOrNull()

fun JSONObject.obj(key: String): JSONObject? = if (isNull(key)) null else optJSONObject(key)

fun JSONObject.arr(key: String): JSONArray? = if (isNull(key)) null else optJSONArray(key)

inline fun JSONArray.forEachObj(f: (JSONObject) -> Unit) {
    for (i in 0 until length()) optJSONObject(i)?.let(f)
}

/** IMDb-Bild-URL auf passende Breite verkleinern (spart Datenvolumen). */
fun posterUrl(url: String?, width: Int): String? {
    if (url.isNullOrBlank()) return null
    val m = Regex("""\._V1_.*\.(jpg|jpeg|png|webp)$""", RegexOption.IGNORE_CASE).find(url) ?: return url
    return url.substring(0, m.range.first) + "._V1_QL75_UX${width}_." + m.groupValues[1]
}

fun formatVotes(v: Int?): String = when {
    v == null -> "–"
    v >= 1_000_000 -> String.format(java.util.Locale.GERMANY, "%.1f Mio.", v / 1_000_000.0)
    v >= 1_000 -> String.format(java.util.Locale.GERMANY, "%.0f Tsd.", v / 1_000.0)
    else -> v.toString()
}

fun formatRating(r: Double?): String =
    if (r == null) "–" else String.format(java.util.Locale.GERMANY, "%.1f", r)
