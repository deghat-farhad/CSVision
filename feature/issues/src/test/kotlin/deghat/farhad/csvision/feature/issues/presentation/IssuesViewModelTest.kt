package deghat.farhad.csvision.feature.issues.presentation

import androidx.lifecycle.ViewModelStore
import androidx.paging.PagingData
import deghat.farhad.csvision.feature.issues.domain.Issue
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IssuesViewModelTest {
    private val store = ViewModelStore()

    @Before
    fun replaceMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun ignoresRepeatedLoadsUntilTheCurrentImportFinishes() = runTest {
        val finishImport = CompletableDeferred<Unit>()
        var reloadCount = 0
        val viewModel = createViewModel {
            reloadCount++
            finishImport.await()
        }

        viewModel.loadIssues()
        viewModel.loadIssues()
        assertEquals(IssuesUiState(isLoading = true), viewModel.uiState.value)
        runCurrent()
        assertEquals(1, reloadCount)

        finishImport.complete(Unit)
        runCurrent()
        assertEquals(IssuesUiState(), viewModel.uiState.value)

        viewModel.loadIssues()
        runCurrent()
        assertEquals(2, reloadCount)
        assertEquals(IssuesUiState(), viewModel.uiState.value)
    }

    @Test
    fun clearingViewModelCancelsImportWithoutReportingFailure() = runTest {
        var importCancelled = false
        val viewModel = createViewModel {
            try {
                awaitCancellation()
            } finally {
                importCancelled = true
            }
        }
        viewModel.loadIssues()
        runCurrent()
        assertEquals(IssuesUiState(isLoading = true), viewModel.uiState.value)

        store.clear()
        runCurrent()

        assertTrue(importCancelled)
        assertEquals(IssuesUiState(), viewModel.uiState.value)
    }

    private fun createViewModel(importIssues: suspend () -> Unit): IssuesViewModel {
        val repository = object : IssueRepository {
            override fun observeIssues(): Flow<PagingData<Issue>> = flowOf(PagingData.empty())

            override suspend fun reload() = importIssues()
        }
        return IssuesViewModel(repository).also { store.put("issues", it) }
    }
}
