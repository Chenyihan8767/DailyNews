package com.dailynews.reader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dailynews.reader.AppContainer
import com.dailynews.reader.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class WordSelection(val article: Article, val paragraph: Paragraph, val term: String, val insight: WordInsight? = null,
    val loading: Boolean = false, val error: String? = null, val saved: Boolean = false)

data class TitleTranslation(val articleId: String, val translation: String = "", val loading: Boolean = false, val error: String? = null)

class NewsViewModel(val app: AppContainer) : ViewModel() {
    val articles = app.dao.observeArticles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sources = app.dao.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val notes = app.dao.observeNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())
    val hasKey = app.settings.hasKey.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedArticle = _selectedId.flatMapLatest { id -> if (id == null) flowOf(null) else app.dao.observeArticle(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selectedId = _selectedId.asStateFlow()
    val refreshing = MutableStateFlow(false)
    val loadingBody = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val word = MutableStateFlow<WordSelection?>(null)
    val savedParagraph = MutableStateFlow<String?>(null)
    val titleTranslation = MutableStateFlow<TitleTranslation?>(null)
    val connectionStatus = MutableStateFlow<String?>(null)
    private val wordCache = mutableMapOf<String, WordInsight>()

    init { viewModelScope.launch { app.repository.initialize() } }

    private fun launchAction(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { messages.emit(e.localizedMessage ?: "操作失败，请重试") }
    }

    fun refresh() {
        if (refreshing.value) return
        launchAction {
            refreshing.value = true
            try {
                val result = app.repository.refresh()
                messages.emit(if (result.successes == 0) "订阅更新失败，请在订阅页查看原因" else
                    "已更新 ${result.successes} 个订阅源${if (result.failures > 0) "，${result.failures} 个暂不可用" else ""}")
            } finally { refreshing.value = false }
        }
    }

    fun openArticle(id: String) {
        _selectedId.value = id
        word.value = null
        titleTranslation.value = null
        launchAction {
            loadingBody.value = true
            try {
                val current = app.dao.article(id) ?: return@launchAction
                app.dao.progress(id, current.readParagraph, System.currentTimeMillis())
                app.repository.ensureBody(id)
                // Opening a failed task must preserve its error until an explicit retry.
                if (settings.value.processOnOpen && current.processing in listOf("idle", "ready")) app.scheduler.prepare(id, 100)
            } finally { if (_selectedId.value == id) loadingBody.value = false }
        }
    }
    fun closeArticle() { _selectedId.value = null; word.value = null }
    fun prepare(id: String) = launchAction { app.scheduler.prepare(id, 100); messages.emit("已加入精读队列；已有结果将复用") }
    fun daily() = launchAction {
        val count = app.scheduler.prepareDaily()
        messages.emit(if (count > 0) "已选择 $count 篇今日报道准备精读" else "暂无今日报道，请先刷新订阅")
    }
    fun bookmark(article: Article) = launchAction {
        app.dao.bookmark(article.id, !article.bookmarked)
        messages.emit(if (article.bookmarked) "已取消收藏文章" else "已收藏文章")
        if (!article.bookmarked && settings.value.processOnBookmark) app.scheduler.prepare(article.id, 50)
    }
    fun progress(id: String, paragraph: Int) = launchAction { app.dao.progress(id, paragraph, System.currentTimeMillis()) }
    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) = launchAction { app.settings.update(transform) }

    fun saveApi(endpoint: String, model: String, key: String, maxOutputTokens: Int,
        chunkCharacters: Int, timeoutSeconds: Int, splitOnOutputLimit: Boolean) = launchAction {
        val url = endpoint.trim().toHttpUrlOrNull()
        require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
            "请输入有效的 HTTPS 接口地址，不含查询参数和账号"
        }
        require(model.isNotBlank()) { "请填写模型名称" }
        require(maxOutputTokens in 1..1_048_576) { "最大输出 tokens 需在 1–1048576 之间" }
        require(chunkCharacters in 500..1_000_000) { "分块字符数需在 500–1000000 之间" }
        require(timeoutSeconds in 30..3600) { "请求超时需在 30–3600 秒之间" }
        app.settings.update { it.copy(endpoint = endpoint.trim().trimEnd('/'), model = model.trim(),
            maxOutputTokens = maxOutputTokens, chunkCharacters = chunkCharacters,
            aiTimeoutSeconds = timeoutSeconds, splitOnOutputLimit = splitOnOutputLimit) }
        if (key.isNotBlank()) app.settings.saveApiKey(key)
        app.dao.resumeWaiting()
        app.scheduler.wake()
        connectionStatus.value = null
        messages.emit("API配置已保存")
    }
    fun clearKey() = launchAction { app.settings.saveApiKey(""); messages.emit("已清除本机 API Key") }
    fun testConnection() = launchAction {
        connectionStatus.value = "正在测试…"
        try { app.ai.testConnection(); connectionStatus.value = "连接成功 · JSON输出正常" }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { connectionStatus.value = e.localizedMessage ?: "连接失败" }
    }
    fun toggleSource(source: NewsSource) = launchAction { app.dao.toggleSource(source.id, !source.enabled) }
    fun deleteSource(source: NewsSource) = launchAction { app.dao.deleteSource(source.id) }
    fun addSource(name: String, url: String, category: Category, region: Region) = launchAction {
        require(name.isNotBlank()) { "请输入来源名称" }
        val canonical = NewsParsing.canonicalUrl(url)
        require(canonical != null && canonical.startsWith("https://")) { "订阅地址必须是有效的 HTTPS URL" }
        app.dao.saveSource(NewsSource(digest(canonical), name.trim(), canonical, category.name, region.name))
        messages.emit("订阅已添加，刷新后获取文章")
    }
    fun deleteNote(note: StudyNote) = launchAction { app.dao.deleteNote(note.id) }
    fun saveWord() = launchAction {
        val selection = word.value ?: return@launchAction
        val insight = selection.insight ?: return@launchAction
        app.repository.saveWord(selection.article, selection.paragraph, insight)
        if (word.value == selection) word.value = selection.copy(saved = true)
        messages.emit("已加入笔记，保留原句、分析和来源")
    }
    fun saveSentence(article: Article, paragraph: Paragraph) = launchAction {
        app.repository.saveSentence(article, paragraph); savedParagraph.value = "${article.id}:${paragraph.id}"; messages.emit("已收藏原文段落与分析")
    }
    val savedSentence = MutableStateFlow<String?>(null)
    fun saveAnalyzedSentence(article: Article, paragraph: Paragraph, sentence: SentenceInsight) = launchAction {
        app.dao.saveNote(sentenceNote(article, paragraph, sentence))
        savedSentence.value = "${article.id}:${paragraph.id}:${sentence.quote}"
        messages.emit("已收藏这句原文、译文与分析")
    }
    fun translateTitle(article: Article) = launchAction {
        if (titleTranslation.value?.let { it.articleId == article.id && (it.loading || it.translation.isNotBlank()) } == true) return@launchAction
        titleTranslation.value = TitleTranslation(article.id, loading = true)
        try {
            val key = digest("title-v1|${article.title}|${settings.value.endpoint}|${settings.value.model}")
            val translation = app.dao.cachedGloss(key)?.insightJson ?: app.ai.translateTitle(article.title).also {
                app.dao.saveGloss(CachedGloss(key, it))
            }
            if (selectedId.value == article.id) titleTranslation.value = TitleTranslation(article.id, translation)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (selectedId.value == article.id) titleTranslation.value = TitleTranslation(article.id, error = e.localizedMessage ?: "标题翻译失败")
        }
    }
    fun selectWord(article: Article, paragraph: Paragraph, term: String) {
        val cached = article.analysis()?.vocabulary?.filter { it.paragraphId == paragraph.id && it.term.equals(term, true) }
            ?.firstOrNull() ?: wordCache[wordKey(article, paragraph, term)]
        val selection = WordSelection(article, paragraph, term, cached)
        word.value = selection
        if (cached == null) explainSelection(selection)
    }
    private fun wordKey(article: Article, paragraph: Paragraph, term: String) =
        digest("${article.contentHash}|${paragraph.id}|$term|${settings.value.endpoint}|${settings.value.model}")
    private fun explainSelection(selection: WordSelection) = viewModelScope.launch {
        word.value = selection.copy(loading = true)
        try {
            val key = wordKey(selection.article, selection.paragraph, selection.term)
            val cached = app.dao.cachedGloss(key)?.let { runCatching { newsJson.decodeFromString<WordInsight>(it.insightJson) }.getOrNull() }
            val insight = cached ?: run {
                if (!hasKey.value) {
                    if (word.value?.term == selection.term) word.value = selection
                    return@launch
                }
                app.ai.explainWord(selection.term, selection.paragraph).also {
                    app.dao.saveGloss(CachedGloss(key, newsJson.encodeToString(it)))
                }
            }
            wordCache[key] = insight
            if (word.value?.term == selection.term && word.value?.paragraph?.id == selection.paragraph.id &&
                word.value?.article?.id == selection.article.id) word.value = selection.copy(insight = insight)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (word.value?.term == selection.term && word.value?.article?.id == selection.article.id)
                word.value = selection.copy(error = e.localizedMessage ?: "词义查询失败")
        }
    }
    fun retryWord() { word.value?.let(::explainSelection) }

    class Factory(private val app: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = NewsViewModel(app) as T
    }
}
