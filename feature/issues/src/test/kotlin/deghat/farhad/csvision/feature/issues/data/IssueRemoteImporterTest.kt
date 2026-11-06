package deghat.farhad.csvision.feature.issues.data

import deghat.farhad.csvision.core.csv.CsvParser
import deghat.farhad.csvision.feature.issues.data.local.IssueDao
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import deghat.farhad.csvision.feature.issues.data.remote.IssueCsvHttpSource
import deghat.farhad.csvision.feature.issues.data.remote.createIssueHttpClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okio.BufferedSink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class IssueRemoteImporterTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = createIssueHttpClient(
            loggingEnabled = true,
            logger = HttpLoggingInterceptor.Logger { }
        ).newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.close()
    }

    @Test(timeout = 15_000)
    fun savesFirstBatchBeforeDownloadFinishesAndFinalPartialBatchAtEof() {
        val firstBatchSaved = CountDownLatch(1)
        val dao = RecordingIssueDao(firstBatchSaved)
        val importer = IssueRemoteImporter(
            source = IssueCsvHttpSource(client, server.url("/issues.csv"), Dispatchers.IO),
            batchImporter = IssueBatchImporter(dao, Dispatchers.IO, batchSize = 2),
            createReader = { reader -> IssueCsvReader(CsvParser(reader), IssueMapper()) }
        )
        val firstRecords = listOf(
            "First name,Sur name,Issue count,Date of birth,avatar",
            "Theo,Jansen,5,1978-01-02T00:00:00,https://example.com/theo.png",
            "Fiona,de Vries,7,1950-11-12T00:00:00,https://example.com/fiona.png"
        ).joinToString("\n", postfix = "\n")
        val finalRecord = "Theo,Jansen,5,1978-01-02T00:00:00,https://example.com/theo.png"
        val body = object : MockResponseBody {
            override val contentLength = (firstRecords + finalRecord).toByteArray(Charsets.UTF_8).size.toLong()

            override fun writeTo(sink: BufferedSink) {
                sink.writeUtf8(firstRecords)
                sink.flush()
                check(firstBatchSaved.await(10, TimeUnit.SECONDS)) { "The first batch was not saved while streaming." }
                sink.writeUtf8(finalRecord)
            }
        }
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/csv; charset=utf-8")
                .body(body)
                .build()
        )

        try {
            runBlocking { importer.importIssues() }
        } finally {
            firstBatchSaved.countDown()
        }

        val theo = IssueEntity(
            recordNumber = 2,
            firstName = "Theo",
            surname = "Jansen",
            issueCount = 5,
            dateOfBirthEpochDay = 2_923,
            avatarUrl = "https://example.com/theo.png"
        )
        val fiona = IssueEntity(
            recordNumber = 3,
            firstName = "Fiona",
            surname = "de Vries",
            issueCount = 7,
            dateOfBirthEpochDay = -6_990,
            avatarUrl = "https://example.com/fiona.png"
        )
        assertEquals(
            listOf(listOf(theo, fiona), listOf(theo.copy(recordNumber = 4))),
            dao.batches
        )
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/issues.csv", request.url.encodedPath)
    }

    private class RecordingIssueDao(private val firstBatchSaved: CountDownLatch) : IssueDao {
        val batches = mutableListOf<List<IssueEntity>>()

        override suspend fun insertBatch(issues: List<IssueEntity>) {
            batches.add(issues)
            firstBatchSaved.countDown()
        }

        override suspend fun clear() {
            error("The importer must not clear the working dataset.")
        }
    }
}
