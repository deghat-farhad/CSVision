package deghat.farhad.csvision.feature.issues.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import deghat.farhad.csvision.core.csv.CsvParser
import deghat.farhad.csvision.feature.issues.data.DefaultIssueRepository
import deghat.farhad.csvision.feature.issues.data.IssueBatchImporter
import deghat.farhad.csvision.feature.issues.data.IssueCsvReader
import deghat.farhad.csvision.feature.issues.data.IssueImportCoordinator
import deghat.farhad.csvision.feature.issues.data.IssueMapper
import deghat.farhad.csvision.feature.issues.data.IssueRemoteImporter
import deghat.farhad.csvision.feature.issues.data.local.IssuesDatabase
import deghat.farhad.csvision.feature.issues.data.remote.IssueCsvHttpSource
import deghat.farhad.csvision.feature.issues.data.remote.createIssueHttpClient
import deghat.farhad.csvision.feature.issues.domain.IssueRepository
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrl

@Module
@InstallIn(SingletonComponent::class)
internal object IssuesModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): IssuesDatabase =
        Room.databaseBuilder(context, IssuesDatabase::class.java, "issues.db").build()

    @Provides
    @Singleton
    fun provideIssueRepository(database: IssuesDatabase): IssueRepository {
        val dao = database.issueDao()
        val importer = IssueRemoteImporter(
            source = IssueCsvHttpSource(
                callFactory = createIssueHttpClient(),
                url = "https://raw.githubusercontent.com/RabobankDev/AssignmentCSV/main/issues.csv".toHttpUrl(),
                ioDispatcher = Dispatchers.IO
            ),
            batchImporter = IssueBatchImporter(dao, Dispatchers.IO),
            createReader = { reader -> IssueCsvReader(CsvParser(reader), IssueMapper()) }
        )
        return DefaultIssueRepository(
            dao = dao,
            importCoordinator = IssueImportCoordinator(dao, importer::importIssues)
        )
    }
}
