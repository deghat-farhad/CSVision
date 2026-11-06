package deghat.farhad.csvision.feature.issues.data.local

import deghat.farhad.csvision.feature.issues.domain.Issue
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class IssueEntityMapperTest {
    @Test
    fun mapsEntityToDomainIssue() {
        val issue = Issue(
            recordNumber = 2,
            firstName = "Theo",
            surname = "Jansen",
            issueCount = 5,
            dateOfBirth = LocalDate.of(1978, 1, 2),
            avatarUrl = "https://example.com/theo.png"
        )
        val entity = IssueEntity(
            recordNumber = 2,
            firstName = "Theo",
            surname = "Jansen",
            issueCount = 5,
            dateOfBirthEpochDay = 2_923,
            avatarUrl = "https://example.com/theo.png"
        )

        assertEquals(issue, entity.toDomain())
    }
}
