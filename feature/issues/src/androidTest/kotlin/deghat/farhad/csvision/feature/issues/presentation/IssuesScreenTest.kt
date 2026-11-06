package deghat.farhad.csvision.feature.issues.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.testing.asPagingSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import deghat.farhad.csvision.feature.issues.domain.Issue
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
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

        compose.setContent {
            MaterialTheme {
                IssuesRoute(repository)
            }
        }

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
