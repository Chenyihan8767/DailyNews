@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dailynews.reader.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dailynews.reader.data.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

object ReaderText {
    fun termAt(text: String, offset: Int, vocabulary: List<WordInsight>): String? {
        if (offset !in text.indices || !text[offset].isLetter()) return null
        vocabulary.sortedByDescending { it.term.length }.forEach { insight ->
            val match = Regex("(?<![\\p{L}])${Regex.escape(insight.term)}(?![\\p{L}])", RegexOption.IGNORE_CASE)
                .findAll(text).firstOrNull { offset in it.range }
            if (match != null) return match.value
        }
        return Regex("[\\p{L}]+(?:['’\\-][\\p{L}]+)*").findAll(text).firstOrNull { offset in it.range }?.value
    }
}

@Composable fun ReaderScreen(article: Article, model: NewsViewModel, onSettings: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val loading by model.loadingBody.collectAsStateWithLifecycle()
    val word by model.word.collectAsStateWithLifecycle()
    val hasKey by model.hasKey.collectAsStateWithLifecycle()
    val titleTranslation by model.titleTranslation.collectAsStateWithLifecycle()
    val savedParagraph by model.savedParagraph.collectAsStateWithLifecycle()
    val savedSentence by model.savedSentence.collectAsStateWithLifecycle()
    val analysis = remember(article.analysisJson) { article.analysis() }
    val paragraphs = remember(article.paragraphsJson) { article.paragraphs() }
    val listState = rememberLazyListState()
    var showSummary by rememberSaveable(article.id) { mutableStateOf(false) }
    var showDetails by remember(article.id) { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(IntOffset.Zero) }
    var paragraphDetail by remember { mutableStateOf<Paragraph?>(null) }
    val translated = remember(article.id) { mutableStateMapOf<String, Boolean>() }
    val context = LocalContext.current
    val chromeState = rememberTopAppBarState()
    val chromeBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(chromeState)
    val controlsVisible by remember { derivedStateOf { chromeState.collapsedFraction < .5f } }
    var menuExpanded by remember(article.id) { mutableStateOf(false) }
    LaunchedEffect(article.id, paragraphs.size) {
        if (paragraphs.isNotEmpty() && article.readParagraph > 0) listState.scrollToItem((article.readParagraph + 2).coerceAtMost(paragraphs.size + 1))
    }
    LaunchedEffect(article.id) {
        snapshotFlow { (listState.firstVisibleItemIndex - 2).coerceAtLeast(0) }.distinctUntilChanged().debounce(700).collect {
            model.progress(article.id, it)
        }
    }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) model.word.value = null }
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(chromeBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        // Each system inset belongs to one layer: top bar, bottom surface, or scrolling content.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                modifier = Modifier.testTag("reader-toolbar"),
                title = { Text(article.sourceName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                expandedHeight = 56.dp,
                navigationIcon = { IconButton(onClick = model::closeArticle) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回新闻列表") } },
                actions = {
                    IconButton(onClick = { model.bookmark(article) }) {
                        Icon(if (article.bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "收藏文章")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "更多阅读选项") }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (!article.isDemo) DropdownMenuItem(text = { Text("打开原文") }, onClick = {
                                menuExpanded = false
                                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.url))) }
                                catch (_: ActivityNotFoundException) { model.messages.tryEmit("没有找到可用的浏览器") }
                            }, leadingIcon = { Icon(Icons.Default.OpenInNew, null) })
                            DropdownMenuItem(text = { Text("阅读设置") }, onClick = { menuExpanded = false; onSettings() },
                                leadingIcon = { Icon(Icons.Default.Tune, null) })
                        }
                    }
                },
                scrollBehavior = chromeBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = controlsVisible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
                        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().align(Alignment.Center).testTag("reader-actions")
                            .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton(onClick = { showSummary = true }, modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)) {
                                Text("文章摘要", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { model.prepare(article.id) }, modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp), enabled = article.processing != "running") {
                                Text(if (article.processing == "running") "处理中" else "准备精读", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("reader-list"),
            state = listState, contentPadding = PaddingValues(
                start = padding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current) + 24.dp,
                end = padding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current) + 24.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 36.dp + if (controlsVisible) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${article.categoryValue().english.uppercase()} / ${article.contentType}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(article.title, Modifier.testTag("reader-title").clickable { model.translateTitle(article) }, style = MaterialTheme.typography.headlineLarge)
                    TextButton(onClick = { model.translateTitle(article) }) { Text("翻译标题") }
                    titleTranslation?.takeIf { it.articleId == article.id }?.let { title ->
                        if (title.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (title.translation.isNotBlank()) SelectionContainer { Text(title.translation, style = MaterialTheme.typography.titleLarge) }
                        if (title.error != null) Text(title.error, color = MaterialTheme.colorScheme.error)
                    }
                    Text(if (article.isDemo) "原创教学样文 · 非实时新闻 · ${article.minutes()} min read" else "${articleMeta(article)} · ${article.minutes()} min read",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (article.imageUrl != null) AsyncImage(article.imageUrl, null, Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
                    if (article.author.isNotBlank()) Text(article.author, style = MaterialTheme.typography.bodySmall)
                }
            }
            item(key = "status") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("点词查看语境释义 · 长按选择文本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        loading && paragraphs.isEmpty() -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        article.bodyError != null -> {
                            Text(article.bodyError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { model.openArticle(article.id) }) { Text("重试读取正文") }
                        }
                        article.processing == "running" -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("正在准备翻译和词句分析，可以先读英文。", style = MaterialTheme.typography.bodySmall)
                        }
                        article.processing == "queued" -> Text("已加入处理队列${if (settings.wifiOnly) "，后台处理需连接Wi-Fi" else ""}。", style = MaterialTheme.typography.bodySmall)
                        article.processing == "waiting_key" -> TextButton(onClick = onSettings) { Text("配置 DeepSeek，启用翻译与词句分析") }
                        article.processing == "failed" -> {
                            Text(article.processingError ?: "处理失败", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { model.prepare(article.id) }) { Text("重试AI处理") }
                        }
                    }
                    if (paragraphs.isEmpty() && !loading) Text(article.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            itemsIndexed(paragraphs, key = { _, p -> p.id }) { index, paragraph ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val vocabulary = analysis?.vocabulary?.filter { it.paragraphId == paragraph.id }.orEmpty()
                    val text = remember(paragraph.text, vocabulary, settings.showHighlights) {
                        buildAnnotatedString {
                            append(paragraph.text)
                            if (settings.showHighlights) vocabulary.forEach { insight ->
                                Regex(Regex.escape(insight.term), RegexOption.IGNORE_CASE).findAll(paragraph.text).forEach {
                                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), it.range.first, it.range.last + 1)
                                }
                            }
                        }
                    }
                    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                    var position by remember { mutableStateOf(Offset.Zero) }
                    SelectionContainer {
                        ClickableText(text = text, modifier = Modifier.fillMaxWidth().testTag("paragraph-${paragraph.id}").onGloballyPositioned { position = it.positionInWindow() },
                            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.SansSerif,
                                fontSize = settings.fontSize.sp, lineHeight = (settings.fontSize * settings.lineHeight).sp,
                                color = MaterialTheme.colorScheme.onSurface),
                            onTextLayout = { layout = it }, onClick = { offset ->
                                val term = ReaderText.termAt(paragraph.text, offset, vocabulary)
                                if (term != null) {
                                    val rect = layout?.getBoundingBox(offset)
                                    anchor = IntOffset((position.x + (rect?.left ?: 0f)).roundToInt(), (position.y + (rect?.bottom ?: 0f)).roundToInt())
                                    showDetails = settings.glossMode == GlossMode.SHEET
                                    model.selectWord(article, paragraph, term)
                                }
                            })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}".padStart(2, '0'), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f))
                        TextButton(onClick = { paragraphDetail = paragraph }) { Icon(Icons.Default.AccountTree, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("词句") }
                        TextButton(onClick = {
                            translated[paragraph.id] = translated[paragraph.id] != true
                            if (analysis?.translations?.none { it.paragraphId == paragraph.id } != false) model.prepare(article.id)
                        }) { Icon(Icons.Default.Translate, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(if (translated[paragraph.id] == true) "收起" else "翻译") }
                    }
                    if (translated[paragraph.id] == true) Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("中文译文 · AI辅助", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            SelectionContainer { Text(analysis?.translations?.firstOrNull { it.paragraphId == paragraph.id }?.translation
                                ?: "翻译尚未就绪，完成预处理后会显示在这里。", style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
            }
            if (paragraphs.isNotEmpty()) item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(18.dp))
                Text("${article.sourceName} · ${if (article.isDemo) "教学示例" else "原文版权归原作者及媒体所有"}\nAI翻译与解释可能存在误差，请结合原文判断。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val selection = word?.takeIf { it.article.id == article.id }
    if (selection != null && !showDetails) {
        WordPopup(selection, anchor, allowDetails = settings.glossMode == GlossMode.COMBINED,
            onDismiss = { model.word.value = null }, onDetails = { showDetails = true }, onSave = model::saveWord,
            onRetry = model::retryWord, hasKey = hasKey, onSettings = { model.word.value = null; onSettings() })
    }
    if (selection != null && showDetails) ModalBottomSheet(onDismissRequest = { model.word.value = null; showDetails = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(horizontal = 26.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            WordContent(selection, detailed = true, hasKey, model::retryWord, onSettings)
            FilledTonalButton(onClick = model::saveWord, enabled = selection.insight != null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text(if (selection.saved) "已加入笔记" else "加入笔记")
            }
        }
    }
    if (showSummary) ModalBottomSheet(onDismissRequest = { showSummary = false }) {
        Column(Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(horizontal = 26.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("The brief.", style = MaterialTheme.typography.headlineMedium)
            if (!analysis?.summaryEn.isNullOrBlank()) {
                SelectionContainer { Text(analysis!!.summaryEn, style = MaterialTheme.typography.bodyLarge) }
                HorizontalDivider()
                Text(analysis!!.summaryZh, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("AI概括，请以原文为准", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("摘要尚未生成。准备精读后，可查看中英文摘要。")
                FilledTonalButton(onClick = { model.prepare(article.id); showSummary = false }) { Text("生成摘要与翻译") }
            }
        }
    }
    paragraphDetail?.let { paragraph ->
        ModalBottomSheet(onDismissRequest = { paragraphDetail = null }) {
            LazyColumn(contentPadding = PaddingValues(start = 26.dp, end = 26.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("Read it closely.", style = MaterialTheme.typography.headlineMedium) }
                val sentences = analysis?.sentences?.filter { it.paragraphId == paragraph.id }.orEmpty()
                val vocabulary = analysis?.vocabulary?.filter { it.paragraphId == paragraph.id }.orEmpty()
                itemsIndexed(sentences) { _, sentence ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("长难句", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        SelectionContainer { Text(sentence.quote, style = MaterialTheme.typography.bodyLarge) }
                        Text(sentence.translation)
                        Text(sentence.structure, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { model.saveAnalyzedSentence(article, paragraph, sentence) }) {
                            Text(if (savedSentence == "${article.id}:${paragraph.id}:${sentence.quote}") "已收藏这句" else "收藏这句原文与分析")
                        }
                    }
                }
                itemsIndexed(vocabulary) { _, insight ->
                    Surface(onClick = {
                        paragraphDetail = null; showDetails = true; model.selectWord(article, paragraph, insight.term)
                    }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(insight.term, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            Text(insight.meaning)
                        }
                    }
                }
                if (sentences.isEmpty() && vocabulary.isEmpty()) item {
                    Text("这一段暂无词句分析。你可以先保存原文，或准备精读后再看。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { model.prepare(article.id); paragraphDetail = null }) { Text("准备词句分析") }
                }
                item {
                    FilledTonalButton(onClick = { model.saveSentence(article, paragraph) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (savedParagraph == "${article.id}:${paragraph.id}") "已收藏原文与分析" else "收藏这段原文与分析")
                    }
                }
            }
        }
    }
}

@Composable private fun WordPopup(selection: WordSelection, anchor: IntOffset, allowDetails: Boolean,
    onDismiss: () -> Unit, onDetails: () -> Unit, onSave: () -> Unit, onRetry: () -> Unit, hasKey: Boolean, onSettings: () -> Unit) {
    val positioner = remember(anchor) { object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
            val x = anchor.x.coerceIn(12, (windowSize.width - popupContentSize.width - 12).coerceAtLeast(12))
            val below = anchor.y + 16
            val y = if (below + popupContentSize.height < windowSize.height - 20) below else (anchor.y - popupContentSize.height - 36).coerceAtLeast(20)
            return IntOffset(x, y)
        }
    } }
    Popup(popupPositionProvider = positioner, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(Modifier.widthIn(max = 300.dp).padding(4.dp), shape = RoundedCornerShape(24.dp), shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WordContent(selection, detailed = false, hasKey, onRetry, onSettings)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onSave, enabled = selection.insight != null, modifier = Modifier.weight(1f)) { Text(if (selection.saved) "已收藏" else "收藏") }
                    if (allowDetails) TextButton(onClick = onDetails, modifier = Modifier.weight(1f)) { Text("详情") }
                }
            }
        }
    }
}

@Composable private fun WordContent(selection: WordSelection, detailed: Boolean, hasKey: Boolean, onRetry: () -> Unit, onSettings: () -> Unit) {
    val insight = selection.insight
    Text(selection.term, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    if (selection.loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text("正在结合原句解释…", style = MaterialTheme.typography.bodySmall)
    } else if (insight != null) {
        if (insight.pronunciation.isNotBlank() || insight.partOfSpeech.isNotBlank()) Text("${insight.pronunciation}  ${insight.partOfSpeech}".trim(),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(insight.meaning, style = MaterialTheme.typography.bodyLarge)
        if (detailed) {
            if (insight.explanation.isNotBlank()) Text(insight.explanation, style = MaterialTheme.typography.bodyMedium)
            Text("原文语境", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            SelectionContainer { Text(selection.paragraph.text, style = MaterialTheme.typography.bodyLarge) }
            Text("${selection.article.sourceName} · AI辅助释义", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Text(selection.error ?: if (!hasKey) "暂无离线释义，请配置 DeepSeek 后查询。" else "尚未查询语境释义。", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = if (hasKey) onRetry else onSettings) { Text(if (hasKey) "重新查询" else "配置API") }
    }
}
