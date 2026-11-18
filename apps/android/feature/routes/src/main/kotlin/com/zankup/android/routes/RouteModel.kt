package com.zankup.android.routes

import androidx.lifecycle.ViewModel
import com.zankup.android.api.endpoints.TrainingApi
import com.zankup.android.api.model.RouteDetail
import com.zankup.android.data.Query
import com.zankup.android.data.Store
import com.zankup.android.network.call
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** One route and every time it was done. */
@HiltViewModel
class RouteModel @Inject constructor(private val store: Store) : ViewModel() {
    private var id = -1L

    lateinit var route: Query<RouteDetail>
        private set

    fun open(id: Long) {
        if (this.id == id) return
        this.id = id
        route = store.query("routes/$id", RouteDetail.serializer()) { api ->
            call { api.service<TrainingApi>().getRoute(id.toInt()) }
        }
    }
}
