package deghat.farhad.csvision.feature.issues.data

import deghat.farhad.csvision.core.csv.CsvParser
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IssueCsvReaderIntegrationTest {
    @Test
    fun readsRabobankMalformedFixtureAndCountsRejectedRecord() {
        val input = requireNotNull(javaClass.getResourceAsStream("/issues_malformed.csv"))

        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val issues = IssueCsvReader(CsvParser(reader), IssueMapper())
            val theo = IssueEntity(
                recordNumber = 6,
                firstName = "Theo",
                surname = "Jansen",
                issueCount = 5,
                dateOfBirthEpochDay = 2_923,
                avatarUrl = "https://api.multiavatar.com/2cdf5db9b4dee297b7.png"
            )
            val expected = listOf(
                theo.copy(recordNumber = 2, surname = "'Jansen'"),
                IssueEntity(
                    recordNumber = 5,
                    firstName = "Petra",
                    surname = "Boersma",
                    issueCount = 1,
                    dateOfBirthEpochDay = LocalDate.of(2001, 4, 20).toEpochDay(),
                    avatarUrl = "httultiavatar.com/2672c49d6099f87274.png"
                ),
                theo
            )

            val actual = generateSequence(issues::readIssue).toList()

            assertEquals(expected, actual)
            assertEquals(1L, issues.rejectedRecordCount)
            assertNull(issues.readIssue())
            assertEquals(1L, issues.rejectedRecordCount)
        }
    }
}
