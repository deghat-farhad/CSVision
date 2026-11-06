package deghat.farhad.csvision

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesIssuesScreenInsteadOfStarterGreeting() {
        compose.onNodeWithText("Issues").assertIsDisplayed()
        compose.onNodeWithText("Hello Android!").assertDoesNotExist()
    }

    @Test
    fun keepsIssuesScreenAndRepositoryWhenActivityIsRecreated() {
        val repository = compose.activity.issueRepository
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Issues").assertIsDisplayed()
        assertSame(repository, compose.activity.issueRepository)
    }
}
