package deghat.farhad.csvision.feature.issues.data

import androidx.paging.PagingSource
import deghat.farhad.csvision.feature.issues.data.local.IssueDao
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueImportCoordinatorTest {
    @Test
    fun waitsForCancelledImportCleanupBeforeClearingAndStartingAnotherImport() = runTest {
        val events = mutableListOf<String>()
        val firstImportStarted = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        var importCount = 0
        val dao = object : IssueDao {
            override suspend fun clear() {
                events.add("clear")
            }

            override suspend fun insertBatch(issues: List<IssueEntity>) {
                error("The coordinator must delegate issue insertion to the importer.")
            }

            override fun pagingSource(): PagingSource<Int, IssueEntity> {
                error("Reload coordination must not read pages.")
            }
        }
        val coordinator = IssueImportCoordinator(dao) {
            importCount++
            events.add("import $importCount")

            if (importCount == 1) {
                firstImportStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        events.add("cleanup 1 started")
                        cleanupStarted.complete(Unit)
                        releaseCleanup.await()
                        events.add("cleanup 1 finished")
                    }
                }
            }
        }
        val firstReload = launch { coordinator.reload() }
        withTimeout(5_000) { firstImportStarted.await() }
        val secondReload = launch { coordinator.reload() }

        try {
            withTimeout(5_000) { cleanupStarted.await() }
            assertEquals(listOf("clear", "import 1", "cleanup 1 started"), events)
            assertFalse(firstReload.isCompleted)
            assertFalse(secondReload.isCompleted)
        } finally {
            releaseCleanup.complete(Unit)
        }

        withTimeout(5_000) {
            firstReload.join()
            secondReload.join()
        }

        assertTrue(firstReload.isCancelled)
        assertFalse(secondReload.isCancelled)
        assertEquals(
            listOf("clear", "import 1", "cleanup 1 started", "cleanup 1 finished", "clear", "import 2"),
            events
        )
    }
}
