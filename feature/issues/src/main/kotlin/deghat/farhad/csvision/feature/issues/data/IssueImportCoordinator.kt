package deghat.farhad.csvision.feature.issues.data

import deghat.farhad.csvision.feature.issues.data.local.IssueDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class IssueImportCoordinator(
    private val dao: IssueDao,
    private val importIssues: suspend () -> Unit
) {
    private val mutex = Mutex()
    private var activeImport: Job? = null

    suspend fun reload(): Unit = coroutineScope {
        val operation = mutex.withLock {
            activeImport?.cancelAndJoin()
            async {
                dao.clear()
                importIssues()
            }.also { activeImport = it }
        }

        operation.await()
    }
}
