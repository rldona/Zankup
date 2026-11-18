package com.zankup.android.routes

import androidx.lifecycle.ViewModel
import com.zankup.android.api.endpoints.TrainingApi
import com.zankup.android.api.model.SegmentOut
import com.zankup.android.data.Store
import com.zankup.android.network.call
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.serialization.builtins.ListSerializer

/** Your segments, kept on the phone and refreshed when the screen opens. */
@HiltViewModel
class SegmentsModel @Inject constructor(store: Store) : ViewModel() {
    val segments = store.query("routes/segments", ListSerializer(SegmentOut.serializer())) { api ->
        call { api.service<TrainingApi>().listSegments() }
    }
}
