package com.surpride.navhostpoc.map

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.surpride.navhostpoc.guidance.GuidanceRoute
import com.surpride.navhostpoc.tracking.MatchState
import com.surpride.navhostpoc.tracking.PositionFix
import com.surpride.navhostpoc.tracking.PositionSource
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.*

/** Canvas renderer for the host's stable unobscured map region. No destination identity is rendered.
 * Map labels are the original OSM raster labels and can include ordinary place/POI names.
 */
class RoadMapRenderer(context: Context, private val onChanged: () -> Unit) : AutoCloseable {
    private data class TileImage(val bitmap: Bitmap?, val stale: Boolean, val failed: Boolean, val loadedAt: Long)
    private val camera = FollowCamera()
    private val tiles = ViewportTiles(File(context.applicationContext.filesDir, "osm-viewport-tiles"))
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var closed = false
    private var redrawPending = false
    private var lastTileMetadata: String? = null
    private val redraw = Runnable {
        synchronized(lock) { redrawPending = false; if (closed) return@Runnable }
        onChanged()
    }
    private fun changed() {
        synchronized(lock) {
            if (closed || redrawPending) return
            redrawPending = true
            handler.postDelayed(redraw, 150)
        }
    }

    @Synchronized
    fun render(canvas: Canvas, bounds: Rect, route: GuidanceRoute?, fix: PositionFix?, match: MatchState?, status: String,
        darkMode: Boolean = false) {
        if (closed || bounds.width() <= 0 || bounds.height() <= 0) return
        val saved = canvas.save()
        try {
            canvas.clipRect(bounds)
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.drawColor(if (darkMode) Color.rgb(30, 34, 38) else Color.rgb(230, 233, 231))
            val width = bounds.width(); val height = bounds.height()
            val unit = min(width / 900f, height / 520f).coerceIn(0.85f, 2.5f)
            val pose = camera.update(route, fix, match, width, height)
            val visible = pose?.visibleTiles().orEmpty()
            tiles.want(visible.map { it.key }.toSet())
            // Transform raster colours only. Original OSM road/place/POI label pixels remain intact.
            val rasterPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                if (darkMode) colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    -0.75f, 0f, 0f, 0f, 220f,
                    0f, -0.75f, 0f, 0f, 225f,
                    0f, 0f, -0.75f, 0f, 230f,
                    0f, 0f, 0f, 1f, 0f)))
            }
            var loaded = 0; var failures = 0; var stale = 0
            for (tile in visible) {
                val image = tiles.peek(tile.key)
                val bitmap = image?.bitmap
                if (bitmap != null) {
                    val corners = pose!!.tileCorners(tile)
                    val matrix = Matrix()
                    matrix.setPolyToPoly(floatArrayOf(0f, 0f, bitmap.width.toFloat(), 0f, 0f, bitmap.height.toFloat()), 0,
                        floatArrayOf(corners[0].x.toFloat(), corners[0].y.toFloat(), corners[1].x.toFloat(), corners[1].y.toFloat(),
                            corners[3].x.toFloat(), corners[3].y.toFloat()), 0, 3)
                    canvas.drawBitmap(bitmap, matrix, rasterPaint)
                    loaded++
                    if (image.stale) stale++
                }
                if (image?.failed == true) failures++
            }
            val tileMetadata = "MAP_TILES loaded=$loaded requested=${visible.size} failed=$failures stale=$stale"
            if (tileMetadata != lastTileMetadata) { lastTileMetadata = tileMetadata; Log.i("SurprideMap", tileMetadata) }
            if (pose != null && fix != null) {
                drawForwardPath(canvas, pose, unit)
                drawPosition(canvas, pose, fix, unit)
            }
            val mapStatus = when {
                pose == null -> "현재 위치 대기 · 지도 미표시"
                failures > 0 && loaded == 0 -> "지도 로드 실패 · 네트워크 확인 필요"
                stale > 0 -> "저장 지도 표시 · 최신 지도 연결 실패"
                failures > 0 -> "지도 일부 로드 실패 ($loaded/${visible.size})"
                loaded < visible.size -> "OSM 지도 로딩 ($loaded/${visible.size})"
                else -> "OSM 지도 표시"
            }
            label(canvas, mapStatus, 14f * unit, 14f * unit, 18f * unit, width - 28f * unit)
            if (status.isNotBlank()) label(canvas, status, 14f * unit, 49f * unit, 15f * unit, width - 28f * unit)
            if (fix != null) {
                val source = when {
                    fix.source == PositionSource.FAKE -> "모의 위치"
                    fix.platformMock -> "기기 모의 위치"
                    else -> "기기 위치"
                }
                val direction = when (pose?.headingSource) {
                    HeadingSource.ROUTE_ESTIMATE -> " · 경로 방향 추정"
                    HeadingSource.DEVICE_BEARING -> " · GPS 이동 방향"
                    else -> " · 방향 정보 없음"
                }
                label(canvas, source + direction, 14f * unit, height - 78f * unit, 14f * unit, width - 28f * unit)
            }
            // Both lines stay inside the supplied map bounds, above any host-owned footer.
            label(canvas, "© OpenStreetMap contributors", 12f * unit, height - 51f * unit, 13f * unit, width - 24f * unit)
            label(canvas, "openstreetmap.org/copyright · ODbL", 12f * unit, height - 28f * unit, 13f * unit, width - 24f * unit)
        } finally { canvas.restoreToCount(saved) }
    }

    private fun drawForwardPath(canvas: Canvas, pose: CameraPose, unit: Float) {
        val path = pose.forwardPath
        if (path.size < 2) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        path.zipWithNext().forEachIndexed { index, (a, b) ->
            val start = pose.screen(a); val end = pose.screen(b)
            // Fade the forward window into the road instead of drawing a terminal cap or destination pin.
            fun alpha(fraction: Double) = if (fraction < 0.72) 255 else (255 * ((1 - fraction) / 0.28).coerceIn(0.0, 1.0)).toInt()
            val startAlpha = alpha(index.toDouble() / (path.size - 1))
            val endAlpha = alpha((index + 1.0) / (path.size - 1))
            paint.strokeCap = if (index == path.size - 2) Paint.Cap.BUTT else Paint.Cap.ROUND
            paint.shader = LinearGradient(start.x.toFloat(), start.y.toFloat(), end.x.toFloat(), end.y.toFloat(),
                Color.argb(startAlpha, 255, 255, 255), Color.argb(endAlpha, 255, 255, 255), Shader.TileMode.CLAMP)
            paint.strokeWidth = 12f * unit
            canvas.drawLine(start.x.toFloat(), start.y.toFloat(), end.x.toFloat(), end.y.toFloat(), paint)
            paint.shader = LinearGradient(start.x.toFloat(), start.y.toFloat(), end.x.toFloat(), end.y.toFloat(),
                Color.argb(startAlpha, 33, 109, 223), Color.argb(endAlpha, 33, 109, 223), Shader.TileMode.CLAMP)
            paint.strokeWidth = 7f * unit
            canvas.drawLine(start.x.toFloat(), start.y.toFloat(), end.x.toFloat(), end.y.toFloat(), paint)
        }
    }
    private fun drawPosition(canvas: Canvas, pose: CameraPose, fix: PositionFix, unit: Float) {
        val x = pose.anchorX.toFloat(); val y = pose.anchorY.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = (fix.accuracyMeters.takeIf { it.isFinite() && it > 0 } ?: 0.0) /
            WebMercator.metersPerPixel(fix.coordinate.latitude, pose.zoom)
        paint.color = Color.argb(35, 35, 111, 220)
        canvas.drawCircle(x, y, radius.coerceIn(0.0, pose.width * 0.2).toFloat(), paint)
        paint.color = Color.WHITE; canvas.drawCircle(x, y, 20f * unit, paint)
        paint.color = Color.rgb(30, 99, 207); canvas.drawCircle(x, y, 16f * unit, paint)
        paint.color = Color.WHITE
        if (pose.directionDegrees == null) {
            canvas.drawCircle(x, y, 5f * unit, paint)
            return
        }
        val saved = canvas.save()
        try {
            canvas.rotate((pose.directionDegrees - pose.headingDegrees).toFloat(), x, y)
            val arrow = Path().apply {
                moveTo(x, y - 12f * unit); lineTo(x + 8f * unit, y + 8f * unit)
                lineTo(x, y + 4f * unit); lineTo(x - 8f * unit, y + 8f * unit); close()
            }
            canvas.drawPath(arrow, paint)
        } finally { canvas.restoreToCount(saved) }
    }
    private fun label(canvas: Canvas, text: String, x: Float, y: Float, size: Float, maxWidth: Float) {
        if (maxWidth <= 0) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val count = paint.breakText(text, true, maxWidth - 12, null)
        val shown = text.take(count)
        val box = RectF(x - 6, y - 4, x + paint.measureText(shown) + 6, y + size + 5)
        paint.color = Color.argb(235, 255, 255, 255); canvas.drawRoundRect(box, 5f, 5f, paint)
        paint.color = Color.rgb(35, 45, 55); canvas.drawText(shown, x, y + size, paint)
    }

    @Synchronized
    override fun close() {
        synchronized(lock) { closed = true; handler.removeCallbacks(redraw) }
        tiles.close()
    }

    private inner class ViewportTiles(directory: File) : AutoCloseable {
        private val executor = Executors.newFixedThreadPool(2) { task -> Thread(task, "surpride-osm-viewport").apply { isDaemon = true } }
        private val store = TileStore(directory)
        private val monitor = Any()
        private var wanted = emptySet<TileKey>()
        private val pending = mutableSetOf<TileKey>()
        private val images = LinkedHashMap<TileKey, TileImage>(64, 0.75f, true)
        private var stopped = false
        fun peek(key: TileKey): TileImage? = synchronized(monitor) { images[key] }
        fun want(keys: Set<TileKey>) {
            synchronized(monitor) {
                if (stopped) return
                wanted = keys
                val now = System.currentTimeMillis()
                for (key in keys) {
                    val existing = images[key]
                    if (key in pending || (existing != null && (!existing.failed || now - existing.loadedAt < 30_000))) continue
                    pending.add(key)
                    executor.execute {
                        try {
                            // Recheck at worker start: queued work for old viewports never triggers a request.
                            if (!synchronized(monitor) { !stopped && key in wanted }) return@execute
                            val result = store.load(key)
                            val bitmap = result.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                            synchronized(monitor) {
                                if (!stopped) {
                                    images[key] = TileImage(bitmap, result.stale, result.failure != null || bitmap == null, System.currentTimeMillis())
                                    // Only decoded memory is bounded; persistent fresh tiles are retained.
                                    while (images.size > max(96, wanted.size * 2)) {
                                        val victim = images.keys.firstOrNull { it !in wanted } ?: break
                                        images.remove(victim)
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            synchronized(monitor) { if (!stopped) images[key] = TileImage(null, false, true, System.currentTimeMillis()) }
                        } finally {
                            synchronized(monitor) { pending.remove(key) }
                            changed()
                        }
                    }
                }
            }
        }
        override fun close() {
            synchronized(monitor) { stopped = true; wanted = emptySet(); images.clear() }
            executor.shutdownNow()
        }
    }
}
