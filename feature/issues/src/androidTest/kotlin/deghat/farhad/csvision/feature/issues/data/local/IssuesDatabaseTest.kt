package deghat.farhad.csvision.feature.issues.data.local

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssuesDatabaseTest {
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
    fun savesBatchesAndClearsWorkingDataset() = runTest {
        val theo = IssueEntity(
            recordNumber = 2,
            firstName = "Theo",
            surname = "Jansen",
            issueCount = 5,
            dateOfBirthEpochDay = 2_923,
            avatarUrl = "https://example.com/theo.png"
        )
        val fiona = IssueEntity(
            recordNumber = 3,
            firstName = "Fiona",
            surname = "de Vries",
            issueCount = 7,
            dateOfBirthEpochDay = -6_990,
            avatarUrl = "https://example.com/fiona.png"
        )
        val secondTheo = theo.copy(recordNumber = 4)
        val dao = database.issueDao()

        dao.insertBatch(listOf(theo, fiona))
        dao.insertBatch(listOf(secondTheo))

        database.openHelper.readableDatabase.query("SELECT * FROM issues ORDER BY recordNumber").use { cursor ->
            assertEquals(3, cursor.count)
            for (issue in listOf(theo, fiona, secondTheo)) {
                assertTrue(cursor.moveToNext())
                assertEquals(issue.recordNumber, cursor.getLong(cursor.getColumnIndexOrThrow("recordNumber")))
                assertEquals(issue.firstName, cursor.getString(cursor.getColumnIndexOrThrow("firstName")))
                assertEquals(issue.surname, cursor.getString(cursor.getColumnIndexOrThrow("surname")))
                assertEquals(issue.issueCount, cursor.getInt(cursor.getColumnIndexOrThrow("issueCount")))
                assertEquals(issue.dateOfBirthEpochDay, cursor.getLong(cursor.getColumnIndexOrThrow("dateOfBirthEpochDay")))
                assertEquals(issue.avatarUrl, cursor.getString(cursor.getColumnIndexOrThrow("avatarUrl")))
            }
            assertFalse(cursor.moveToNext())
        }

        dao.clear()

        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM issues").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
    }

    @Test
    fun readsPagesInRecordNumberOrder() = runTest {
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
        val secondTheo = theo.copy(recordNumber = 9)
        val dao = database.issueDao()
        dao.insertBatch(listOf(secondTheo, fiona, theo))
        val source = dao.pagingSource()

        val firstPage = source.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 2,
                placeholdersEnabled = false
            )
        ) as PagingSource.LoadResult.Page<Int, IssueEntity>

        assertEquals(listOf(theo, fiona), firstPage.data)
        assertNull(firstPage.prevKey)

        val secondPage = source.load(
            PagingSource.LoadParams.Append(
                key = requireNotNull(firstPage.nextKey),
                loadSize = 2,
                placeholdersEnabled = false
            )
        ) as PagingSource.LoadResult.Page<Int, IssueEntity>

        assertEquals(listOf(secondTheo), secondPage.data)
        assertNull(secondPage.nextKey)
    }
}
