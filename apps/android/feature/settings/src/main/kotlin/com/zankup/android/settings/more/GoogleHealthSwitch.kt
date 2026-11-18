package com.zankup.android.settings.more

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.network.ApiResult
import com.zankup.android.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** The instance's `google_health_enabled`: whether connecting Google Health is offered. */
@HiltViewModel
class GoogleHealthModel
@Inject
constructor(val service: ServiceSettings) : ViewModel() {
    suspend fun refresh() = service.refreshInstance()

    suspend fun set(on: Boolean) = service.patchInstance(mapOf(SETTING to JsonPrimitive(on)))

    companion object {
        const val SETTING = "google_health_enabled"
    }
}

/**
 * Settings › Health › Google Health (OS-1161): a card with the switch, shown only when the instance
 * lists the setting. Place it in the health settings' column.
 */
@Composable
fun GoogleHealthSwitch(model: GoogleHealthModel = hiltViewModel()) {
    val scope = rememberCoroutineScope()
    val instance by model.service.instance.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { model.refresh() }
    val setting = instance.data?.settings?.firstOrNull { it.name == GoogleHealthModel.SETTING } ?: return
    var failure by remember { mutableStateOf<ApiResult.Failure?>(null) }
    MoreCard(stringResource(R.string.setm_instance_googleHealthTitle)) {
        SwitchLine(stringResource(R.string.setm_instance_googleHealth), MoreLogic.flag(setting.value), { on ->
            failure = null
            scope.launch { (model.set(on) as? ApiResult.Failure)?.let { failure = it } }
        })
        failure?.let { Note(failureText(it), live = true) }
    }
}
