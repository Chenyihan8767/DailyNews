package com.dailynews.reader.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Query("SELECT * FROM sources ORDER BY name, category")
    fun observeSources(): Flow<List<NewsSource>>
    @Query("SELECT * FROM sources WHERE enabled = 1")
    suspend fun enabledSources(): List<NewsSource>
    @Query("SELECT COUNT(*) FROM sources") suspend fun sourceCount(): Int
    @Upsert suspend fun saveSource(source: NewsSource)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSources(sources: List<NewsSource>)
    @Query("UPDATE sources SET enabled = :enabled WHERE id = :id")
    suspend fun toggleSource(id: String, enabled: Boolean)
    @Query("DELETE FROM sources WHERE id = :id") suspend fun deleteSource(id: String)
    @Query("UPDATE sources SET lastSync = :at, error = :error WHERE id = :id")
    suspend fun sourceResult(id: String, at: Long, error: String?)

    @Query("SELECT * FROM articles ORDER BY isDemo ASC, publishedAt DESC")
    fun observeArticles(): Flow<List<Article>>
    @Query("SELECT * FROM articles WHERE id = :id") fun observeArticle(id: String): Flow<Article?>
    @Query("SELECT * FROM articles WHERE id = :id") suspend fun article(id: String): Article?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertArticles(items: List<Article>)
    @Upsert suspend fun saveArticle(article: Article)
    @Query("UPDATE articles SET bookmarked = :value WHERE id = :id")
    suspend fun bookmark(id: String, value: Boolean)
    @Query("UPDATE articles SET lastRead = :at, readParagraph = :paragraph WHERE id = :id")
    suspend fun progress(id: String, paragraph: Int, at: Long)
    @Query("UPDATE articles SET paragraphsJson = :body, contentHash = :hash, author = :author, imageUrl = COALESCE(:image, imageUrl), bodyError = NULL WHERE id = :id")
    suspend fun saveBody(id: String, body: String, hash: String, author: String, image: String?)
    @Query("UPDATE articles SET bodyError = :error WHERE id = :id")
    suspend fun bodyFailed(id: String, error: String)
    @Query("UPDATE articles SET processing = 'queued', priority = MAX(priority, :priority), queuedAt = :at, processingError = NULL WHERE id = :id AND processing != 'running'")
    suspend fun queue(id: String, priority: Int, at: Long)
    @Query("SELECT * FROM articles WHERE processing = 'queued' ORDER BY priority DESC, queuedAt ASC LIMIT 1")
    suspend fun nextQueued(): Article?
    @Query("UPDATE articles SET processing = :state, processingError = :error WHERE id = :id")
    suspend fun processingState(id: String, state: String, error: String? = null)
    @Query("UPDATE articles SET analysisJson = :analysis, analysisKey = :key, processing = 'ready', priority = 0, processingError = NULL WHERE id = :id")
    suspend fun saveAnalysis(id: String, analysis: String, key: String)
    @Query("UPDATE articles SET processing = 'queued' WHERE processing = 'running'")
    suspend fun recoverInterrupted()
    @Query("UPDATE articles SET processing = 'queued', processingError = NULL WHERE processing = 'waiting_key'")
    suspend fun resumeWaiting()
    @Query("SELECT * FROM articles WHERE isDemo = 0 AND publishedAt >= :since ORDER BY publishedAt DESC")
    suspend fun dailyCandidates(since: Long): List<Article>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC") fun observeNotes(): Flow<List<StudyNote>>
    @Upsert suspend fun saveNote(note: StudyNote)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun deleteNote(id: String)
    @Query("SELECT * FROM gloss_cache WHERE id = :id") suspend fun cachedGloss(id: String): CachedGloss?
    @Upsert suspend fun saveGloss(gloss: CachedGloss)
}

@Database(entities = [NewsSource::class, Article::class, StudyNote::class, CachedGloss::class], version = 2, exportSchema = false)
abstract class NewsDatabase : RoomDatabase() { abstract fun newsDao(): NewsDao }

val NEWS_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sources ADD COLUMN region TEXT NOT NULL DEFAULT 'GLOBAL'")
        db.execSQL("ALTER TABLE articles ADD COLUMN region TEXT NOT NULL DEFAULT 'GLOBAL'")
        db.execSQL("ALTER TABLE notes ADD COLUMN insightJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE notes ADD COLUMN analysisJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE sources SET region = 'ASIA' WHERE id = 'cna-asia'")
        db.query("SELECT id, title, sourceId FROM articles").use { cursor ->
            while (cursor.moveToNext()) {
                val fallback = if (cursor.getString(2) == "cna-asia") Region.ASIA.name else Region.GLOBAL.name
                db.execSQL("UPDATE articles SET region = ? WHERE id = ?", arrayOf(NewsParsing.region(cursor.getString(1), fallback), cursor.getString(0)))
            }
        }
    }
}
