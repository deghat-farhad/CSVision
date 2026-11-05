package deghat.farhad.csvision.feature.issues.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import deghat.farhad.csvision.feature.issues.domain.Issue
import java.time.LocalDate

@Entity(tableName = "issues")
data class IssueEntity(
    @PrimaryKey val recordNumber: Long,
    val firstName: String,
    val surname: String,
    val issueCount: Int,
    val dateOfBirthEpochDay: Long,
    val avatarUrl: String
)

fun IssueEntity.toDomain(): Issue = Issue(
    firstName = firstName,
    surname = surname,
    issueCount = issueCount,
    dateOfBirth = LocalDate.ofEpochDay(dateOfBirthEpochDay),
    avatarUrl = avatarUrl
)
