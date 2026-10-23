package com.dailynews.reader.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

class NewsRepository(val dao: NewsDao, private val http: OkHttpClient = publicHttpClient(),
    private val catalogStore: SettingsStore? = null) {
    private val initializeLock = Mutex()
    private val bodyLocks = ConcurrentHashMap<String, Mutex>()
    suspend fun initialize() = initializeLock.withLock {
        catalogStore?.upgradeAiDefaults()
        if (catalogStore?.catalogVersion() != SourceCatalog.VERSION) {
            dao.insertSources(SourceCatalog.sources)
            catalogStore?.markCatalogVersion(SourceCatalog.VERSION)
        }
        if (dao.article(DemoContent.article.id) == null) dao.insertArticles(listOf(DemoContent.article))
    }

    data class RefreshResult(val articles: Int, val successes: Int, val failures: Int)
    suspend fun refresh(): RefreshResult = withContext(Dispatchers.IO) {
        var count = 0; var success = 0; var failures = 0
        for (source in dao.enabledSources()) {
            try {
                val items = NewsParsing.feed(http.fetchPublic(source.feedUrl), source)
                dao.insertArticles(items)
                dao.sourceResult(source.id, System.currentTimeMillis(), null)
                count += items.size; success++
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                failures++
                dao.sourceResult(source.id, System.currentTimeMillis(), e.localizedMessage ?: "订阅更新失败")
            }
        }
        RefreshResult(count, success, failures)
    }

    suspend fun ensureBody(id: String): Article = bodyLocks.getOrPut(id) { Mutex() }.withLock {
        val article = dao.article(id) ?: error("找不到文章")
        if (article.paragraphs().isNotEmpty()) return@withLock article
        try {
            val body = withContext(Dispatchers.IO) { NewsParsing.extract(http.fetchPublic(article.url), article.url) }
            val paragraphs = encodeParagraphs(body.paragraphs)
            dao.saveBody(id, paragraphs, digest(body.paragraphs.joinToString("\n")), body.author, body.image)
            dao.article(id) ?: error("文章读取失败")
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            dao.bodyFailed(id, e.localizedMessage ?: "正文提取失败，请打开原文")
            throw e
        }
    }

    suspend fun saveWord(article: Article, paragraph: Paragraph, insight: WordInsight) {
        dao.saveNote(StudyNote(
            id = digest("${article.id}|${paragraph.id}|${insight.term.lowercase()}"), articleId = article.id,
            articleTitle = article.title, paragraphId = paragraph.id, term = insight.term,
            meaning = insight.meaning, context = paragraph.text,
            insightJson = newsJson.encodeToString(insight),
            analysisJson = article.analysis()?.let { newsJson.encodeToString(it.forParagraph(paragraph.id)) }.orEmpty(),
        ))
    }

    suspend fun saveSentence(article: Article, paragraph: Paragraph) {
        val analysis = article.analysis()?.forParagraph(paragraph.id)
        dao.saveNote(StudyNote(
            id = digest("${article.id}|${paragraph.id}|sentence"), articleId = article.id, articleTitle = article.title,
            paragraphId = paragraph.id, term = paragraph.text,
            meaning = analysis?.translations?.firstOrNull()?.translation.orEmpty(),
            context = paragraph.text, kind = "paragraph",
            analysisJson = analysis?.let { newsJson.encodeToString(it) }.orEmpty(),
        ))
    }
}

fun ArticleAnalysis.forParagraph(id: String) = copy(
    translations = translations.filter { it.paragraphId == id },
    vocabulary = vocabulary.filter { it.paragraphId == id },
    sentences = sentences.filter { it.paragraphId == id },
)
