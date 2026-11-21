package com.zankup.android.map

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zankup.android.designsystem.theme.ZankupTheme
import kotlin.math.cos
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Many lines on one map (the heatmap): each a see-through stroke of [colour], so where they overlap
 * reads hotter. Fitted to all of them, leaving [top] and [bottom] free for what sits over the map.
 * Lines are (lat, lon) pairs; those with fewer than two points are left out.
 */
@Composable
fun LinesMap(
    lines: List<List<Pair<Double, Double>>>,
    colour: Color,
    modifier: Modifier = Modifier,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp
) {
    val context = LocalContext.current
    val palette = ZankupTheme.colors
    val density = LocalDensity.current
    val view = remember { linesView(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var ready by remember { mutableStateOf(false) }
    SideEffect { view.setBackgroundColor(palette.page.toArgb()) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, view) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> view.onStart()
                    Lifecycle.Event.ON_RESUME -> view.onResume()
                    Lifecycle.Event.ON_PAUSE -> view.onPause()
                    Lifecycle.Event.ON_STOP -> view.onStop()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }

    DisposableEffect(view) {
        val listener = MapView.OnDidFinishRenderingMapListener { fully -> if (fully) ready = true }
        view.addOnDidFinishRenderingMapListener(listener)
        onDispose { view.removeOnDidFinishRenderingMapListener(listener) }
    }

    val styleUrl = if (palette.isDark) STYLE_DARK else STYLE_LIGHT
    LaunchedEffect(view, styleUrl) {
        view.getMapAsync { m ->
            m.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
            }
            m.setStyle(Style.Builder().fromUri(styleUrl)) { loaded ->
                loaded.addSource(GeoJsonSource(LINES, FeatureCollection.fromFeatures(emptyList())))
                loaded.addLayer(
                    LineLayer(LINES, LINES).withProperties(
                        PropertyFactory.lineColor(colour.toArgb()),
                        PropertyFactory.lineOpacity(0.25f),
                        PropertyFactory.lineWidth(
                            Expression.interpolate(
                                Expression.linear(),
                                Expression.zoom(),
                                Expression.stop(8, 1f),
                                Expression.stop(15, 3f)
                            )
                        ),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                    )
                )
                map = m
                style = loaded
            }
        }
    }

    // New lines (another sport): one source update and the camera fitted to them.
    LaunchedEffect(style, lines) {
        val loaded = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val kept = lines.filter { it.size >= 2 }
        val features = kept.map { line ->
            Feature.fromGeometry(LineString.fromLngLats(line.map { (lat, lon) -> Point.fromLngLat(lon, lat) }))
        }
        loaded.getSourceAs<GeoJsonSource>(LINES)?.setGeoJson(FeatureCollection.fromFeatures(features))
        val all = focusLines(kept).flatten()
        if (all.size < 2) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().includes(all.map { (lat, lon) -> LatLng(lat, lon) }).build()
        val side = with(density) { 24.dp.roundToPx() }
        m.moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds,
                side,
                with(density) { top.roundToPx() } + side,
                side,
                with(density) { bottom.roundToPx() } + side
            )
        )
    }

    Box(modifier.background(palette.page)) {
        AndroidView(
            factory = { view },
            modifier = Modifier.fillMaxSize().alpha(if (ready) 1f else 0f)
        )
    }
}

private const val LINES = "lines"

/** The routes whose centre is within 40 km of the median centre, as React Native's heatmap focuses. */
private fun focusLines(lines: List<List<Pair<Double, Double>>>): List<List<Pair<Double, Double>>> {
    if (lines.size < 3) return lines
    val centres = lines.map { line ->
        line.sumOf { it.first } / line.size to line.sumOf { it.second } / line.size
    }
    val lon = centres.map { it.second }.sorted()[centres.size / 2]
    val lat = centres.map { it.first }.sorted()[centres.size / 2]
    val near = lines.filterIndexed { i, _ ->
        val (lineLat, lineLon) = centres[i]
        val dx = (lineLon - lon) * 111 * cos(Math.toRadians(lat))
        val dy = (lineLat - lat) * 111
        kotlin.math.hypot(dx, dy) <= 40
    }
    return near.ifEmpty { lines }
}

private fun linesView(context: Context): MapView {
    MapLibre.getInstance(context)
    return MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
        onCreate(Bundle())
    }
}
