package deghat.farhad.csvision.feature.issues.presentation

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.paging.PagingData
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import deghat.farhad.csvision.feature.issues.data.DefaultIssueRepository
import deghat.farhad.csvision.feature.issues.data.IssueImportCoordinator
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import deghat.farhad.csvision.feature.issues.data.local.IssuesDatabase
import deghat.farhad.csvision.feature.issues.domain.Issue
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssuesContinuousScrollTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: IssuesDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            IssuesDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun settlesOnRealRecordsAfterRapidScrollingDuringContinuousBatchImports() {
        val dao = database.issueDao()
        fun records(start: Int) = (start until start + 500).map {
            IssueEntity(it.toLong(), "Person", "$it", it, 0, "")
        }
        runBlocking {
            dao.insertBatch(records(0))
            dao.insertBatch(records(500))
        }
        val saved = DefaultIssueRepository(dao, IssueImportCoordinator(dao) { error("Unexpected import") })
        val generations = AtomicInteger()
        val repository = object : IssueRepository {
            override fun observeIssues(): Flow<PagingData<Issue>> = saved.observeIssues().onEach {
                generations.incrementAndGet()
            }

            override suspend fun reload() = error("Unexpected reload")
        }
        compose.setContent { MaterialTheme { IssuesRoute(repository) } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Person 0").fetchSemanticsNodes().isNotEmpty()
        }
        val requests = Channel<Int>(Channel.UNLIMITED)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val import = scope.async {
            var nextRecord = 1_000
            for (batchCount in requests) {
                repeat(batchCount) {
                    dao.insertBatch(records(nextRecord))
                    nextRecord += 500
                }
            }
        }
        try {
            repeat(6) { swipe ->
                requests.trySend(20).getOrThrow()
                compose.onNode(hasScrollAction()).performTouchInput {
                    if (swipe % 2 == 0) swipeUp(durationMillis = 60) else swipeDown(durationMillis = 60)
                    advanceEventTime(120)
                    down(center)
                    advanceEventTime(50)
                    up()
                }
            }
            requests.close()
            runBlocking { withTimeout(30_000) { import.await() } }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("Person", substring = true).fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("Loading record…").fetchSemanticsNodes().isEmpty()
            }
            val settledGeneration = generations.get()
            repeat(30) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                assertTrue(
                    "The viewport became empty after import; generations=$settledGeneration->${generations.get()}",
                    compose.onAllNodesWithText("Person", substring = true).fetchSemanticsNodes().isNotEmpty()
                )
            }
        } finally {
            requests.close()
            scope.cancel()
            runBlocking { import.join() }
        }
    }
}
