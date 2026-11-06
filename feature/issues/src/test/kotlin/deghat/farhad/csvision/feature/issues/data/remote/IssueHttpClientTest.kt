package deghat.farhad.csvision.feature.issues.data.remote

import deghat.farhad.csvision.feature.issues.BuildConfig
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueHttpClientTest {
    @Test
    fun enablesOnlyBasicLoggingWhenRequested() {
        for (enabled in listOf(true, false)) {
            val client = createIssueHttpClient(
                loggingEnabled = enabled,
                logger = HttpLoggingInterceptor.Logger { }
            )
            val loggingInterceptors = client.interceptors.filterIsInstance<HttpLoggingInterceptor>()

            if (enabled) {
                assertEquals(HttpLoggingInterceptor.Level.BASIC, loggingInterceptors.single().level)
            } else {
                assertTrue(loggingInterceptors.isEmpty())
            }
        }
    }

    @Test
    fun defaultLoggingFollowsBuildType() {
        val client = createIssueHttpClient(logger = HttpLoggingInterceptor.Logger { })

        assertEquals(BuildConfig.DEBUG, client.interceptors.any { it is HttpLoggingInterceptor })
    }
}
