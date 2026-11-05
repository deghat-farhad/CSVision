package deghat.farhad.csvision.feature.issues.data

import deghat.farhad.csvision.core.csv.CsvParser
import deghat.farhad.csvision.feature.issues.data.local.IssueDao
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import java.io.StringReader
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IssueBatchImporterTest {
    @Test
    fun savesFullBatchAndFinalPartialBatch() = runTest {
        val dao = RecordingIssueDao()
        val importer = IssueBatchImporter(
            dao = dao,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            batchSize = 2
        )
        val csv = listOf(
            "First name,Sur name,Issue count,Date of birth,avatar",
            "Theo,Jansen,5,1978-01-02T00:00:00,https://example.com/theo.png",
            "Fiona,de Vries,7,1950-11-12T00:00:00,https://example.com/fiona.png",
            "Theo,Jansen,5,1978-01-02T00:00:00,https://example.com/theo.png"
        ).joinToString("\n")

        StringReader(csv).use { reader ->
            importer.importIssues(IssueCsvReader(CsvParser(reader), IssueMapper()))
        }

        val theo = IssueEntity(
            recordNumber = 2,
            firstName = "Theo",
            surname = "Jansen",
            issueCount = 5,
            dateOfBirthEpochDay = 2_923,
            avatarUrl = "https://example.com/theo.png"
        )
        val fiona = IssueEntity(
            recordNumber = 3,
            firstName = "Fiona",
            surname = "de Vries",
            issueCount = 7,
            dateOfBirthEpochDay = -6_990,
            avatarUrl = "https://example.com/fiona.png"
        )

        assertEquals(
            listOf(listOf(theo, fiona), listOf(theo.copy(recordNumber = 4))),
            dao.batches
        )
    }

    @Test
    fun rejectsNonPositiveBatchSizes() {
        for (batchSize in listOf(0, -1)) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                IssueBatchImporter(RecordingIssueDao(), StandardTestDispatcher(), batchSize)
            }

            assertEquals("Batch size must be positive.", error.message)
        }
    }

    private class RecordingIssueDao : IssueDao {
        val batches = mutableListOf<List<IssueEntity>>()

        override suspend fun insertBatch(issues: List<IssueEntity>) {
            batches.add(issues)
        }

        override suspend fun clear() {
            error("The importer must not clear the working dataset.")
        }
    }
}
