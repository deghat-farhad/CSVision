package deghat.farhad.csvision.feature.issues.domain

import java.time.LocalDate

data class Issue(
    val recordNumber: Long,
    val firstName: String,
    val surname: String,
    val issueCount: Int,
    val dateOfBirth: LocalDate,
    val avatarUrl: String
)
