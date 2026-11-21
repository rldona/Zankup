package com.zankup.android.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.LruCache
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.theme.ZankupTheme
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter

/** A small snapshot cache: the same route is not rendered again when the list is scrolled. */
private val snapshots = object : LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(24) {
    override fun sizeOf(key: String, value: androidx.compose.ui.graphics.ImageBitmap) = 1
}

/**
 * A route on its real map: a MapLibre snapshot with the line drawn on top, for the route list's
 * thumbnail. The bitmap is cached in memory and on disk (so a restart does not render every route again),
 * at most two snapshotters run at once, and if the map cannot be loaded the line is drawn on the card
 * colour alone, so a route never disappears offline.
 */
@Composable
fun RouteSnapshot(
    points: List<Pair<Double, Double>>,
    colour: Color,
    modifier: Modifier = Modifier,
    width: Dp = 88.dp,
    height: Dp = 64.dp
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val palette = ZankupTheme.colors
    val style = if (palette.isDark) STYLE_DARK else STYLE_LIGHT
    val key = remember(points, colour, style, width, height, density) {
        "${points.size}:${points.hashCode()}:${colour.value}:$style:${width.value}:${height.value}:${density.density}"
    }
    var image by remember(key) { mutableStateOf(snapshots.get(key)) }
    val scope = rememberCoroutineScope()

    DisposableEffect(key) {
        if (image != null || points.size < 2) return@DisposableEffect onDispose { }
        val w = with(density) { width.roundToPx() }.coerceAtLeast(1)
        val h = with(density) { height.roundToPx() }.coerceAtLeast(1)
        val bounds = LatLngBounds.Builder()
        points.forEach { (lat, lon) -> bounds.include(LatLng(lat, lon)) }
        val options = MapSnapshotter.Options(w, h)
            .withStyle(style)
            .withRegion(bounds.build())
            .withPadding(4, 4, 4, 4)
            .withPixelRatio(density.density)
            .withLogo(false)
            .withAttribution(false)
        val job = scope.launch {
            // Disk first: a route seen before a restart does not pay for the map again.
            val kept = withContext(Dispatchers.IO) { readSnapshot(context, key) }
            if (kept != null) {
                kept.asImageBitmap().also {
                    snapshots.put(key, it)
                    image = it
                }
                return@launch
            }
            MapLibre.getInstance(context)
            val rendered = renderGate.withPermit {
                awaitSnapshot(context, options, points, colour, density.density)
            } ?: return@launch
            val bitmap = rendered.asImageBitmap()
            snapshots.put(key, bitmap)
            image = bitmap
            withContext(Dispatchers.IO) { writeSnapshot(context, key, rendered) }
        }
        onDispose { job.cancel() }
    }

    Box(modifier) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: FallbackRoute(points, colour, Modifier.fillMaxSize())
    }
}

/** At most this many snapshotters at once: a list of cards must not start one per route. */
private val renderGate = Semaphore(2)

private suspend fun awaitSnapshot(
    context: Context,
    options: MapSnapshotter.Options,
    points: List<Pair<Double, Double>>,
    colour: Color,
    density: Float
): Bitmap? = suspendCancellableCoroutine { cont ->
    val snapshotter = MapSnapshotter(context, options)
    snapshotter.start({ snapshot ->
        if (cont.isActive) cont.resume(snapshot.drawRoute(points, colour, density))
    }, { if (cont.isActive) cont.resume(null) })
    cont.invokeOnCancellation { snapshotter.cancel() }
}

private fun snapshotFile(context: Context, key: String): File =
    File(File(context.cacheDir, "route-snapshots").apply { mkdirs() }, "${key.hashCode().toString(16)}.webp")

private fun readSnapshot(context: Context, key: String): Bitmap? {
    val file = snapshotFile(context, key)
    if (!file.exists()) return null
    return runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
}

private fun writeSnapshot(context: Context, key: String, bitmap: Bitmap) {
    runCatching {
        val file = snapshotFile(context, key)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP, 90, it) }
        // A few hundred portraits are plenty; the oldest go first.
        file.parentFile?.listFiles()?.takeIf { it.size > 200 }?.sortedBy { it.lastModified() }?.let { all ->
            all.take(all.size - 200).forEach { it.delete() }
        }
    }
}

/** The route's line on the snapshot bitmap, in the snapshot's own pixels. */
private fun MapSnapshot.drawRoute(points: List<Pair<Double, Double>>, colour: Color, density: Float): Bitmap? {
    val bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: return null
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colour.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = max(2f, 2.5f * density)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val path = Path()
    var start: android.graphics.PointF? = null
    var end: android.graphics.PointF? = null
    points.forEachIndexed { i, (lat, lon) ->
        val at = pixelForLatLng(LatLng(lat, lon))
        if (i == 0) {
            path.moveTo(at.x, at.y)
            start = at
        } else {
            path.lineTo(at.x, at.y)
        }
        end = at
    }
    canvas.drawPath(path, paint)
    start?.let {
        canvas.drawCircle(it.x, it.y, max(2f, 2.5f * density), paint.apply { style = Paint.Style.FILL })
    }
    end?.let {
        canvas.drawCircle(it.x, it.y, max(2f, 2.5f * density), paint)
    }
    return bitmap
}

/** The route alone, as the app drew it before the map: the fallback when the snapshot fails. */
@Composable
private fun FallbackRoute(points: List<Pair<Double, Double>>, colour: Color, modifier: Modifier) {
    if (points.size < 2) return
    val box = remember(points) { routeBox(points) }
    ComposeCanvas(modifier) {
        val pad = 4.dp.toPx()
        val scale = min((size.width - pad * 2) / box.width, (size.height - pad * 2) / box.height)
        val ox = (size.width - box.width * scale) / 2
        val oy = (size.height - box.height * scale) / 2
        val path = ComposePath()
        points.forEachIndexed { i, (lat, lon) ->
            val x = (ox + (lon * cos(Math.toRadians(box.lat)) - box.minLon) * scale).toFloat()
            val y = (size.height - oy - (lat - box.minLat) * scale).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            colour,
            style = Stroke(max(2.dp.toPx(), 1f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        points.first().let { (lat, lon) ->
            drawCircle(
                colour,
                2.5.dp.toPx(),
                Offset(
                    (ox + (lon * cos(Math.toRadians(box.lat)) - box.minLon) * scale).toFloat(),
                    (size.height - oy - (lat - box.minLat) * scale).toFloat()
                )
            )
        }
    }
}

private class RouteBox(val minLat: Double, val minLon: Double, val width: Double, val height: Double, val lat: Double)

private fun routeBox(points: List<Pair<Double, Double>>): RouteBox {
    val lat = points.map { it.first }
    val k = cos(Math.toRadians(lat.average()))
    val xs = points.map { it.second * k }
    val minLat = lat.min()
    val minLon = xs.min()
    val width = (xs.max() - minLon).coerceAtLeast(1e-9)
    val height = (lat.max() - minLat).coerceAtLeast(1e-9)
    return RouteBox(minLat, minLon, width, height, lat.average())
}
