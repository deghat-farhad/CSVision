package deghat.farhad.csvision.feature.issues.presentation

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import deghat.farhad.csvision.core.csv.CsvParser
import deghat.farhad.csvision.feature.issues.data.DefaultIssueRepository
import deghat.farhad.csvision.feature.issues.data.IssueBatchImporter
import deghat.farhad.csvision.feature.issues.data.IssueCsvReader
import deghat.farhad.csvision.feature.issues.data.IssueImportCoordinator
import deghat.farhad.csvision.feature.issues.data.IssueMapper
import deghat.farhad.csvision.feature.issues.data.IssueRemoteImporter
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import deghat.farhad.csvision.feature.issues.data.local.IssuesDatabase
import deghat.farhad.csvision.feature.issues.data.remote.IssueCsvHttpSource
import deghat.farhad.csvision.feature.issues.data.remote.createIssueHttpClient
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssuesLoadingIntegrationTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var database: IssuesDatabase
    private lateinit var repository: IssueRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = createIssueHttpClient()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, IssuesDatabase::class.java).build()
        val dao = database.issueDao()
        val importer = IssueRemoteImporter(
            source = IssueCsvHttpSource(client, server.url("/issues.csv"), Dispatchers.IO),
            batchImporter = IssueBatchImporter(dao, Dispatchers.IO),
            createReader = { reader -> IssueCsvReader(CsvParser(reader), IssueMapper()) }
        )
        repository = DefaultIssueRepository(dao, IssueImportCoordinator(dao, importer::importIssues))
    }

    @After
    fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.close()
        database.close()
    }

    @Test
    fun downloadsCsvAndDisplaysTypedRecordsWithoutMergingDuplicates() {
        enqueueCsv()
        showIssues()
        assertEquals(0, server.requestCount)

        compose.onNodeWithText("Load issues").assertIsEnabled().performClick()

        assertImportedRecords()
        assertGetRequest()
        assertEquals(1, server.requestCount)
    }

    @Test
    fun showsDownloadFailureAndImportsFreshRecordsWhenRetried() {
        runBlocking {
            database.issueDao().insertBatch(listOf(IssueEntity(99, "Previous", "Import", 1, 0, "")))
        }
        server.enqueue(MockResponse(code = 503, body = "Unavailable"))
        enqueueCsv()
        showIssues()
        waitForText("Previous Import")
        assertEquals(0, server.requestCount)

        compose.onNodeWithText("Reload").performClick()

        waitForText("Retry")
        compose.onNodeWithText("Loading failed. Any displayed records may be incomplete.").assertIsDisplayed()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Previous Import").fetchSemanticsNodes().isEmpty()
        }
        assertGetRequest()
        assertEquals(1, server.requestCount)

        compose.onNodeWithText("Retry").assertIsEnabled().performClick()

        assertImportedRecords()
        compose.onNodeWithText("Previous Import").assertDoesNotExist()
        assertGetRequest()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun keepsLoadedRowsVisibleWhileReloadStreamsManyBatches() {
        runBlocking {
            database.issueDao().insertBatch((0 until 3_000).map {
                IssueEntity(it.toLong(), "Previous", "$it", it, 0, "")
            })
        }
        val csv = buildString {
            appendLine("First name,Sur name,Issue count,Date of birth,avatar")
            repeat(30_000) {
                appendLine("Person,$it,5,1978-01-02T00:00:00,https://example.com/avatar.png")
            }
        }
        server.enqueue(
            MockResponse.Builder()
                .body(csv)
                .throttleBody(16_384, 50, TimeUnit.MILLISECONDS)
                .build()
        )
        val completed = AtomicBoolean()
        val saved = repository
        repository = object : IssueRepository by saved {
            override suspend fun reload() {
                try {
                    saved.reload()
                } finally {
                    completed.set(true)
                }
            }
        }
        showIssues()
        waitForText("Previous 0")
        compose.onNode(hasScrollAction()).performScrollToIndex(900)
        waitForText("Previous 900")
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        waitForText("Previous 0")
        val list = compose.onNode(hasScrollAction()).fetchSemanticsNode()

        compose.onNodeWithText("Reload").performClick()

        var sawRecords = false
        var sampledFrames = 0
        var placeholderFrames = 0
        compose.waitUntil(30_000) {
            compose.runOnUiThread {
                val texts = visibleTexts(list, list)
                if (texts.any { it.startsWith("Person ") }) sawRecords = true
                if (sawRecords) {
                    sampledFrames++
                    if (texts.contains("Loading record…")) placeholderFrames++
                }
            }
            completed.get()
        }
        waitForText("Reload")
        waitForText("Person 0")
        assertTrue("The test must observe loaded records during import", sawRecords && sampledFrames > 5)
        assertEquals("Loaded rows blinked back to placeholders in $sampledFrames sampled frames", 0, placeholderFrames)
        assertGetRequest()
        assertEquals(1, server.requestCount)
    }

    private fun visibleTexts(node: SemanticsNode, list: SemanticsNode): List<String> {
        val bounds = node.boundsInRoot
        val viewport = list.boundsInRoot
        if (bounds.bottom <= viewport.top || bounds.top >= viewport.bottom) return emptyList()
        val own = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        return own + node.children.flatMap { visibleTexts(it, list) }
    }

    private fun showIssues() {
        compose.setContent { MaterialTheme { IssuesRoute(repository) } }
    }

    private fun enqueueCsv() {
        val csv = listOf(
            "\"First name\",\"Sur name\",\"Issue count\",\"Date of birth\",\"avatar\"",
            "\"Théo, \"\"TJ\"\"\",Jansen,005,1978-01-02T00:00:00,https://example.com/theo.png",
            "Fiona,de Vries,7,1950-11-12T00:00:00,https://example.com/fiona.png",
            "\"Théo, \"\"TJ\"\"\",Jansen,005,1978-01-02T00:00:00,https://example.com/theo.png"
        ).joinToString("\r\n", prefix = "\uFEFF", postfix = "\r\n")
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "text/csv; charset=utf-8")
                .body(csv)
                .build()
        )
    }

    private fun assertImportedRecords() {
        waitForText("Reload")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Théo, \"TJ\" Jansen").fetchSemanticsNodes().size == 2
        }
        compose.onAllNodesWithText("Théo, \"TJ\" Jansen").assertCountEquals(2)
        compose.onAllNodesWithText("Issue count: 5").assertCountEquals(2)
        compose.onAllNodesWithText("Date of birth: 1978-01-02").assertCountEquals(2)
        compose.onNodeWithText("Fiona de Vries").assertIsDisplayed()
        compose.onNodeWithText("Issue count: 7").assertIsDisplayed()
        compose.onNodeWithText("Date of birth: 1950-11-12").assertIsDisplayed()
        compose.onNodeWithText("Reload").assertIsEnabled()
        compose.onNodeWithText("Loading failed. Any displayed records may be incomplete.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Loading issues").assertDoesNotExist()

        val theo = IssueEntity(2, "Théo, \"TJ\"", "Jansen", 5, 2_923, "https://example.com/theo.png")
        val fiona = IssueEntity(3, "Fiona", "de Vries", 7, -6_990, "https://example.com/fiona.png")
        assertEquals(listOf(theo, fiona, theo.copy(recordNumber = 4)), readSavedIssues())
    }

    private fun readSavedIssues(): List<IssueEntity> = runBlocking {
        val source = database.issueDao().pagingSource()
        try {
            val page = source.load(
                PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false)
            ) as PagingSource.LoadResult.Page<Int, IssueEntity>
            page.data
        } finally {
            source.invalidate()
        }
    }

    private fun assertGetRequest() {
        val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "Expected a CSV download request." }
        assertEquals("GET", request.method)
        assertEquals("/issues.csv", request.url.encodedPath)
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
