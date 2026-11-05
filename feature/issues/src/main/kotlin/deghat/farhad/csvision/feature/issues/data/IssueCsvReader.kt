package deghat.farhad.csvision.feature.issues.data

import deghat.farhad.csvision.core.csv.CsvParser
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity

internal class IssueCsvReader(
    private val parser: CsvParser,
    private val mapper: IssueMapper
) {
    private var recordNumber = 0L
    var rejectedRecordCount = 0L
        private set

    fun readIssue(): IssueEntity? {
        if (recordNumber == 0L) {
            val header = parser.readRecord() ?: return null
            require(header == expectedHeader) { "Unexpected Issue CSV header." }
            recordNumber++
        }

        while (true) {
            val fields = parser.readRecord() ?: return null
            recordNumber++
            if (fields.size == 1 && fields[0].isEmpty()) continue

            if (fields.size != expectedHeader.size) {
                rejectedRecordCount++
                continue
            }

            return mapper.map(fields, recordNumber)
        }
    }

    private companion object {
        val expectedHeader = listOf(
            "First name",
            "Sur name",
            "Issue count",
            "Date of birth",
            "avatar"
        )
    }
}
