package deghat.farhad.csvision.feature.issues.presentation

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import deghat.farhad.csvision.feature.issues.data.DefaultIssueRepository
import deghat.farhad.csvision.feature.issues.data.IssueImportCoordinator
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import deghat.farhad.csvision.feature.issues.data.local.IssuesDatabase
import deghat.farhad.csvision.feature.issues.domain.Issue
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssuesPagingScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: IssuesDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, IssuesDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun keepsVisibleRecordsWhenOlderPagesAreDropped() {
        val dao = database.issueDao()
        runBlocking {
            dao.insertBatch((0 until 1_000).map { index ->
                IssueEntity(index.toLong(), "Person", "$index", index, 0, "")
            })
        }
        val savedIssues = DefaultIssueRepository(
            dao = dao,
            importCoordinator = IssueImportCoordinator(dao) { error("Unexpected import") }
        )
        val loadedRecords = CopyOnWriteArraySet<Long>()
        val repository = object : IssueRepository {
            override fun observeIssues(): Flow<PagingData<Issue>> = savedIssues.observeIssues().map { page ->
                page.map { issue ->
                    loadedRecords.add(issue.recordNumber)
                    issue
                }
            }

            override suspend fun reload() = error("Unexpected reload")
        }
        compose.setContent { MaterialTheme { IssuesRoute(repository) } }
        compose.waitUntil(5_000) { loadedRecords.contains(99L) }
        compose.onNode(hasScrollAction()).performScrollToIndex(75)
        compose.waitUntil(5_000) { loadedRecords.contains(149L) }
        compose.onNode(hasScrollAction()).performScrollToIndex(120)
        compose.waitUntil(5_000) { loadedRecords.contains(199L) }
        compose.onNodeWithText("Person 120").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToIndex(180)
        compose.waitUntil(5_000) { loadedRecords.contains(249L) }
        compose.onNodeWithText("Person 180").assertIsDisplayed()

        var before = visibleRecordNumbers().min()
        repeat(8) {
            if (!loadedRecords.contains(299L)) {
                compose.onNode(hasScrollAction()).performTouchInput { swipeUp(durationMillis = 1_000) }
                compose.waitForIdle()
                val visible = visibleRecordNumbers()
                val after = visible.min()
                assertTrue("One slow swipe jumped from $before to $visible", after in before..(before + 20))
                before = after
            }
        }
        assertTrue("Scrolling did not reach the eviction boundary", loadedRecords.contains(299L))
        val indexForKey = compose.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.IndexForKey]
        compose.runOnIdle {
            assertEquals("The first record should have been evicted", -1, indexForKey(0L))
            assertEquals("The visible record must retain its absolute index", before, indexForKey(before.toLong()))
        }
    }

    @Test
    fun keepsVisibleRecordsWhenBatchesArriveAfterPageEviction() {
        assertVisibleRecordsSurviveNewBatches(lastRecordToVisit = 275, jumpToRecord = false)
    }

    @Test
    fun keepsVisibleRecordsAtTheLoadedEndWhenMoreBatchesArrive() {
        assertVisibleRecordsSurviveNewBatches(lastRecordToVisit = 999, jumpToRecord = false)
    }

    @Test
    fun keepsVisibleRecordsAfterAFastJumpAndMoreBatchesArrive() {
        assertVisibleRecordsSurviveNewBatches(lastRecordToVisit = 900, jumpToRecord = true)
    }

    private fun assertVisibleRecordsSurviveNewBatches(lastRecordToVisit: Int, jumpToRecord: Boolean) {
        val dao = database.issueDao()
        fun records(start: Int, end: Int) = (start until end).map {
            IssueEntity(it.toLong(), "Person", "$it", it, 0, "")
        }
        runBlocking { dao.insertBatch(records(0, 1_000)) }
        val savedIssues = DefaultIssueRepository(
            dao = dao,
            importCoordinator = IssueImportCoordinator(dao) { error("Unexpected import") }
        )
        val generation = AtomicInteger()
        val presentedGenerations = CopyOnWriteArraySet<Int>()
        val repository = object : IssueRepository {
            override fun observeIssues(): Flow<PagingData<Issue>> = savedIssues.observeIssues().map { page ->
                val currentGeneration = generation.incrementAndGet()
                page.map { issue ->
                    presentedGenerations.add(currentGeneration)
                    issue
                }
            }

            override suspend fun reload() = error("Unexpected reload")
        }
        compose.setContent { MaterialTheme { IssuesRoute(repository) } }
        if (jumpToRecord) {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Person 0").fetchSemanticsNodes().isNotEmpty()
            }
            val generationBeforeJump = generation.get()
            compose.onNode(hasScrollAction()).performScrollToIndex(lastRecordToVisit)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Person $lastRecordToVisit").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            assertEquals(
                "Fast scrolling must not restart the paging generation",
                generationBeforeJump,
                generation.get()
            )
        } else {
            for (recordNumber in 75..lastRecordToVisit step 40) {
                scrollToRecord(recordNumber)
            }
            scrollToRecord(lastRecordToVisit)
        }
        val before = visibleRecordNumbers()
        assertTrue("The viewport must contain records", before.isNotEmpty())
        if (jumpToRecord) {
            assertEquals("The fast jump must reach the requested record", lastRecordToVisit, before.first())
        }
        val firstRecord = compose.onNodeWithText("Person ${before.first()}")
        val topBefore = firstRecord.fetchSemanticsNode().boundsInRoot.top
        assertEquals("The first page must have been evicted", -1, indexForRecord(0))

        repeat(3) { batch ->
            val previousGeneration = generation.get()
            val start = 1_000 + batch * 500
            runBlocking { dao.insertBatch(records(start, start + 500)) }
            compose.waitUntil(10_000) {
                generation.get() > previousGeneration && presentedGenerations.contains(generation.get())
            }
            compose.waitForIdle()

            assertEquals(
                "Batch ${batch + 1} changed the stopped viewport",
                before,
                visibleRecordNumbers().take(before.size)
            )
            assertEquals(
                "Batch ${batch + 1} moved the visible record",
                topBefore,
                firstRecord.fetchSemanticsNode().boundsInRoot.top,
                1f
            )
        }
    }

    private fun scrollToRecord(recordNumber: Int) {
        compose.waitUntil(10_000) { indexForRecord(recordNumber) >= 0 }
        val index = indexForRecord(recordNumber)
        compose.onNode(hasScrollAction()).performScrollToIndex(index)
        compose.waitForIdle()
    }

    private fun indexForRecord(recordNumber: Int): Int {
        val indexForKey = compose.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.IndexForKey]
        return compose.runOnIdle { indexForKey(recordNumber.toLong()) }
    }

    private fun visibleRecordNumbers(): List<Int> =
        compose.onAllNodesWithText("Person", substring = true).fetchSemanticsNodes()
            .flatMap { it.config[SemanticsProperties.Text] }
            .map { it.text.removePrefix("Person ").toInt() }
}
