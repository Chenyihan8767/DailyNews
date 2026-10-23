package com.dailynews.reader

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.dailynews.reader.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReaderFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as NewsApplication

    @Before fun setup() {
        runBlocking {
            app.container.settings.update { ReaderSettings(appearance = Appearance.DARK, dailyEnabled = false,
                processOnOpen = false, processOnBookmark = false, wifiOnly = false) }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText(DemoContent.article.title).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun combinedGlossRetainsContextAndCreatesNote() {
        screenshot("home-dark")
        openDemo()
        screenshot("reader-dark")
        tapWeigh()
        compose.onNodeWithText("权衡；仔细考虑").assertIsDisplayed()
        screenshot("gloss-popup-dark")
        compose.onNodeWithText("详情").performClick()
        compose.onNodeWithText("原文语境").assertIsDisplayed()
        screenshot("gloss-details-dark")
        compose.onNodeWithText("加入笔记").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { app.container.dao.observeNotes().first().any { it.term == "weigh" } } }
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("返回新闻列表").performClick()
        compose.onNodeWithText("笔记").performClick()
        compose.onNodeWithText("weigh").assertIsDisplayed()
        compose.onNodeWithText("权衡；仔细考虑").assertIsDisplayed()
        screenshot("notes-dark")
    }

    @Test fun translationUsesOriginalParagraphAndSummaryIsAvailableOffline() {
        openDemo()
        compose.onAllNodesWithText("翻译").onFirst().performClick()
        compose.onNodeWithText(DemoContent.analysis.translations.first().translation).performScrollTo().assertIsDisplayed()
        screenshot("translation-dark")
        compose.onNodeWithText("文章摘要").performClick()
        compose.onNodeWithText(DemoContent.analysis.summaryEn).assertIsDisplayed()
        screenshot("summary-dark")
    }

    @Test fun preparedListOpensRunningArticlesAndPreservesFailuresOnOpen() {
        val original = runBlocking { app.container.dao.article(DemoContent.article.id)!! }
        val originalSettings = runBlocking { app.container.settings.current() }
        try {
            val article = original.copy(isDemo = false,
                title = "Prepared article navigation test", processing = "running", analysisJson = null,
                analysisKey = "", queuedAt = 1)
            runBlocking { app.container.dao.saveArticle(article) }
            compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("prepared-list-entry"))
            compose.onNodeWithTag("prepared-list-entry").performClick()
            compose.onNodeWithTag("collection-list").performScrollToNode(hasText(article.title))
            compose.onNodeWithText(article.title).performClick()
            compose.onNodeWithTag("reader-list").performScrollToNode(hasTestTag("paragraph-p1"))
            compose.onNodeWithTag("paragraph-p1").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回新闻列表").performClick()
            runBlocking {
                app.container.settings.update { it.copy(processOnOpen = true) }
                app.container.dao.processingState(article.id, "failed", "AI接口返回输出长度超限")
            }
            compose.onNodeWithTag("collection-list").performScrollToNode(hasText(article.title))
            compose.onNodeWithText(article.title).performClick()
            compose.onNodeWithTag("reader-list").performScrollToNode(hasText("重试AI处理"))
            compose.onNodeWithText("重试AI处理").assertIsDisplayed()
            assertEquals("failed", runBlocking { app.container.dao.article(article.id)!!.processing })
        } finally {
            runBlocking {
                app.container.dao.saveArticle(original)
                app.container.settings.update { originalSettings }
            }
        }
    }

    @Test fun glossModePersistsAndDirectSheetModeWorks() {
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("点词释义方式").performScrollTo().performClick()
        compose.onNodeWithText("底部面板").performClick()
        compose.waitUntil(5_000) { runBlocking { app.container.settings.current().glossMode == GlossMode.SHEET } }
        screenshot("settings-dark")
        compose.onNodeWithText("今日").performClick()
        openDemo()
        tapWeigh()
        compose.onNodeWithText("原文语境").assertIsDisplayed()
        compose.onNodeWithText("加入笔记").assertIsDisplayed()
    }

    @Test fun lightThemeRendersAndSubscriptionCanBeAddedWithoutNetwork() {
        runBlocking { app.container.settings.update { it.copy(appearance = Appearance.LIGHT) } }
        compose.waitForIdle()
        screenshot("home-light")
        compose.onNodeWithText("订阅").performClick()
        compose.onNodeWithText("添加RSS").performClick()
        compose.onNodeWithText("来源名称").performTextInput("Test feed")
        compose.onNodeWithText("HTTPS RSS / Atom 地址").performTextInput("https://example.org/test.xml")
        compose.onNodeWithText("添加", useUnmergedTree = true).performClick()
        compose.waitUntil(5_000) { runBlocking { app.container.dao.observeSources().first().any { it.name == "Test feed" } } }
        compose.onNodeWithTag("sources-list").performScrollToNode(hasText("Test feed"))
        compose.onNodeWithText("Test feed").assertIsDisplayed()
        screenshot("sources-light")
    }

    private fun openDemo() {
        compose.onNodeWithTag("home-list").performScrollToNode(hasText(DemoContent.article.title))
        compose.onNodeWithText(DemoContent.article.title).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("reader-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("reader-list").performScrollToNode(hasTestTag("paragraph-p1"))
        compose.onNodeWithTag("paragraph-p1").assertIsDisplayed()
    }

    private fun tapWeigh() {
        val node = compose.onNodeWithTag("paragraph-p1")
        node.performScrollTo()
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val index = DemoContent.paragraphs.first().indexOf("weigh") + 1
        val bounds = results.first().getBoundingBox(index)
        node.performTouchInput { click(Offset(bounds.center.x, bounds.center.y)) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
