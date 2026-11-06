package deghat.farhad.csvision.feature.issues.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import deghat.farhad.csvision.feature.issues.data.local.IssueDao
import deghat.farhad.csvision.feature.issues.data.local.toDomain
import deghat.farhad.csvision.feature.issues.domain.Issue
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DefaultIssueRepository(
    private val dao: IssueDao,
    private val importCoordinator: IssueImportCoordinator,
    private val pagingConfig: PagingConfig = PagingConfig(
        pageSize = 50,
        enablePlaceholders = false,
        maxSize = 200
    )
) : IssueRepository {
    override fun observeIssues(): Flow<PagingData<Issue>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::pagingSource
    ).flow.map { pagingData ->
        pagingData.map { entity -> entity.toDomain() }
    }

    override suspend fun reload() = importCoordinator.reload()
}
