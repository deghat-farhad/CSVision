package deghat.farhad.csvision.feature.issues.data.remote

import java.io.IOException
import java.io.Reader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Request

internal class IssueCsvHttpSource(
    private val callFactory: Call.Factory,
    private val url: HttpUrl,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun <T> withReader(consume: suspend (Reader) -> T): T = coroutineScope {
        val call = callFactory.newCall(Request.Builder().url(url).build())

        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }

            launch(ioDispatcher) {
                try {
                    val result = call.execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code} while downloading issues CSV.")
                        }

                        response.body.charStream().use { reader ->
                            consume(reader)
                        }
                    }
                    continuation.resume(result)
                } catch (failure: Throwable) {
                    continuation.resumeWithException(failure)
                }
            }
        }
    }
}
