@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dailynews.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dailynews.reader.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun NewsApp(model: NewsViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var collection by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = collection != null) { collection = null }
    val selectedId by model.selectedId.collectAsStateWithLifecycle()
    val selected by model.selectedArticle.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model) { model.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(enabled = selectedId != null) { model.closeArticle() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = if (selectedId != null) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selectedId == null && collection == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                listOf("今日" to Icons.Default.Public, "订阅" to Icons.Default.RssFeed,
                    "笔记" to Icons.AutoMirrored.Filled.MenuBook, "设置" to Icons.Default.Tune).forEachIndexed { index, item ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index },
                        icon = { Icon(item.second, contentDescription = null) }, label = { Text(item.first) })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            if (selectedId != null) {
                if (selected != null) ReaderScreen(selected!!, model, onSettings = { model.closeArticle(); collection = null; tab = 3 })
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (collection != null) {
                ArticleCollectionScreen(model, collection!!, onBack = { collection = null })
            } else when (tab) {
                0 -> TodayScreen(model, onSettings = { tab = 3 }, onCollection = { collection = it })
                1 -> SourcesScreen(model)
                2 -> NotesScreen(model)
                else -> SettingsScreen(model)
            }
        }
    }
}

@Composable private fun TodayScreen(model: NewsViewModel, onSettings: () -> Unit, onCollection: (String) -> Unit) {
    val articles by model.articles.collectAsStateWithLifecycle()
    val refreshing by model.refreshing.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var region by rememberSaveable { mutableStateOf<String?>(null) }
    var filterPicker by remember { mutableStateOf<String?>(null) }
    val realArticles = articles.filterNot { it.isDemo }
    val displayed = (if (realArticles.isEmpty()) articles else realArticles).filter {
        (category == null || it.category == category) &&
            (region == null || Region.valueOf(region!!).includes(it.region)) &&
            (query.isBlank() || "${it.title} ${it.description} ${it.sourceName}".contains(query, true))
    }
    val queued = articles.count { it.processing in listOf("queued", "running", "waiting_key") }
    LazyColumn(Modifier.testTag("home-list"), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("DAILYNEWS", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = model::refresh, enabled = !refreshing) {
                    if (refreshing) CircularProgressIndicator(Modifier.size(21.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "刷新订阅")
                }
                FilledTonalIconButton(onClick = onSettings, shape = CircleShape) { Icon(Icons.Default.Tune, "阅读设置") }
            }
        }
        item {
            Text(Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)).uppercase(),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text("Read the\nworld.", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(10.dp))
            Text("了解世界，也读懂英语。", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(onClick = { onCollection("prepared") }, modifier = Modifier.weight(1f).testTag("prepared-list-entry"),
                    color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Text("精读列表", style = MaterialTheme.typography.titleSmall)
                        Text(if (queued > 0) "$queued 篇待处理或处理中" else "查看精读文章", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Surface(onClick = { onCollection("saved") }, modifier = Modifier.weight(1f).testTag("saved-list-entry"),
                    color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BookmarkBorder, null, tint = MaterialTheme.colorScheme.primary)
                        Text("文章收藏", style = MaterialTheme.typography.titleSmall)
                        Text("${articles.count { it.bookmarked }} 篇已收藏", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("搜索标题、来源") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(24.dp))
        }
        item {
            LazyRow(Modifier.testTag("news-filters"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(category == null && region == null, { category = null; region = null }, label = { Text("全部") }) }
                item { FilterChip(category != null, { filterPicker = "topic" },
                    label = { Text("主题 · ${category?.let { Category.valueOf(it).label } ?: "不限"}") },
                    trailingIcon = { Icon(Icons.Default.ExpandMore, null) }) }
                item { FilterChip(region != null, { filterPicker = "region" },
                    label = { Text("地区 · ${region?.let { Region.valueOf(it).label } ?: "不限"}") },
                    trailingIcon = { Icon(Icons.Default.ExpandMore, null) }) }
            }
        }
        if (realArticles.isEmpty() && query.isBlank() && category == null && region == null) item {
            Text("欢迎。先试读教学样文，或点击右上角刷新，获取真实新闻。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (displayed.isNotEmpty()) {
            item { SectionLabel(if (realArticles.isEmpty()) "试读 · SAMPLE" else "最新报道 · LATEST") }
            item { HeroArticle(displayed.first(), { model.openArticle(displayed.first().id) }, { model.bookmark(displayed.first()) }) }
            items(displayed.drop(1), key = { it.id }) { article ->
                ArticleRow(article, onClick = { model.openArticle(article.id) }, bookmark = { model.bookmark(article) })
            }
        } else item { EmptyState(Icons.Default.SearchOff, "没有匹配的文章", "试试其他分类，或刷新订阅。") }
        if (realArticles.isNotEmpty()) item {
            TextButton(onClick = { model.openArticle(DemoContent.article.id) }) { Text("体验原创教学样文") }
        }
    }
    if (filterPicker != null) {
        val topicPicker = filterPicker == "topic"
        val choices = listOf<String?>(null) + if (topicPicker) Category.entries.filter { it != Category.GENERAL }.map { it.name }
            else Region.entries.filter { it != Region.GLOBAL }.map { it.name }
        AlertDialog(onDismissRequest = { filterPicker = null }, title = { Text(if (topicPicker) "选择主题" else "选择地区") },
            text = { LazyColumn { items(choices.size) { index ->
                val value = choices[index]
                val label = if (value == null) "不限" else if (topicPicker) Category.valueOf(value).label else Region.valueOf(value).label
                Row(Modifier.fillMaxWidth().clickable {
                    if (topicPicker) category = value else region = value
                    filterPicker = null
                }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = value == if (topicPicker) category else region, onClick = {
                        if (topicPicker) category = value else region = value
                        filterPicker = null
                    })
                    Text(label)
                }
            } } }, confirmButton = { TextButton(onClick = { filterPicker = null }) { Text("关闭") } })
    }
}

@Composable private fun ArticleCollectionScreen(model: NewsViewModel, collection: String, onBack: () -> Unit) {
    val articles by model.articles.collectAsStateWithLifecycle()
    val prepared = collection == "prepared"
    val displayed = if (prepared) articles.filter { !it.isDemo && it.processing != "idle" }
        .sortedWith(compareBy<Article> { when (it.processing) { "running" -> 0; "queued" -> 1; "waiting_key" -> 2; "failed" -> 3; else -> 4 } }.thenByDescending { it.queuedAt })
        else articles.filter { it.bookmarked }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(if (prepared) "精读列表" else "文章收藏") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回今日") } })
        LazyColumn(Modifier.testTag("collection-list"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (prepared) item { FilledTonalButton(onClick = model::daily) { Icon(Icons.Default.Add, null); Text("准备今日精选") } }
            item { Text("共 ${displayed.size} 篇", style = MaterialTheme.typography.labelMedium) }
            if (displayed.isEmpty()) item { EmptyState(if (prepared) Icons.Default.AutoAwesome else Icons.Default.BookmarkBorder,
                if (prepared) "暂无精读文章" else "暂无收藏文章", if (prepared) "准备今日精选，或在阅读时加入精读。" else "点击文章上的收藏图标，保存在这里。") }
            items(displayed, key = { it.id }) { article ->
                ArticleRow(article, { model.openArticle(article.id) }, { model.bookmark(article) })
            }
        }
    }
}

@Composable fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun HeroArticle(article: Article, onClick: () -> Unit, bookmark: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (article.imageUrl != null) AsyncImage(article.imageUrl, null, Modifier.fillMaxWidth().height(166.dp), contentScale = ContentScale.Crop)
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(article.categoryValue().english.uppercase(), Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = bookmark) { Icon(if (article.bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "收藏文章") }
            }
            Text(article.title, style = MaterialTheme.typography.headlineMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Text(article.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(article.sourceName, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(if (article.isDemo) "教学样文" else article.contentType, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable private fun ArticleRow(article: Article, onClick: () -> Unit, bookmark: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("${article.categoryValue().label} · ${article.regionValue().label} · ${article.sourceName}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(7.dp))
                Text(article.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Text(articleMeta(article), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (article.processing == "failed") Text(article.processingError ?: "处理失败，点击文章查看并重试",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            if (article.imageUrl != null) AsyncImage(article.imageUrl, null, Modifier.size(80.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
            else Surface(Modifier.size(64.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(categoryIcon(article.categoryValue()), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = bookmark, modifier = Modifier.size(40.dp)) { Icon(if (article.bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "收藏文章", Modifier.size(20.dp)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
    }
}

fun articleMeta(article: Article): String {
    val time = if (article.publishedAt > 0) Instant.ofEpochMilli(article.publishedAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) else "时间未提供"
    val state = when (article.processing) { "ready" -> "辅助已就绪"; "queued" -> "等待处理"; "running" -> "正在处理"; "waiting_key" -> "待配置API"; "failed" -> "AI处理失败"; else -> article.contentType }
    return "$time · $state"
}

fun categoryIcon(category: Category): ImageVector = when (category) {
    Category.POLITICS -> Icons.Default.Public
    Category.ECONOMY -> Icons.Default.TrendingUp
    Category.SECURITY -> Icons.Default.Shield
    Category.CULTURE -> Icons.Default.Museum
    Category.GENERAL -> Icons.Default.Public
    Category.TECHNOLOGY -> Icons.Default.Computer
    Category.SCIENCE -> Icons.Default.Science
    Category.HEALTH -> Icons.Default.HealthAndSafety
    Category.ENVIRONMENT -> Icons.Default.Eco
    Category.SOCIETY -> Icons.Default.People
    Category.SPORTS -> Icons.Default.SportsSoccer
    Category.LIFE -> Icons.Default.Coffee
}

@Composable fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun SourcesScreen(model: NewsViewModel) {
    val sources by model.sources.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    LazyColumn(Modifier.testTag("sources-list"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeader("Your sources.", "订阅", "多一种来源，多一个观察世界的角度。") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("添加RSS") }
                OutlinedButton(onClick = model::refresh) { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("刷新全部") }
            }
        }
        items(sources, key = { it.id }) { source ->
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RssFeed, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(source.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(source.enabled, { model.toggleSource(source) })
                    }
                    Text("${runCatching { Category.valueOf(source.category).label }.getOrDefault("综合")} · ${runCatching { Region.valueOf(source.region).label }.getOrDefault("全球")}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(source.feedUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(source.error ?: if (source.lastSync > 0) "已同步 · ${Instant.ofEpochMilli(source.lastSync).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}" else "等待首次同步",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = if (source.error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        IconButton(onClick = { model.deleteSource(source) }) { Icon(Icons.Default.DeleteOutline, "移除订阅", Modifier.size(20.dp)) }
                    }
                }
            }
        }
        item { Text("订阅用于发现新闻；正文可用性取决于原网站。报道、评论和直播分别标注，全文不可获取时仍可打开原文。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (adding) AddSourceDialog(onDismiss = { adding = false }) { name, url, category, region ->
        model.addSource(name, url, category, region); adding = false
    }
}

@Composable private fun AddSourceDialog(onDismiss: () -> Unit, onAdd: (String, String, Category, Region) -> Unit) {
    var name by remember { mutableStateOf("") }; var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.GENERAL) }
    var region by remember { mutableStateOf(Region.GLOBAL) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加订阅") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("来源名称") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("HTTPS RSS / Atom 地址") }, singleLine = true)
                ChoiceRow("默认地区", region.label, Region.entries.map { it to it.label }) { region = it }
                Text("默认主题（文章会按关键词细分）", style = MaterialTheme.typography.bodySmall)
                Column { Category.entries.forEach { value ->
                    Row(Modifier.fillMaxWidth().clickable { category = value }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(category == value, { category = value }); Text(value.label)
                    }
                } }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, url, category, region) }, enabled = name.isNotBlank() && url.startsWith("https://")) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable private fun NotesScreen(model: NewsViewModel) {
    val notes by model.notes.collectAsStateWithLifecycle()
    var sentencesOnly by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val displayed = notes.filter { !it.isWord() == sentencesOnly }
    LazyColumn(Modifier.testTag("notes-list"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("Keep the words.", "笔记", "让每一个表达，都有它的上下文。") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!sentencesOnly, { sentencesOnly = false }, label = { Text("单词与短语") })
                FilterChip(sentencesOnly, { sentencesOnly = true }, label = { Text("句子与段落") })
            }
        }
        if (displayed.isEmpty()) item { EmptyState(Icons.Default.Bookmarks, "还没有笔记", "阅读时点词收藏，或保存值得细读的段落。") }
        items(displayed, key = { it.id }) { note ->
            Surface(onClick = { detailId = note.id }, modifier = Modifier.testTag("note-${note.id}"),
                shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(note.term, Modifier.weight(1f), style = if (note.isWord()) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                            maxLines = 5, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { model.deleteNote(note) }, Modifier.size(40.dp)) { Icon(Icons.Default.DeleteOutline, "删除笔记") }
                    }
                    if (note.meaning.isNotBlank()) Text(note.meaning, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (note.showSeparateContext()) Text(note.context, style = MaterialTheme.typography.bodyMedium, maxLines = 3,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点击查看释义、原句与分析", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(note.articleTitle, Modifier.fillMaxWidth().clickable { model.openArticle(note.articleId) }.padding(vertical = 8.dp),
                        maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    val note = notes.firstOrNull { it.id == detailId }
    if (note != null) NoteDetailSheet(note, model, onDismiss = { detailId = null })
}

@Composable fun PageHeader(english: String, chinese: String, subtitle: String) {
    Column(Modifier.padding(top = 18.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(chinese.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(english, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
