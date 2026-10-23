package com.dailynews.reader

import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dailynews.reader.data.*
import com.dailynews.reader.ui.NewsViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Targeted checks preserve user settings and only create a temporary note. No paid API calls. */
class ReadingUpdatesDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = (compose.activity.application as NewsApplication).container
    private var originalSettings: ReaderSettings? = null
    private val temporaryNote = "reading-update-ui-check"

    @Before fun setup() {
        runBlocking {
            app.repository.initialize()
            originalSettings = app.settings.current()
            app.settings.update { it.copy(processOnOpen = false, processOnBookmark = false) }
        }
        compose.waitUntil(10000) { runCatching { compose.onAllNodesWithTag("home-list").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    @After fun cleanup() {
        runBlocking {
            app.dao.deleteNote(temporaryNote)
            originalSettings?.let { previous -> app.settings.update { previous } }
        }
    }
    @Test fun noteOpensCompleteOfflineDetailsAndParagraphDoesNotRepeatContext() {
        val word = DemoContent.analysis.vocabulary.first { it.term == "weigh" }
        runBlocking {
            app.dao.saveNote(StudyNote(temporaryNote, DemoContent.article.id, DemoContent.article.title,
                "p1", word.term, word.meaning, DemoContent.paragraphs.first(),
                insightJson = newsJson.encodeToString(WordInsight.serializer(), word),
                analysisJson = newsJson.encodeToString(ArticleAnalysis.serializer(), DemoContent.analysis.forParagraph("p1"))))
        }
        compose.onNodeWithText("笔记").performClick()
        compose.onNodeWithTag("notes-list").performScrollToNode(hasTestTag("note-$temporaryNote"))
        compose.onNodeWithTag("note-$temporaryNote").performClick()
        compose.onNodeWithText("词语详情").assertIsDisplayed()
        compose.onNodeWithText(word.explanation).assertExists()
        compose.onNodeWithText("原文语境").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        runBlocking {
            app.dao.saveNote(StudyNote(temporaryNote, DemoContent.article.id, DemoContent.article.title, "p3",
                DemoContent.paragraphs[2], "段落译文测试", DemoContent.paragraphs[2], kind = "paragraph",
                analysisJson = newsJson.encodeToString(ArticleAnalysis.serializer(), DemoContent.analysis.forParagraph("p3"))))
        }
        compose.onNodeWithText("句子与段落").performClick()
        compose.onNodeWithTag("notes-list").performScrollToNode(hasTestTag("note-$temporaryNote"))
        compose.onAllNodesWithText(DemoContent.paragraphs[2]).assertCountEquals(1)
        compose.onNodeWithTag("note-$temporaryNote").performClick()
        compose.onNodeWithText("段落详情").assertIsDisplayed()
        compose.onNodeWithText("句子分析").assertExists()
    }
    @Test fun readerBarsFitScreenHideOnReadAndReturnOnReverseScroll() {
        openDemoDirect()
        val toolbarBounds = compose.onNodeWithTag("reader-toolbar").getUnclippedBoundsInRoot()
        assertTrue(toolbarBounds.bottom - toolbarBounds.top <= 112.dp)
        compose.onNodeWithTag("reader-actions").assertIsDisplayed()
        readerScreenshot("reader-bars-visible")
        compose.onNodeWithTag("reader-list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNodeWithTag("reader-actions").assertDoesNotExist()
        readerScreenshot("reader-bars-hidden")
        compose.onNodeWithTag("reader-list").performTouchInput { swipeDown() }
        compose.waitForIdle()
        compose.onNodeWithTag("reader-actions").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多阅读选项").performClick()
        compose.onNodeWithText("阅读设置").assertIsDisplayed()
    }
    private fun openDemoDirect() {
        compose.runOnUiThread {
            ViewModelProvider(compose.activity, NewsViewModel.Factory(app))[NewsViewModel::class.java].openArticle(DemoContent.article.id)
        }
        compose.waitUntil(10000) {
            runCatching { compose.onAllNodesWithTag("reader-list").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }
    private fun readerScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun titleCanBeTranslatedByTappingHeadlineUsingLocalCache() {
        val translated = "读懂经济新闻的言外之意"
        runBlocking {
            val config = app.settings.current()
            val key = digest("title-v1|${DemoContent.article.title}|${config.endpoint}|${config.model}")
            app.dao.saveGloss(CachedGloss(key, translated))
        }
        openDemoDirect()
        compose.onNodeWithTag("reader-list").performScrollToNode(hasTestTag("reader-title"))
        compose.onNodeWithTag("reader-title").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(translated).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(translated).assertIsDisplayed()
    }
    @Test fun migratedDatabaseAndCatalogAreAvailable() {
        runBlocking {
            assertNotNull(app.dao.article(DemoContent.article.id))
            assertTrue(app.dao.observeSources().first().any { it.id == "guardian-china" })
            assertEquals(2, app.database.openHelper.readableDatabase.version)
        }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("单次最大输出 tokens").performScrollTo().assertIsDisplayed()
    }
}
