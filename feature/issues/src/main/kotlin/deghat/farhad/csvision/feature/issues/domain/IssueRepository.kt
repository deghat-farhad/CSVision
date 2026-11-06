package deghat.farhad.csvision.feature.issues.domain

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface IssueRepository {
    fun observeIssues(): Flow<PagingData<Issue>>

    suspend fun reload()
}
