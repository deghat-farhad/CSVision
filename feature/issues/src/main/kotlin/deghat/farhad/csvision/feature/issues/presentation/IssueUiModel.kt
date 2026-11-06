package deghat.farhad.csvision.feature.issues.presentation

import deghat.farhad.csvision.feature.issues.domain.Issue

internal data class IssueUiModel(
    val recordNumber: Long,
    val fullName: String,
    val issueCount: Int,
    val dateOfBirth: String
)

internal fun Issue.toUiModel(): IssueUiModel = IssueUiModel(
    recordNumber = recordNumber,
    fullName = "$firstName $surname",
    issueCount = issueCount,
    dateOfBirth = dateOfBirth.toString()
)
