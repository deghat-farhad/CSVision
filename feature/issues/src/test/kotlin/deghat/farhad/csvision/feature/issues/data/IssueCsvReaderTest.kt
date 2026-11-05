package deghat.farhad.csvision.feature.issues.data

import deghat.farhad.csvision.core.csv.CsvParser
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import java.io.Reader
import java.io.StringReader
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class IssueCsvReaderTest {
    private val header = "\"First name\",\"Sur name\",\"Issue count\",\"Date of birth\",\"avatar\""
    private val theoRecord = "\"Theo\",\"Jansen\",5,\"1978-01-02T00:00:00\",\"https://example.com/theo.png\""
    private val theo = IssueEntity(
        recordNumber = 2,
        firstName = "Theo",
        surname = "Jansen",
        issueCount = 5,
        dateOfBirthEpochDay = 2_923,
        avatarUrl = "https://example.com/theo.png"
    )

    @Test
    fun readsTypedIssuesOneAtATimeAfterHeader() {
        val csv = listOf(
            header,
            theoRecord,
            "\"Fiona\",\"de Vries\",7,\"1950-11-12T00:00:00\",\"https://example.com/fiona.png\""
        ).joinToString("\r\n")

        StringReader(csv).use { reader ->
            val issues = IssueCsvReader(CsvParser(reader), IssueMapper())

            assertEquals(theo, issues.readIssue())
            assertEquals(
                IssueEntity(
                    recordNumber = 3,
                    firstName = "Fiona",
                    surname = "de Vries",
                    issueCount = 7,
                    dateOfBirthEpochDay = LocalDate.of(1950, 11, 12).toEpochDay(),
                    avatarUrl = "https://example.com/fiona.png"
                ),
                issues.readIssue()
            )
            assertNull(issues.readIssue())
            assertNull(issues.readIssue())
        }
    }

    @Test
    fun skipsBlankRecordsAndPreservesRecordNumbers() {
        for (lineEnding in listOf("\n", "\r\n", "\r")) {
            val csv = listOf(header, "", "", theoRecord, "", theoRecord, "", "")
                .joinToString(lineEnding)

            StringReader(csv).use { reader ->
                val issues = IssueCsvReader(CsvParser(reader), IssueMapper())

                assertEquals(theo.copy(recordNumber = 4), issues.readIssue())
                assertEquals(theo.copy(recordNumber = 6), issues.readIssue())
                assertNull(issues.readIssue())
                assertNull(issues.readIssue())
                assertEquals(0L, issues.rejectedRecordCount)
            }
        }
    }

    @Test
    fun returnsFirstIssueWithoutReadingRemainingInput() {
        val source = StringReader("$header\n$theoRecord\n")
        val reader = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                val count = source.read(buffer, offset, length)
                check(count != -1) { "The reader read beyond the first issue." }
                return count
            }

            override fun close() = source.close()
        }

        reader.use {
            assertEquals(theo, IssueCsvReader(CsvParser(it), IssueMapper()).readIssue())
        }
    }

    @Test
    fun usesSuppliedParserRecordLimit() {
        val maxRecordChars = header.length

        StringReader("$header\n$theoRecord").use { reader ->
            val parser = CsvParser(reader, maxRecordChars = maxRecordChars)
            val issues = IssueCsvReader(parser, IssueMapper())

            val error = assertThrows(IllegalArgumentException::class.java) {
                issues.readIssue()
            }

            assertEquals(
                "Record exceeds the maximum size of $maxRecordChars characters.",
                error.message
            )
        }
    }

    @Test
    fun returnsNullForEmptyInputOrHeaderWithoutRecords() {
        for (csv in listOf("", header, "$header\r\n")) {
            StringReader(csv).use { reader ->
                val issues = IssueCsvReader(CsvParser(reader), IssueMapper())

                assertNull(issues.readIssue())
                assertNull(issues.readIssue())
            }
        }
    }

    @Test
    fun rejectsUnexpectedHeader() {
        for (invalidHeader in listOf(
            "First name,Sur name,Issue count,Date of birth",
            "Sur name,First name,Issue count,Date of birth,avatar",
            "First name,Sur name,Issue count,Date of birth,photo",
            "First name,Sur name,Issue count,Date of birth,avatar,extra"
        )) {
            StringReader("$invalidHeader\n$theoRecord").use { reader ->
                val error = assertThrows(IllegalArgumentException::class.java) {
                    IssueCsvReader(CsvParser(reader), IssueMapper()).readIssue()
                }

                assertEquals("Unexpected Issue CSV header.", error.message)
            }
        }
    }

    @Test
    fun skipsAndCountsRecordsWithMissingOrExtraFields() {
        val csv = listOf(
            header,
            theoRecord,
            "Theo,Jansen,5,1978-01-02T00:00:00",
            "$theoRecord,extra",
            "",
            theoRecord,
            "Theo,Jansen"
        ).joinToString("\n")

        StringReader(csv).use { reader ->
            val issues = IssueCsvReader(CsvParser(reader), IssueMapper())

            assertEquals(0L, issues.rejectedRecordCount)
            assertEquals(theo, issues.readIssue())
            assertEquals(0L, issues.rejectedRecordCount)
            assertEquals(theo.copy(recordNumber = 6), issues.readIssue())
            assertEquals(2L, issues.rejectedRecordCount)
            assertNull(issues.readIssue())
            assertEquals(3L, issues.rejectedRecordCount)
            assertNull(issues.readIssue())
            assertEquals(3L, issues.rejectedRecordCount)
        }
    }
}
