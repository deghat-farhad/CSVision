package deghat.farhad.csvision.feature.issues.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface IssueDao {
    @Insert
    suspend fun insertBatch(issues: List<IssueEntity>)

    @Query("SELECT * FROM issues ORDER BY recordNumber ASC")
    fun pagingSource(): PagingSource<Int, IssueEntity>

    @Query("DELETE FROM issues")
    suspend fun clear()
}
