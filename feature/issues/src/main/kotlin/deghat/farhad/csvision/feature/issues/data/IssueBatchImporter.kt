package deghat.farhad.csvision.feature.issues.data

import deghat.farhad.csvision.feature.issues.data.local.IssueDao
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class IssueBatchImporter(
    private val dao: IssueDao,
    private val ioDispatcher: CoroutineDispatcher,
    private val batchSize: Int = 500
) {
    init {
        require(batchSize > 0) { "Batch size must be positive." }
    }

    suspend fun importIssues(reader: IssueCsvReader) {
        withContext(ioDispatcher) {
            val batch = ArrayList<IssueEntity>(batchSize)

            while (true) {
                currentCoroutineContext().ensureActive()
                val issue = reader.readIssue() ?: break
                batch.add(issue)

                if (batch.size == batchSize) {
                    dao.insertBatch(batch.toList())
                    batch.clear()
                }
            }

            if (batch.isNotEmpty()) {
                dao.insertBatch(batch.toList())
            }
        }
    }
}
