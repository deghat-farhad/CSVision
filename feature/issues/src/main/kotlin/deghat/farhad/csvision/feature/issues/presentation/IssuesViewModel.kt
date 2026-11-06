package deghat.farhad.csvision.feature.issues.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.map
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import kotlinx.coroutines.flow.map

internal class IssuesViewModel(repository: IssueRepository) : ViewModel() {
    val issues = repository.observeIssues()
        .map { pagingData -> pagingData.map { issue -> issue.toUiModel() } }
        .cachedIn(viewModelScope)
}
