package deghat.farhad.csvision.feature.issues.data

import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import java.time.LocalDateTime

class IssueMapper {
    fun map(fields: List<String>, recordNumber: Long): IssueEntity = IssueEntity(
        recordNumber = recordNumber,
        firstName = fields[0],
        surname = fields[1],
        issueCount = fields[2].toInt(),
        dateOfBirthEpochDay = LocalDateTime.parse(fields[3]).toLocalDate().toEpochDay(),
        avatarUrl = fields[4]
    )
}
