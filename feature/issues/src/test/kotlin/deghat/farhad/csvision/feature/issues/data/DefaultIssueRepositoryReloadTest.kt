package deghat.farhad.csvision.feature.issues.data

import androidx.paging.PagingSource
import deghat.farhad.csvision.feature.issues.data.local.IssueDao
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DefaultIssueRepositoryReloadTest {
    @Test
    fun delegatesReloadToCoordinatorAndWaitsForImportCompletion() = runTest {
        val events = mutableListOf<String>()
        val importStarted = CompletableDeferred<Unit>()
        val finishImport = CompletableDeferred<Unit>()
        val dao = object : IssueDao {
            override suspend fun clear() {
                events.add("clear")
            }

            override suspend fun insertBatch(issues: List<IssueEntity>) {
                error("Reload must delegate issue insertion to the importer.")
            }

            override fun pagingSource(): PagingSource<Int, IssueEntity> {
                error("Reload must not read pages.")
            }
        }
        val coordinator = IssueImportCoordinator(dao) {
            events.add("import started")
            importStarted.complete(Unit)
            finishImport.await()
            events.add("import finished")
        }
        val repository: IssueRepository = DefaultIssueRepository(dao, coordinator)
        val reload = launch { repository.reload() }

        try {
            withTimeout(5_000) { importStarted.await() }
            assertEquals(listOf("clear", "import started"), events)
            assertFalse(reload.isCompleted)
        } finally {
            finishImport.complete(Unit)
        }

        withTimeout(5_000) { reload.join() }

        assertFalse(reload.isCancelled)
        assertEquals(listOf("clear", "import started", "import finished"), events)
    }
}
