package com.zankup.android.routes

import androidx.lifecycle.ViewModel
import com.zankup.android.api.endpoints.TrainingApi
import com.zankup.android.api.model.RouteGroup
import com.zankup.android.data.Store
import com.zankup.android.network.call
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.serialization.builtins.ListSerializer

/** Routes done more than once, kept on the phone and refreshed when the screen opens. */
@HiltViewModel
class RoutesModel @Inject constructor(store: Store) : ViewModel() {
    val routes = store.query("routes", ListSerializer(RouteGroup.serializer())) { api ->
        call { api.service<TrainingApi>().listRoutes() }
    }
}
