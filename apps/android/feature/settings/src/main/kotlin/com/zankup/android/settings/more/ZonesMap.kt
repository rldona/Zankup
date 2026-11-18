package com.zankup.android.settings.more

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zankup.android.api.model.PrivacyZone
import com.zankup.android.designsystem.theme.ZankupTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

// OpenFreeMap's styles (free, no key), as core:map uses.
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/positron"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
private const val ZONES = "zones"

/**
 * The privacy zones on a map: each a circle in the accent, the one being placed ([draft]) stronger. A tap
 * calls [onTap] with where it landed (lat, lon). Opens on [centre] at street level, or on the world.
 */
@Composable
internal fun ZonesMap(
    zones: List<PrivacyZone>,
    draft: PrivacyZone?,
    centre: Pair<Double, Double>?,
    onTap: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = ZankupTheme.colors
    val accent = palette.accent.toArgb()
    val view = remember { zonesView(context) }
    var style by remember { mutableStateOf<Style?>(null) }
    val tap by rememberUpdatedState(onTap)

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
        view.getMapAsync { map ->
            map.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
            }
            if (style == null) {
                map.cameraPosition =
                    CameraPosition
                        .Builder()
                        .target(centre?.let { LatLng(it.first, it.second) } ?: LatLng(20.0, 0.0))
                        .zoom(if (centre != null) 13.0 else 1.5)
                        .build()
                map.addOnMapClickListener { point ->
                    tap(point.latitude, point.longitude)
                    true
                }
            }
            map.setStyle(Style.Builder().fromUri(styleUrl)) { loaded ->
                loaded.addSource(GeoJsonSource(ZONES, FeatureCollection.fromFeatures(emptyList())))
                loaded.addLayer(
                    FillLayer("$ZONES-fill", ZONES).withProperties(
                        PropertyFactory.fillColor(accent),
                        PropertyFactory.fillOpacity(
                            Expression.switchCase(
                                Expression.eq(Expression.get("draft"), Expression.literal(1)),
                                Expression.literal(0.35f),
                                Expression.literal(0.2f)
                            )
                        )
                    )
                )
                loaded.addLayer(
                    LineLayer("$ZONES-edge", ZONES).withProperties(
                        PropertyFactory.lineColor(accent),
                        PropertyFactory.lineWidth(2f)
                    )
                )
                style = loaded
            }
        }
    }

    LaunchedEffect(style, zones, draft) {
        val source = style?.getSourceAs<GeoJsonSource>(ZONES) ?: return@LaunchedEffect
        source.setGeoJson(FeatureCollection.fromFeatures(features(zones, draft)))
    }

    AndroidView(factory = { view }, modifier = modifier)
}

private fun features(zones: List<PrivacyZone>, draft: PrivacyZone?): List<Feature> =
    (zones.map { it to 0 } + listOfNotNull(draft?.let { it to 1 })).map { (zone, isDraft) ->
        val ring = MoreLogic.circle(zone.lat, zone.lon, zone.radiusM).map { (lon, lat) -> Point.fromLngLat(lon, lat) }
        Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))).apply { addNumberProperty("draft", isDraft) }
    }

private fun zonesView(context: Context): MapView {
    MapLibre.getInstance(context)
    // A TextureView, so the map scrolls with the page.
    return MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
        onCreate(Bundle())
    }
}
