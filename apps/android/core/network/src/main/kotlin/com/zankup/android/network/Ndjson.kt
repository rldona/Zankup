package com.zankup.android.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody

/**
 * The lines of a streamed NDJSON answer (the Coach writes its reply this way), one by one as they
 * arrive. Empty lines are skipped; the body is closed when the flow ends or is cancelled.
 */
fun ResponseBody.ndjsonLines(): Flow<String> = flow {
    use { body ->
        val source = body.source()
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isNotBlank()) emit(line)
        }
    }
}.flowOn(Dispatchers.IO)
