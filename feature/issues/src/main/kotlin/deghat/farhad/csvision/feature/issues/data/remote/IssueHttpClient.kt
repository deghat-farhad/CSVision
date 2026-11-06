package deghat.farhad.csvision.feature.issues.data.remote

import android.util.Log
import deghat.farhad.csvision.feature.issues.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

internal fun createIssueHttpClient(
    loggingEnabled: Boolean = BuildConfig.DEBUG,
    logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger { message ->
        Log.d("CSVisionHttp", message)
    }
): OkHttpClient {
    val builder = OkHttpClient.Builder()

    if (loggingEnabled) {
        val logging = HttpLoggingInterceptor(logger)
        logging.level = HttpLoggingInterceptor.Level.BASIC
        builder.addInterceptor(logging)
    }

    return builder.build()
}
