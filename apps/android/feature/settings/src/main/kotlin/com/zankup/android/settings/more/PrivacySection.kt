package com.zankup.android.settings.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.PrivacyZone
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.network.ApiResult
import com.zankup.android.settings.R
import com.zankup.android.ui.decodePolyline
import kotlinx.coroutines.launch

/**
 * Settings › Privacy (OS-1161): places hidden from anything shared. Tap the map to place one, pick its
 * radius and name, and add it; the list below removes them. A body for the section screen.
 */
@Composable
fun PrivacySection(model: PrivacyModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    val settings by model.settings.state.collectAsStateWithLifecycle(initialValue = null)
    val latest by model.latest.state.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(Unit) { model.refresh() }
    var draft by remember { mutableStateOf<PrivacyZone?>(null) }
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableIntStateOf(400) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    val prefs = settings?.data
    if (prefs == null) {
        if (settings?.failure != null) {
            ErrorState(
                onRetry = { scope.launch { model.refresh() } },
                offline = settings?.failure is ApiResult.Offline
            )
        } else {
            Skeleton(Modifier.fillMaxWidth(), height = 300.dp)
        }
        return
    }
    val zones = prefs.privacyZones
    val routeStart = remember(latest?.data) {
        latest?.data?.items?.firstNotNullOfOrNull { it.route }?.let { decodePolyline(it).firstOrNull() }
    }
    val centre = MoreLogic.centre(zones.map { it.lat to it.lon }, routeStart)
    val ready = centre != null || latest?.data != null || latest?.failure != null
    fun save(next: List<PrivacyZone>, done: () -> Unit = {}) {
        busy = true
        failure = null
        scope.launch {
            when (val r = model.save(next)) {
                is ApiResult.Ok -> done()
                is ApiResult.Failure -> failure = r
            }
            busy = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MoreCard(stringResource(R.string.setm_privacy_title)) {
            Note(stringResource(R.string.setm_privacy_help))
            if (ready) {
                ZonesMap(
                    zones = zones,
                    draft = draft,
                    centre = centre,
                    onTap = { lat, lon ->
                        draft =
                            PrivacyZone(MoreLogic.zoneId(System.currentTimeMillis()), lat, lon, radius.toDouble(), name)
                    },
                    modifier = Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.medium)
                )
            } else {
                Skeleton(Modifier.fillMaxWidth(), height = 300.dp)
            }
            Note(stringResource(R.string.setm_privacy_tap))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                label = { Text(stringResource(R.string.setm_privacy_name)) },
                placeholder = { Text(stringResource(R.string.setm_privacy_namePlaceholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Note(stringResource(R.string.setm_privacy_radius))
            ChipPicker(
                options = MoreLogic.RADII,
                selected = radius,
                label = { MoreLogic.radiusText(it.toDouble()) },
                onSelect = { r ->
                    radius = r
                    draft = draft?.copy(radiusM = r.toDouble())
                },
                accent = true
            )
            Button(enabled = draft != null && !busy, onClick = {
                val zone = draft ?: return@Button
                save(zones + zone.copy(name = name.trim())) {
                    draft = null
                    name = ""
                }
            }) { Text(stringResource(R.string.setm_privacy_add)) }
            failure?.let { Note(failureText(it), live = true) }
        }
        if (zones.isNotEmpty()) {
            MoreCard(null) {
                zones.forEach { zone ->
                    ItemRow(
                        zone.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.setm_privacy_unnamed),
                        MoreLogic.radiusText(zone.radiusM)
                    ) {
                        OutlinedButton(enabled = !busy, onClick = { save(zones.filter { it.id != zone.id }) }) {
                            Text(stringResource(R.string.setm_privacy_remove))
                        }
                    }
                }
            }
        }
    }
}
