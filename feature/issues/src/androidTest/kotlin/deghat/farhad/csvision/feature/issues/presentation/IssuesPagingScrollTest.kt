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
            assertTrue("No older pages were dropped", indexForKey(before.toLong()) in 0 until before)
        }
    }

    private fun visibleRecordNumbers(): List<Int> =
        compose.onAllNodesWithText("Person", substring = true).fetchSemanticsNodes()
            .flatMap { it.config[SemanticsProperties.Text] }
            .map { it.text.removePrefix("Person ").toInt() }
}
