package deghat.farhad.csvision.feature.issues.data.remote

import deghat.farhad.csvision.core.csv.CsvParser
import java.io.IOException
import java.io.Reader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IssueCsvHttpSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var source: IssueCsvHttpSource
    private val logMessages = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = createIssueHttpClient(
            loggingEnabled = true,
            logger = HttpLoggingInterceptor.Logger { message -> logMessages.add(message) }
        ).newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
        source = IssueCsvHttpSource(client, server.url("/issues.csv"), Dispatchers.IO)
    }

    @After
    fun tearDown() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.close()
    }

    @Test(timeout = 15_000)
    fun readsFirstRecordBeforeRemainingResponseIsAvailable() {
        val releaseRemainingBody = CountDownLatch(1)
        server.enqueue(gatedResponse(releaseRemainingBody))
        lateinit var suppliedReader: Reader

        try {
            runBlocking {
                source.withReader { reader ->
                    suppliedReader = reader
                    val parser = CsvParser(reader)

                    assertEquals(listOf("Theo", "Jansen", "5"), parser.readRecord())
                    releaseRemainingBody.countDown()
                    assertEquals(listOf("Fiona", "de Vries", "7"), parser.readRecord())
                    assertNull(parser.readRecord())
                }
            }
        } finally {
            releaseRemainingBody.countDown()
        }

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/issues.csv", request.url.encodedPath)
        assertThrows(IOException::class.java) { suppliedReader.read() }
        assertTrue(logMessages.any { it.startsWith("--> GET ") && it.contains("/issues.csv") })
        assertTrue(logMessages.any { it.startsWith("<-- 200 ") })
        assertTrue(logMessages.none {
            it.contains("Theo") || it.contains("Fiona") || it.contains("Content-Type")
        })
    }

    @Test(timeout = 15_000)
    fun rejectsUnsuccessfulHttpResponses() {
        server.enqueue(MockResponse(code = 503, body = "Unavailable"))

        val error = assertThrows(IOException::class.java) {
            runBlocking {
                source.withReader { error("An unsuccessful response must not reach the consumer.") }
            }
        }

        assertEquals("HTTP 503 while downloading issues CSV.", error.message)
        assertTrue(logMessages.any { it.startsWith("<-- 503 ") })
    }

    @Test(timeout = 15_000)
    fun closesReaderWhenConsumerFails() {
        server.enqueue(MockResponse(body = "Theo,Jansen,5"))
        val failure = IllegalStateException("Consumer failed.")
        lateinit var suppliedReader: Reader

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                source.withReader { reader ->
                    suppliedReader = reader
                    throw failure
                }
            }
        }

        assertEquals(failure.message, error.message)
        assertThrows(IOException::class.java) { suppliedReader.read() }
    }

    @Test(timeout = 15_000)
    fun cancellationStopsReadingBlockedResponseBody() {
        val releaseRemainingBody = CountDownLatch(1)
        server.enqueue(gatedResponse(releaseRemainingBody))
        lateinit var suppliedReader: Reader

        runBlocking {
            val firstRecordRead = CompletableDeferred<Unit>()
            val operation = launch {
                source.withReader { reader ->
                    suppliedReader = reader
                    val parser = CsvParser(reader)
                    assertEquals(listOf("Theo", "Jansen", "5"), parser.readRecord())
                    firstRecordRead.complete(Unit)
                    parser.readRecord()
                }
            }

            try {
                withTimeout(5_000) {
                    firstRecordRead.await()
                    operation.cancelAndJoin()
                }
            } finally {
                releaseRemainingBody.countDown()
            }

            assertTrue(operation.isCancelled)
        }

        assertThrows(IOException::class.java) { suppliedReader.read() }
    }

    private fun gatedResponse(releaseRemainingBody: CountDownLatch): MockResponse {
        val firstRecord = "Theo,Jansen,5\n"
        val remainingRecord = "Fiona,de Vries,7"
        val body = object : MockResponseBody {
            override val contentLength = (firstRecord + remainingRecord).toByteArray(Charsets.UTF_8).size.toLong()

            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8(firstRecord)
                sink.flush()
                check(releaseRemainingBody.await(10, TimeUnit.SECONDS)) { "The consumer did not release the remaining body." }
                sink.writeUtf8(remainingRecord)
            }
        }

        return MockResponse.Builder()
            .addHeader("Content-Type", "text/csv; charset=utf-8")
            .body(body)
            .build()
    }
}
