 package deghat.farhad.csvision.feature.issues.data

import deghat.farhad.csvision.feature.issues.data.remote.IssueCsvHttpSource
import java.io.Reader

internal class IssueRemoteImporter(
    private val source: IssueCsvHttpSource,
    private val batchImporter: IssueBatchImporter,
    private val createReader: (Reader) -> IssueCsvReader
) {
    suspend fun importIssues() {
        source.withReader { reader ->
            batchImporter.importIssues(createReader(reader))
        }
    }
}
