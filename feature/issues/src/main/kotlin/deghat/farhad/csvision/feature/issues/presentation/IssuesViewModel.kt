package deghat.farhad.csvision.feature.issues.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.map
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class IssuesViewModel(private val repository: IssueRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(IssuesUiState())
    val uiState = _uiState.asStateFlow()

    val issues = repository.observeIssues()
        .map { pagingData -> pagingData.map { issue -> issue.toUiModel() } }
        .cachedIn(viewModelScope)

    fun loadIssues() {
        if (_uiState.value.isLoading) return
        _uiState.value = IssuesUiState(isLoading = true)

        viewModelScope.launch {
            try {
                repository.reload()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = IssuesUiState(loadFailed = true)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
