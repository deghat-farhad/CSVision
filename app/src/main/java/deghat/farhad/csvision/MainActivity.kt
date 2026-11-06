package deghat.farhad.csvision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import deghat.farhad.csvision.feature.issues.presentation.IssuesRoute
import deghat.farhad.csvision.ui.theme.CSVisionTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var issueRepository: IssueRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CSVisionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    IssuesRoute(
                        repository = issueRepository,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
