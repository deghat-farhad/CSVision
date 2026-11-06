package deghat.farhad.csvision.feature.issues.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.testing.asPagingSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import deghat.farhad.csvision.feature.issues.domain.Issue
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssuesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun displaysPagedDomainIssuesThroughViewModelAndPreservesDuplicates() {
        val theo = Issue(2, "Theo", "Jansen", 5, LocalDate.of(1978, 1, 2), "https://example.com/theo.png")
        val fiona = Issue(3, "Fiona", "de Vries", 7, LocalDate.of(1950, 11, 12), "https://example.com/fiona.png")
        val repository = FakeIssueRepository(listOf(theo, fiona, theo.copy(recordNumber = 4)))

        showIssues(repository)

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Theo Jansen").fetchSemanticsNodes().size == 2
        }
        compose.onNodeWithText("Issues").assertIsDisplayed()
        compose.onNodeWithText("Fiona de Vries").assertIsDisplayed()
        compose.onNodeWithText("Issue count: 7").assertIsDisplayed()
        compose.onNodeWithText("Date of birth: 1950-11-12").assertIsDisplayed()
        compose.onAllNodesWithText("Theo Jansen").assertCountEquals(2)
        assertEquals(1, repository.observationCount)
    }

    @Test
    fun showsImportedRowsWhileLoadingAndAllowsReloadAfterSuccess() {
        val finishImport = CompletableDeferred<Unit>()
        val theo = Issue(2, "Theo", "Jansen", 5, LocalDate.of(1978, 1, 2), "")
        val repository = LoadingIssueRepository {
            records.value = listOf(theo)
            finishImport.await()
        }
        showIssues(repository)

        try {
            compose.onNodeWithText("Load issues").performClick()
            waitForText("Theo Jansen")
            compose.onNodeWithText("Loading…").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Loading issues").assertIsDisplayed()
            assertEquals(1, repository.reloadCount)

            compose.runOnIdle { finishImport.complete(Unit) }
            waitForText("Reload")
            compose.onNodeWithText("Reload").assertIsEnabled()
            compose.onNodeWithContentDescription("Loading issues").assertDoesNotExist()
            compose.onNodeWithText("Reload").performClick()
            compose.waitUntil(5_000) { repository.reloadCount == 2 }
            compose.onNodeWithText("Reload").assertIsEnabled()
        } finally {
            finishImport.complete(Unit)
        }
    }

    @Test
    fun warnsAboutIncompleteRowsAfterFailureAndAllowsFreshRetry() {
        val finishRetry = CompletableDeferred<Unit>()
        val theo = Issue(2, "Theo", "Jansen", 5, LocalDate.of(1978, 1, 2), "")
        val fiona = Issue(3, "Fiona", "de Vries", 7, LocalDate.of(1950, 11, 12), "")
        val repository = LoadingIssueRepository {
            records.value = emptyList()
            if (reloadCount == 1) {
                records.value = listOf(theo)
                throw IOException("Download interrupted")
            }
            finishRetry.await()
            records.value = listOf(fiona)
        }
        showIssues(repository)

        try {
            compose.onNodeWithText("Load issues").performClick()
            waitForText("Retry")
            waitForText("Theo Jansen")
            compose.onNodeWithText("Loading failed. Any displayed records may be incomplete.").assertIsDisplayed()
            compose.onNodeWithContentDescription("Loading issues").assertDoesNotExist()
            compose.onNodeWithText("Retry").assertIsEnabled().performClick()

            waitForText("Loading…")
            compose.onNodeWithText("Loading…").assertIsNotEnabled()
            compose.onNodeWithText("Loading failed. Any displayed records may be incomplete.").assertDoesNotExist()
            compose.onNodeWithText("Theo Jansen").assertDoesNotExist()
            compose.onNodeWithContentDescription("Loading issues").assertIsDisplayed()
            assertEquals(2, repository.reloadCount)

            compose.runOnIdle { finishRetry.complete(Unit) }
            waitForText("Fiona de Vries")
            compose.onNodeWithText("Reload").assertIsEnabled()
            compose.onNodeWithText("Theo Jansen").assertDoesNotExist()
            compose.onNodeWithText("Loading failed. Any displayed records may be incomplete.").assertDoesNotExist()
        } finally {
            finishRetry.complete(Unit)
        }
    }

    private fun showIssues(repository: IssueRepository) {
        compose.setContent {
            MaterialTheme {
                IssuesRoute(repository)
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class LoadingIssueRepository(
        private val importIssues: suspend LoadingIssueRepository.() -> Unit
    ) : IssueRepository {
        val records = MutableStateFlow<List<Issue>>(emptyList())
        var reloadCount = 0
            private set

        override fun observeIssues(): Flow<PagingData<Issue>> = records.map { PagingData.from(it) }

        override suspend fun reload() {
            reloadCount++
            importIssues()
        }
    }

    private class FakeIssueRepository(issues: List<Issue>) : IssueRepository {
        var observationCount = 0
            private set
        private val sourceFactory = issues.asPagingSourceFactory()

        override fun observeIssues(): Flow<PagingData<Issue>> {
            observationCount++
            return Pager(
                config = PagingConfig(
                    pageSize = 2,
                    initialLoadSize = 2,
                    prefetchDistance = 1,
                    enablePlaceholders = false,
                    maxSize = 6
                ),
                pagingSourceFactory = { sourceFactory() }
            ).flow
        }

        override suspend fun reload() {
            error("Showing saved issues must not trigger a reload.")
        }
    }
}
