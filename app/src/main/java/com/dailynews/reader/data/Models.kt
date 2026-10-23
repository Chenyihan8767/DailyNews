package com.dailynews.reader.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

val newsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

enum class Category(val label: String, val english: String) {
    GENERAL("综合", "General"), POLITICS("政治", "Politics"), ECONOMY("经济", "Economy"),
    SECURITY("军事安全", "Security"), CULTURE("文化", "Culture"),
    TECHNOLOGY("科技", "Technology"), SCIENCE("科学", "Science"), HEALTH("健康", "Health"),
    ENVIRONMENT("环境", "Environment"), SOCIETY("社会", "Society"), SPORTS("体育", "Sports"), LIFE("生活", "Life")
}
enum class Region(val label: String) {
    GLOBAL("全球"), CHINA_MAINLAND("中国大陆"), HONG_KONG_MACAO_TAIWAN("港澳台"),
    EUROPE("欧洲"), USA("美国"), CANADA("加拿大"), ASIA_PACIFIC("亚太"), ASIA("亚洲"),
    MIDDLE_EAST("中东"), LATIN_AMERICA("拉美"), AFRICA("非洲"), OCEANIA("大洋洲");

    fun includes(value: String): Boolean = value == name || when (this) {
        ASIA -> value in setOf(CHINA_MAINLAND.name, HONG_KONG_MACAO_TAIWAN.name, MIDDLE_EAST.name)
        ASIA_PACIFIC -> ASIA.includes(value) || value == OCEANIA.name
        else -> false
    }
}
enum class GlossMode(val label: String) { POPUP("快速浮卡"), SHEET("底部面板"), COMBINED("组合模式") }
enum class Appearance(val label: String) { SYSTEM("跟随系统"), LIGHT("米白浅色"), DARK("暖黑深色") }

@Entity(tableName = "sources")
data class NewsSource(
    @PrimaryKey val id: String,
    val name: String,
    val feedUrl: String,
    val category: String = Category.GENERAL.name,
    @ColumnInfo(defaultValue = "'GLOBAL'") val region: String = Region.GLOBAL.name,
    val enabled: Boolean = true,
    val lastSync: Long = 0,
    val error: String? = null,
)

@Entity(tableName = "articles")
data class Article(
    @PrimaryKey val id: String,
    val sourceId: String,
    val sourceName: String,
    val url: String,
    val title: String,
    val description: String = "",
    val imageUrl: String? = null,
    val publishedAt: Long = 0,
    val category: String = Category.GENERAL.name,
    @ColumnInfo(defaultValue = "'GLOBAL'") val region: String = Region.GLOBAL.name,
    val contentType: String = "报道",
    val author: String = "",
    val paragraphsJson: String = "[]",
    val contentHash: String = "",
    val analysisJson: String? = null,
    val analysisKey: String = "",
    val bookmarked: Boolean = false,
    val lastRead: Long = 0,
    val readParagraph: Int = 0,
    val bodyError: String? = null,
    val processing: String = "idle",
    val priority: Int = 0,
    val queuedAt: Long = 0,
    val processingError: String? = null,
    val isDemo: Boolean = false,
) {
    fun paragraphs(): List<Paragraph> = runCatching {
        newsJson.decodeFromString<List<Paragraph>>(paragraphsJson)
    }.getOrDefault(emptyList())
    fun analysis(): ArticleAnalysis? = analysisJson?.let {
        runCatching { newsJson.decodeFromString<ArticleAnalysis>(it) }.getOrNull()
    }
    fun categoryValue() = runCatching { Category.valueOf(category) }.getOrDefault(Category.GENERAL)
    fun regionValue() = runCatching { Region.valueOf(region) }.getOrDefault(Region.GLOBAL)
    fun minutes() = ((paragraphs().sumOf { it.text.split(Regex("\\s+")).size }
        .takeIf { it > 0 } ?: 750) / 180).coerceAtLeast(1)
}

@Serializable data class Paragraph(val id: String, val text: String)
@Serializable data class ParagraphTranslation(val paragraphId: String, val translation: String)
@Serializable data class WordInsight(
    val paragraphId: String,
    val term: String,
    val meaning: String,
    val lemma: String = "",
    val partOfSpeech: String = "",
    val pronunciation: String = "",
    val explanation: String = "",
)
@Serializable data class SentenceInsight(
    val paragraphId: String,
    val quote: String,
    val translation: String,
    val structure: String,
)
@Serializable data class ArticleAnalysis(
    val summaryEn: String = "",
    val summaryZh: String = "",
    val translations: List<ParagraphTranslation> = emptyList(),
    val vocabulary: List<WordInsight> = emptyList(),
    val sentences: List<SentenceInsight> = emptyList(),
)

@Entity(tableName = "notes")
data class StudyNote(
    @PrimaryKey val id: String,
    val articleId: String,
    val articleTitle: String,
    val paragraphId: String,
    val term: String,
    val meaning: String,
    val context: String,
    val kind: String = "word",
    @ColumnInfo(defaultValue = "''") val insightJson: String = "",
    @ColumnInfo(defaultValue = "''") val analysisJson: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "gloss_cache")
data class CachedGloss(@PrimaryKey val id: String, val insightJson: String)

@Serializable data class ReaderSettings(
    val appearance: Appearance = Appearance.SYSTEM,
    val dynamicColor: Boolean = false,
    val glossMode: GlossMode = GlossMode.COMBINED,
    val fontSize: Int = 19,
    val lineHeight: Float = 1.65f,
    val showHighlights: Boolean = false,
    val processOnOpen: Boolean = true,
    val processOnBookmark: Boolean = true,
    val dailyEnabled: Boolean = true,
    val dailyCount: Int = 5,
    val dailyHour: Int = 8,
    val wifiOnly: Boolean = true,
    val endpoint: String = "https://api.deepseek.com",
    val model: String = "deepseek-flash",
    val maxOutputTokens: Int = 393216,
    val chunkCharacters: Int = 2400,
    val aiTimeoutSeconds: Int = 210,
    val splitOnOutputLimit: Boolean = true,
)

fun encodeParagraphs(texts: List<String>): String = newsJson.encodeToString(
    texts.mapIndexed { index, text -> Paragraph("p${index + 1}", text) }
)

fun StudyNote.insight(): WordInsight? = runCatching { newsJson.decodeFromString<WordInsight>(insightJson) }.getOrNull()
fun StudyNote.analysis(): ArticleAnalysis? = runCatching { newsJson.decodeFromString<ArticleAnalysis>(analysisJson) }.getOrNull()
fun StudyNote.isWord() = kind == "word"
fun StudyNote.showSeparateContext() = isWord() && context.isNotBlank() && context.trim() != term.trim()
