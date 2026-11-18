package com.zankup.android.data

import com.zankup.android.network.ApiResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the instance answers. No coverage, the server down or its edge turning requests away (429,
 * 5xx) make it unreachable: screens keep what they show and a notice says so, until any request goes
 * through again.
 */
@Singleton
class Reachability
@Inject
constructor() {
    private val state = MutableStateFlow(false)
    val unreachable: StateFlow<Boolean> = state.asStateFlow()

    fun note(result: ApiResult<*>) {
        when {
            result is ApiResult.Ok -> state.value = false
            isUnreachable(result) -> state.value = true
        }
    }

    companion object {
        /** A failure that says nothing about the request itself: no network, or the server not answering. */
        fun isUnreachable(result: ApiResult<*>) =
            result is ApiResult.Offline || (result is ApiResult.Http && (result.code == 429 || result.code >= 500))
    }
}
