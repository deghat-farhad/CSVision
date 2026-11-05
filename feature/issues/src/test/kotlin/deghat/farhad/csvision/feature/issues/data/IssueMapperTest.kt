package deghat.farhad.csvision.feature.issues.data

import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class IssueMapperTest {
    @Test
    fun mapsValidRecordToEntity() {
        val fields = listOf(
            "Theo",
            "Jansen",
            "5",
            "1978-01-02T00:00:00",
            "https://example.com/theo.png"
        )

        val entity = IssueMapper().map(fields, recordNumber = 2)

        assertEquals(
            IssueEntity(
                recordNumber = 2,
                firstName = "Theo",
                surname = "Jansen",
                issueCount = 5,
                dateOfBirthEpochDay = 2_923,
                avatarUrl = "https://example.com/theo.png"
            ),
            entity
        )
    }
}
