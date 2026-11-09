package com.surpride.navhostpoc.map

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Properties
import java.util.TimeZone

data class TileResponse(val code: Int, val bytes: ByteArray?, val headers: Map<String, String>)
fun interface TileTransport { fun fetch(key: TileKey, headers: Map<String, String>): TileResponse }
data class TileLoad(val bytes: ByteArray?, val stale: Boolean = false, val failure: String? = null)

/** App-private persistent files, deliberately without automatic eviction of unexpired tiles.
 * This is a revisit cache, not an offline pack: callers request current-viewport tiles only.
 */
class TileStore(private val directory: File, private val transport: TileTransport = OsmTileTransport()) {
    private data class Cached(val bytes: ByteArray, val expires: Long, val etag: String?, val modified: String?)
    fun load(key: TileKey, nowMillis: Long = System.currentTimeMillis()): TileLoad {
        val cached = read(key)
        if (cached != null && nowMillis < cached.expires) return TileLoad(cached.bytes)
        val headers = mutableMapOf("User-Agent" to USER_AGENT, "Accept" to "image/png")
        cached?.etag?.let { headers["If-None-Match"] = it }
        cached?.modified?.let { headers["If-Modified-Since"] = it }
        return try {
            val response = transport.fetch(key, headers)
            val fields = response.headers.mapKeys { it.key.lowercase(Locale.US) }
            val bytes = when {
                response.code == 304 && cached != null -> cached.bytes
                response.code == 200 && response.bytes != null && response.bytes.isNotEmpty() -> response.bytes
                else -> throw IOException("TILE_HTTP_${response.code}")
            }
            val expires = expiry(fields, nowMillis)
            val noStore = fields["cache-control"]?.contains("no-store", ignoreCase = true) == true
            if (!noStore) {
                try { write(key, Cached(bytes, expires, fields["etag"] ?: cached?.etag, fields["last-modified"] ?: cached?.modified)) }
                catch (_: Exception) { return TileLoad(bytes, failure = "TILE_CACHE_UNAVAILABLE") }
            }
            TileLoad(bytes)
        } catch (_: Exception) {
            TileLoad(cached?.bytes, stale = cached != null, failure = "TILE_NETWORK_UNAVAILABLE")
        }
    }
    private fun expiry(headers: Map<String, String>, now: Long): Long {
        val maxAge = Regex("(?:^|,)\\s*max-age\\s*=\\s*\"?(\\d+)", RegexOption.IGNORE_CASE)
            .find(headers["cache-control"].orEmpty())?.groupValues?.get(1)?.toLongOrNull()
        if (maxAge != null) {
            val age = headers["age"]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
            return now + (maxAge - age).coerceIn(0, 3650L * 86400) * 1000
        }
        headers["expires"]?.let { value ->
            try {
                val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }
                format.parse(value)?.let { return it.time }
            } catch (_: Exception) { }
        }
        return now + SEVEN_DAYS
    }
    private fun stem(key: TileKey) = "${key.zoom}_${key.x}_${key.y}"
    private fun read(key: TileKey): Cached? = try {
        val base = stem(key)
        val properties = Properties().apply { File(directory, "$base.meta").inputStream().use { load(it) } }
        val file = File(directory, "$base.png")
        if (file.length() !in 1..MAX_TILE_BYTES.toLong()) null else
            Cached(file.readBytes(), properties.getProperty("expires").toLong(), properties.getProperty("etag"), properties.getProperty("modified"))
    } catch (_: Exception) { null }
    private fun write(key: TileKey, value: Cached) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("cache directory")
        val base = stem(key)
        val bytes = File(directory, "$base.png.tmp")
        bytes.writeBytes(value.bytes)
        java.nio.file.Files.move(bytes.toPath(), File(directory, "$base.png").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val properties = Properties().apply {
            setProperty("expires", value.expires.toString())
            value.etag?.let { setProperty("etag", it) }; value.modified?.let { setProperty("modified", it) }
        }
        val metadata = File(directory, "$base.meta.tmp")
        metadata.outputStream().use { properties.store(it, "OSM tile cache") }
        java.nio.file.Files.move(metadata.toPath(), File(directory, "$base.meta").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    companion object {
        const val USER_AGENT = "SURPRIDE/0.1 (+https://github.com/jllgame00/surpride)"
        const val SEVEN_DAYS = 7 * 86400000L
        const val MAX_TILE_BYTES = 1024 * 1024
    }
}

/** Native client: no Referer is needed. Redirects are disabled to keep tile traffic HTTPS-only. */
class OsmTileTransport : TileTransport {
    override fun fetch(key: TileKey, headers: Map<String, String>): TileResponse {
        val url = URL("https://tile.openstreetmap.org/${key.zoom}/${key.x}/${key.y}.png")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val code = connection.responseCode
            val responseHeaders = connection.headerFields.entries.mapNotNull { (name, values) ->
                if (name == null || values.isNullOrEmpty()) null else name to values.joinToString(",")
            }.toMap()
            val bytes = if (code == 200) connection.inputStream.use { input ->
                val data = input.readBytesLimited(TileStore.MAX_TILE_BYTES)
                if (data.isEmpty()) throw IOException("empty tile")
                data
            } else null
            return TileResponse(code, bytes, responseHeaders)
        } finally { connection.disconnect() }
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (output.size() + read > limit) throw IOException("tile too large")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
