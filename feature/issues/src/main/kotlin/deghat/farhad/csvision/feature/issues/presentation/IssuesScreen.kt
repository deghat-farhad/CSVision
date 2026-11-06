package deghat.farhad.csvision.feature.issues.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import deghat.farhad.csvision.feature.issues.R
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import kotlinx.coroutines.flow.flowOf

@Composable
fun IssuesRoute(repository: IssueRepository, modifier: Modifier = Modifier) {
    val viewModel: IssuesViewModel = viewModel { IssuesViewModel(repository) }
    val issues = viewModel.issues.collectAsLazyPagingItems()
    IssuesScreen(issues, modifier)
}

@Composable
private fun IssuesScreen(issues: LazyPagingItems<IssueUiModel>, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            Text(
                text = stringResource(R.string.issues_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    count = issues.itemCount,
                    key = issues.itemKey { it.recordNumber },
                    contentType = { "issue" }
                ) { index ->
                    val issue = issues[index]
                    if (issue != null) {
                        IssueRow(issue)
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: IssueUiModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = issue.fullName, style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.issues_count, issue.issueCount))
            Text(text = stringResource(R.string.issues_date_of_birth, issue.dateOfBirth))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IssuesScreenPreview() {
    val issues = remember {
        flowOf(
            PagingData.from(
                listOf(
                    IssueUiModel(2, "Theo Jansen", 5, "1978-01-02"),
                    IssueUiModel(3, "Fiona de Vries", 7, "1950-11-12")
                )
            )
        )
    }.collectAsLazyPagingItems()

    MaterialTheme {
        IssuesScreen(issues)
    }
}
