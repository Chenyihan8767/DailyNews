package com.dailynews.reader

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.dailynews.reader.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*

class DatabaseUpgradeTest {
    @Test fun versionOneDataSurvivesValidatedRoomMigration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-check-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val original = SQLiteDatabase.openOrCreateDatabase(file, null)
        original.use { db ->
            db.execSQL("""CREATE TABLE IF NOT EXISTS `sources` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `feedUrl` TEXT NOT NULL, `category` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `lastSync` INTEGER NOT NULL, `error` TEXT, PRIMARY KEY(`id`))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `articles` (`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `sourceName` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `imageUrl` TEXT, `publishedAt` INTEGER NOT NULL, `category` TEXT NOT NULL, `contentType` TEXT NOT NULL, `author` TEXT NOT NULL, `paragraphsJson` TEXT NOT NULL, `contentHash` TEXT NOT NULL, `analysisJson` TEXT, `analysisKey` TEXT NOT NULL, `bookmarked` INTEGER NOT NULL, `lastRead` INTEGER NOT NULL, `readParagraph` INTEGER NOT NULL, `bodyError` TEXT, `processing` TEXT NOT NULL, `priority` INTEGER NOT NULL, `queuedAt` INTEGER NOT NULL, `processingError` TEXT, `isDemo` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `articleId` TEXT NOT NULL, `articleTitle` TEXT NOT NULL, `paragraphId` TEXT NOT NULL, `term` TEXT NOT NULL, `meaning` TEXT NOT NULL, `context` TEXT NOT NULL, `kind` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("""CREATE TABLE IF NOT EXISTS `gloss_cache` (`id` TEXT NOT NULL, `insightJson` TEXT NOT NULL, PRIMARY KEY(`id`))""")
            db.execSQL("INSERT INTO sources VALUES ('cna-asia','Existing custom source','https://example.org/rss','ECONOMY',0,123,NULL)")
            db.execSQL("INSERT INTO articles VALUES ('old','cna-asia','Existing source','https://example.org/a','Taiwan election','description',NULL,123,'POLITICS','报道','author','[]','hash',NULL,'',1,123,2,NULL,'failed',5,123,'old failure',0)")
            db.execSQL("INSERT INTO notes VALUES ('old-note','old','Existing title','p1','word','meaning','context','word',123)")
            db.execSQL("INSERT INTO gloss_cache VALUES ('cached','existing value')")
            db.version = 1
        }
        val upgraded = Room.databaseBuilder(context, NewsDatabase::class.java, name).addMigrations(NEWS_MIGRATION_1_2).build()
        try {
            val dao = upgraded.newsDao()
            val article = dao.article("old")!!
            assertTrue(article.bookmarked)
            assertEquals("old failure", article.processingError)
            assertEquals(2, article.readParagraph)
            assertEquals(Region.HONG_KONG_MACAO_TAIWAN.name, article.region)
            val source = dao.observeSources().first().single()
            assertFalse(source.enabled)
            assertEquals("Existing custom source", source.name)
            assertEquals("context", dao.observeNotes().first().single().context)
            assertEquals("existing value", dao.cachedGloss("cached")!!.insightJson)
        } finally {
            upgraded.close()
            context.deleteDatabase(name)
        }
    }
}
