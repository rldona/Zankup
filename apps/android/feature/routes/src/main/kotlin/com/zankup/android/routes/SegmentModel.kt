package com.zankup.android.routes

import androidx.lifecycle.ViewModel
import com.zankup.android.api.endpoints.TrainingApi
import com.zankup.android.api.model.SegmentDetail
import com.zankup.android.data.Query
import com.zankup.android.data.Store
import com.zankup.android.network.ApiResult
import com.zankup.android.network.call
import com.zankup.android.session.ApiSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** One segment, its efforts and deleting it. */
@HiltViewModel
class SegmentModel @Inject constructor(private val store: Store, private val source: ApiSource) : ViewModel() {
    private var id = -1L

    lateinit var segment: Query<SegmentDetail>
        private set

    fun open(id: Long) {
        if (this.id == id) return
        this.id = id
        segment = store.query("routes/segments/$id", SegmentDetail.serializer()) { api ->
            call { api.service<TrainingApi>().getSegment(id.toInt()) }
        }
    }

    /** Deletes the segment; true when the server did. */
    suspend fun delete(): Boolean {
        val api = source.api ?: return false
        return call { api.service<TrainingApi>().deleteSegment(id.toInt()) } is ApiResult.Ok
    }
}
