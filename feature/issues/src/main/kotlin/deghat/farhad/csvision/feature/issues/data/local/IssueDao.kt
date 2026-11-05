package deghat.farhad.csvision.feature.issues.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
internal interface IssueDao {
    @Insert
    suspend fun insertBatch(issues: List<IssueEntity>)

    @Query("DELETE FROM issues")
    suspend fun clear()
}
