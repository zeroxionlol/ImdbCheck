package app.imdbcheck

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** Für Pfad-Segmente: Leerzeichen als %20 statt +. */
    fun encPath(s: String): String = enc(s).replace("+", "%20")

    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        String(request(url, "GET", null, headers), Charsets.UTF_8)

    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String =
        String(request(url, "POST", body, mapOf("Content-Type" to "application/json") + headers), Charsets.UTF_8)

    private fun request(url: String, method: String, body: String?, headers: Map<String, String>): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 25_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", BROWSER_UA)
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = runCatching { c.errorStream?.use { String(it.readBytes()).take(300) } }.getOrNull()
                throw IOException("HTTP $code (${URL(url).host})" + (err?.let { ": $it" } ?: ""))
            }
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }
}
