package deghat.farhad.csvision.feature.issues.data

import android.content.Context
import androidx.paging.PagingConfig
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import deghat.farhad.csvision.feature.issues.data.local.IssueEntity
import deghat.farhad.csvision.feature.issues.data.local.IssuesDatabase
import deghat.farhad.csvision.feature.issues.domain.Issue
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultIssueRepositoryTest {
    private lateinit var database: IssuesDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, IssuesDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun readsSavedIssuesAsPagedDomainModels() = runTest {
        val theo = IssueEntity(
            recordNumber = 2,
            firstName = "Theo",
            surname = "Jansen",
            issueCount = 5,
            dateOfBirthEpochDay = 2_923,
            avatarUrl = "https://example.com/theo.png"
        )
        val fiona = IssueEntity(
            recordNumber = 5,
            firstName = "Fiona",
            surname = "de Vries",
            issueCount = 7,
            dateOfBirthEpochDay = -6_990,
            avatarUrl = "https://example.com/fiona.png"
        )
        val dao = database.issueDao()
        dao.insertBatch(listOf(theo.copy(recordNumber = 9), fiona, theo))
        val repository: IssueRepository = DefaultIssueRepository(
            dao = dao,
            pagingConfig = PagingConfig(
                pageSize = 2,
                initialLoadSize = 2,
                prefetchDistance = 1,
                enablePlaceholders = false,
                maxSize = 6
            )
        )

        val issues = repository.observeIssues().asSnapshot {
            appendScrollWhile { true }
        }

        val theoIssue = Issue(
            firstName = "Theo",
            surname = "Jansen",
            issueCount = 5,
            dateOfBirth = LocalDate.of(1978, 1, 2),
            avatarUrl = "https://example.com/theo.png"
        )
        val fionaIssue = Issue(
            firstName = "Fiona",
            surname = "de Vries",
            issueCount = 7,
            dateOfBirth = LocalDate.of(1950, 11, 12),
            avatarUrl = "https://example.com/fiona.png"
        )
        assertEquals(listOf(theoIssue, fionaIssue, theoIssue), issues)
    }
}
