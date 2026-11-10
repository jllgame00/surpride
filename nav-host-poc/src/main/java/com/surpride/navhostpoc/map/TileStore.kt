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
fun interface TileTransport : AutoCloseable {
    fun fetch(key: TileKey, headers: Map<String, String>): TileResponse
    override fun close() {}
}
data class TileLoad(val bytes: ByteArray?, val stale: Boolean = false, val failure: String? = null,
    val expiresAtMillis: Long = 0, val cacheable: Boolean = true, val requiresValidation: Boolean = false)

/** App-private persistent files, deliberately without automatic eviction of unexpired tiles.
 * This is a revisit cache, not an offline pack: callers request current-viewport tiles only.
 */
class TileStore(private val directory: File, private val transport: TileTransport = OsmTileTransport()) : AutoCloseable {
    @Volatile private var closed = false
    private val closeLock=Any()
    private data class Cached(val bytes: ByteArray, val expires: Long, val etag: String?, val modified: String?,
        val cacheControl: String? = null, val expiresHeader: String? = null)
    private var lastPrune = Long.MIN_VALUE
    fun load(key: TileKey, nowMillis: Long = System.currentTimeMillis()): TileLoad {
        if (closed) return TileLoad(null, failure = "TILE_STORE_CLOSED")
        prune(nowMillis, stem(key))
        val cached = read(key)
        if (cached != null && nowMillis < cached.expires) return TileLoad(cached.bytes, expiresAtMillis = cached.expires,
            requiresValidation = strict(cached.cacheControl))
        val headers = mutableMapOf("User-Agent" to USER_AGENT, "Accept" to "image/png")
        cached?.etag?.let { headers["If-None-Match"] = it }
        cached?.modified?.let { headers["If-Modified-Since"] = it }
        return try {
            val response = transport.fetch(key, headers)
            if (closed) return TileLoad(null, failure = "TILE_STORE_CLOSED")
            val fields = response.headers.mapKeys { it.key.lowercase(Locale.US) }.toMutableMap()
            val bytes = when {
                response.code == 304 && cached != null -> cached.bytes
                response.code == 200 && response.bytes != null && response.bytes.isNotEmpty() -> response.bytes
                else -> throw IOException("TILE_HTTP_${response.code}")
            }
            if (response.code == 304) {
                cached?.cacheControl?.let { fields.putIfAbsent("cache-control", it) }
                cached?.expiresHeader?.let { fields.putIfAbsent("expires", it) }
                cached?.etag?.let { fields.putIfAbsent("etag", it) }
                cached?.modified?.let { fields.putIfAbsent("last-modified", it) }
            }
            val expires = expiry(fields, nowMillis)
            val noStore = fields["cache-control"]?.contains("no-store", ignoreCase = true) == true
            try {
                if (noStore) invalidate(key) else write(key, Cached(bytes, expires, fields["etag"], fields["last-modified"],
                    fields["cache-control"], fields["expires"]))
            } catch (_: Exception) {
                return TileLoad(bytes, failure = "TILE_CACHE_UNAVAILABLE", expiresAtMillis = expires, cacheable = !noStore,
                    requiresValidation = strict(fields["cache-control"]))
            }
            TileLoad(bytes, expiresAtMillis = expires, cacheable = !noStore, requiresValidation = strict(fields["cache-control"]))
        } catch (_: Exception) {
            val validation = strict(cached?.cacheControl)
            TileLoad(if(validation) null else cached?.bytes, stale = cached != null && !validation,
                failure = "TILE_NETWORK_UNAVAILABLE", requiresValidation = validation)
        }
    }
    private fun strict(control:String?)=Regex("(?:^|,)\\s*(no-cache|must-revalidate)(?:\\s|,|=|$)",RegexOption.IGNORE_CASE)
        .containsMatchIn(control.orEmpty())
    private fun expiry(headers: Map<String, String>, now: Long): Long {
        if (Regex("(?:^|,)\\s*no-cache(?:\\s|,|=|$)", RegexOption.IGNORE_CASE)
                .containsMatchIn(headers["cache-control"].orEmpty())) return now
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
            Cached(file.readBytes(), properties.getProperty("expires").toLong(), properties.getProperty("etag"), properties.getProperty("modified"),
                properties.getProperty("cacheControl"), properties.getProperty("expiresHeader"))
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
            value.cacheControl?.let { setProperty("cacheControl", it) }; value.expiresHeader?.let { setProperty("expiresHeader", it) }
        }
        val metadata = File(directory, "$base.meta.tmp")
        metadata.outputStream().use { properties.store(it, "OSM tile cache") }
        java.nio.file.Files.move(metadata.toPath(), File(directory, "$base.meta").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
    fun invalidate(key: TileKey) {
        for (suffix in listOf(".meta", ".png", ".meta.tmp", ".png.tmp")) {
            val file = File(directory, stem(key) + suffix)
            if (file.exists() && !file.delete()) throw IOException("tile removal failed")
        }
    }
    @Synchronized private fun prune(now: Long, current: String) {
        if (lastPrune != Long.MIN_VALUE && now - lastPrune < 3_600_000) return
        lastPrune = now
        val files=directory.listFiles().orEmpty()
        var size=files.sumOf {it.length()}
        // Never discard server-fresh tiles to meet a local size target and trigger needless downloads.
        files.filter { it.name.matches(Regex("\\d+_\\d+_\\d+\\.meta")) && it.nameWithoutExtension != current }
            .sortedBy {it.lastModified()}.forEach { meta ->
                try {
                    val properties = Properties().apply { meta.inputStream().use { load(it) } }
                    if (properties.getProperty("expires").toLong() <= now &&
                        (size>64L*1024*1024 || now - meta.lastModified() > 30L * 86400000)) {
                        val base = meta.nameWithoutExtension
                        val body=File(directory,"$base.png");val bytes=meta.length()+body.length()
                        if (meta.delete() && (!body.exists() || body.delete())) size-=bytes
                    }
                } catch (_: Exception) { /* Unreadable entries are never served by read(). */ }
            }
        files.filter {it.name.matches(Regex("\\d+_\\d+_\\d+\\.(png|meta)(\\.tmp)?")) &&
            it.name.substringBefore('.')!=current && now-it.lastModified()>SEVEN_DAYS &&
            (it.name.endsWith(".tmp") || (it.name.endsWith(".png") && !File(directory,it.nameWithoutExtension+".meta").exists()))}
            .forEach {it.delete()}
    }
    companion object {
        const val USER_AGENT = "SURPRIDE/0.1 (+https://github.com/jllgame00/surpride)"
        const val SEVEN_DAYS = 7 * 86400000L
        const val MAX_TILE_BYTES = 1024 * 1024
    }
    override fun close() {synchronized(closeLock) {if (!closed) {closed = true; transport.close()}}}
}

/** Native client: no Referer is needed. Redirects are disabled to keep tile traffic HTTPS-only. */
class OsmTileTransport : TileTransport {
    private val connections = mutableSetOf<HttpURLConnection>()
    private var closed = false
    override fun fetch(key: TileKey, headers: Map<String, String>): TileResponse {
        val url = URL("https://tile.openstreetmap.org/${key.zoom}/${key.x}/${key.y}.png")
        val connection = url.openConnection() as HttpURLConnection
        synchronized(connections) {
            if (closed) throw IOException("tile transport closed")
            connections.add(connection)
        }
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
        } finally { connection.disconnect(); synchronized(connections) {connections.remove(connection)} }
    }
    override fun close() {
        val active = synchronized(connections) {
            if (closed) return
            closed = true; connections.toList()
        }
        // Disconnect may block in native I/O; never perform it in a Host lifecycle callback.
        if (active.isNotEmpty()) Thread({active.forEach {try {it.disconnect()} catch (_: Exception) {}}},
            "surpride-osm-close").apply {isDaemon = true; start()}
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
