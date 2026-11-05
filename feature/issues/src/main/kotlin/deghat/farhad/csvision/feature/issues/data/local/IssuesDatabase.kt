package deghat.farhad.csvision.feature.issues.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [IssueEntity::class], version = 1, exportSchema = true)
internal abstract class IssuesDatabase : RoomDatabase() {
    abstract fun issueDao(): IssueDao
}
