package com.zankup.android.map

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zankup.android.charts.ChartCursor
import com.zankup.android.charts.ChartSection
import com.zankup.android.designsystem.theme.ZankupTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** OpenFreeMap's styles (free, no key): quiet enough for the route on top to stand out. */
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

/**
 * The activity's map (OS-1132): the route, coloured by a metric over the shared ramp when [colours] has
 * stops, the [section] picked on the charts and a dot at the charts' [cursor]. The route's geometry is
 * sent once; switching the metric only restyles it, and moving the cursor only moves the dot.
 */
@Composable
fun RouteMap(
    route: Route,
    cursor: ChartCursor,
    section: ChartSection,
    modifier: Modifier = Modifier,
    colours: List<Pair<Double, Color>>? = null,
    interactive: Boolean = true
) {
    val context = LocalContext.current
    val palette = ZankupTheme.colors
    val accent = palette.accent
    val surface = palette.surface
    val view = remember { mapView(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

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

    val styleUrl = if (palette.isDark) STYLE_DARK else STYLE_LIGHT
    LaunchedEffect(view, styleUrl) {
        view.getMapAsync { m ->
            map = m
            m.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isScrollGesturesEnabled = interactive
                isZoomGesturesEnabled = interactive
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
            }
            m.setStyle(Style.Builder().fromUri(styleUrl)) { loaded ->
                addRoute(loaded, route, accent, surface)
                fit(m, route)
                style = loaded
            }
        }
    }

    // The colour: a gradient along the line, or the accent.
    LaunchedEffect(style, colours) {
        val line = style?.getLayerAs<LineLayer>(ROUTE) ?: return@LaunchedEffect
        if (colours == null) {
            line.setProperties(PropertyFactory.lineGradient(Expression.color(accent.toArgb())))
        } else {
            val stops = colours.map { (at, c) -> Expression.stop(at, Expression.color(c.toArgb())) }.toTypedArray()
            line.setProperties(
                PropertyFactory.lineGradient(
                    Expression.interpolate(Expression.linear(), Expression.lineProgress(), *stops)
                )
            )
        }
    }

    // The section and the cursor follow the charts; each change touches one small source.
    LaunchedEffect(style) {
        val loaded = style ?: return@LaunchedEffect
        snapshotFlow { section.range }.collect { range ->
            val source = loaded.getSourceAs<GeoJsonSource>(SECTION) ?: return@collect
            val points = range?.let { r ->
                route.drawn.filter { it in r }.map { Point.fromLngLat(route.lon[it], route.lat[it]) }
            }
            if (points != null && points.size >= 2) {
                source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(points)))
            } else {
                source.setGeoJson(EMPTY)
            }
        }
    }
    LaunchedEffect(style) {
        val loaded = style ?: return@LaunchedEffect
        snapshotFlow { cursor.index }.collect { index ->
            val source = loaded.getSourceAs<GeoJsonSource>(CURSOR) ?: return@collect
            val at = route.positionAt(index)
            if (index >= 0 && at != null) {
                source.setGeoJson(Feature.fromGeometry(Point.fromLngLat(route.lon[at], route.lat[at])))
            } else {
                source.setGeoJson(EMPTY)
            }
        }
    }

    AndroidView(factory = { view }, modifier = modifier)
}

private const val ROUTE = "route"
private const val SECTION = "section"
private const val CURSOR = "cursor"

/** An empty source must be an empty collection: an empty line is invalid GeoJSON and MapLibre drops it. */
private val EMPTY = FeatureCollection.fromFeatures(emptyList())

private fun mapView(context: Context): MapView {
    MapLibre.getInstance(context)
    // A TextureView, so the map scrolls with the page and sits under a see-through header.
    return MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
        onCreate(Bundle())
    }
}

private fun addRoute(style: Style, route: Route, accent: Color, halo: Color) {
    val line = LineString.fromLngLats(route.drawn.map { Point.fromLngLat(route.lon[it], route.lat[it]) })
    style.addSource(GeoJsonSource(ROUTE, Feature.fromGeometry(line), GeoJsonOptions().withLineMetrics(true)))
    style.addSource(GeoJsonSource(SECTION, EMPTY))
    style.addSource(GeoJsonSource(CURSOR, EMPTY))
    style.addLayer(
        LineLayer("$ROUTE-halo", ROUTE).withProperties(
            PropertyFactory.lineColor(halo.toArgb()),
            PropertyFactory.lineWidth(7f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer(ROUTE, ROUTE).withProperties(
            PropertyFactory.lineGradient(Expression.color(accent.toArgb())),
            PropertyFactory.lineWidth(4f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer(SECTION, SECTION).withProperties(
            PropertyFactory.lineColor(accent.toArgb()),
            PropertyFactory.lineWidth(8f),
            PropertyFactory.lineOpacity(0.55f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
        )
    )
    style.addLayer(
        CircleLayer(CURSOR, CURSOR).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor(accent.toArgb()),
            PropertyFactory.circleStrokeColor(halo.toArgb()),
            PropertyFactory.circleStrokeWidth(3f)
        )
    )
}

private fun fit(map: MapLibreMap, route: Route) {
    if (!route.hasGps) return
    val bounds = LatLngBounds.Builder()
    route.drawn.forEach { bounds.include(LatLng(route.lat[it], route.lon[it])) }
    runCatching { map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 64)) }
}
