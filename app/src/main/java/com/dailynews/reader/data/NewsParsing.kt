package com.dailynews.reader.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object NewsParsing {
    fun canonicalUrl(raw: String): String? {
        val url = raw.trim().toHttpUrlOrNull() ?: return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val builder = url.newBuilder().fragment(null)
        url.queryParameterNames.filter {
            it.startsWith("utm_", true) || it.lowercase() in setOf("cmp", "traffic_source", "fbclid", "gclid")
        }.forEach(builder::removeAllQueryParameters)
        return builder.build().toString()
    }

    fun feed(xml: String, source: NewsSource): List<Article> {
        val document = Jsoup.parse(xml, source.feedUrl, Parser.xmlParser())
        val items = document.getElementsByTag("item").ifEmpty { document.getElementsByTag("entry") }
        if (document.getElementsByTag("rss").isEmpty() && document.getElementsByTag("feed").isEmpty() &&
            document.getElementsByTag("rdf:RDF").isEmpty()) throw IllegalArgumentException("该地址未返回 RSS / Atom 订阅内容")
        return items.mapNotNull { item ->
            val title = clean(item.childText("title"))
            val linkElement = item.children().firstOrNull { it.tagName() == "link" && it.attr("rel") in listOf("", "alternate") }
            val rawLink = linkElement?.attr("href")?.ifBlank { linkElement.text() }.orEmpty()
            val link = canonicalUrl(runCatching { java.net.URI(source.feedUrl).resolve(rawLink).toString() }.getOrDefault(rawLink))
            if (title.isBlank() || rawLink.isBlank() || link == null) return@mapNotNull null
            val description = clean(item.childText("description").ifBlank { item.childText("summary") })
            val categories = item.getElementsByTag("category").joinToString(" ") { it.text() + " " + it.attr("term") }
            val image = item.getAllElements().firstOrNull {
                (it.tagName() in listOf("media:thumbnail", "media:content") && it.hasAttr("url")) ||
                    (it.tagName() == "enclosure" && it.attr("type").startsWith("image/"))
            }?.attr("url")?.takeIf { it.startsWith("https://") }
            Article(
                id = digest(link), sourceId = source.id, sourceName = source.name, url = link,
                title = title, description = description, imageUrl = image,
                publishedAt = parseDate(item.childText("pubDate").ifBlank {
                    item.childText("published").ifBlank { item.childText("updated").ifBlank { item.childText("dc:date") } }
                }),
                category = categorize("$title $description $categories", source.category),
                region = region("$title $categories", source.region),
                contentType = if (Regex("\\b(opinion|opinions|commentary|editorial|commentisfree)\\b", RegexOption.IGNORE_CASE)
                    .containsMatchIn("$categories $link")) "评论" else if (Regex("/(live|liveblog)(/|$)").containsMatchIn(link)) "直播" else "报道",
            )
        }.distinctBy { it.id }
    }

    private fun Element.childText(name: String) = children().firstOrNull { it.tagName() == name }?.text().orEmpty()
    private fun clean(raw: String) = Jsoup.parse(raw).text().trim()

    fun parseDate(value: String): Long = runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0)

    fun categorize(text: String, fallback: String): String {
        val value = text.lowercase(Locale.ROOT)
        fun has(pattern: String) = Regex("\\b($pattern)\\b").containsMatchIn(value)
        return when {
            has("technology|artificial intelligence|ai|software|semiconductor|cyber|smartphone") -> Category.TECHNOLOGY.name
            has("climate|environment|emissions|biodiversity|wildlife|renewable") -> Category.ENVIRONMENT.name
            has("health|medicine|medical|hospital|vaccine|cancer|disease") -> Category.HEALTH.name
            has("science|scientist|physics|astronomy|spacecraft|nasa|research") -> Category.SCIENCE.name
            has("sport|football|soccer|tennis|olympics|cricket|basketball") -> Category.SPORTS.name
            has("war|military|missile|army|troops|defen[cs]e|ceasefire|nuclear|navy|armed|airstrike|weapons") -> Category.SECURITY.name
            has("economy|economic|inflation|bank|rates|trade|tariff|market|stocks|finance|business|gdp|debt|oil") -> Category.ECONOMY.name
            has("culture|museum|literature|heritage|art|arts|books|theatre|cinema|festival|archaeology|history") -> Category.CULTURE.name
            has("election|parliament|president|minister|government|diplomacy") -> Category.POLITICS.name
            has("education|school|housing|crime|court|migration") -> Category.SOCIETY.name
            has("travel|food|recipe|wellness|lifestyle") -> Category.LIFE.name
            else -> fallback
        }
    }

    fun region(text: String, fallback: String): String {
        val value = text.lowercase(Locale.ROOT)
        fun has(pattern: String) = Regex("\\b($pattern)\\b").containsMatchIn(value)
        return when {
            has("hong kong|macao|macau|taiwan|taipei") -> Region.HONG_KONG_MACAO_TAIWAN.name
            has("china|chinese|beijing|shanghai|shenzhen") -> Region.CHINA_MAINLAND.name
            has("united states|u[.]s[.]|usa|american|washington|trump|biden") -> Region.USA.name
            has("canada|canadian|ottawa") -> Region.CANADA.name
            has("europe|european|britain|british|uk|france|french|germany|german|italy|spain|ukraine|russia") -> Region.EUROPE.name
            has("middle east|israel|gaza|iran|iraq|lebanon|syria|saudi|yemen") -> Region.MIDDLE_EAST.name
            has("africa|african|kenya|nigeria|sudan|ethiopia|congo|egypt") -> Region.AFRICA.name
            has("latin america|brazil|mexico|argentina|colombia|venezuela|chile|peru|cuba") -> Region.LATIN_AMERICA.name
            has("australia|australian|new zealand|pacific islands") -> Region.OCEANIA.name
            has("asia|asian|japan|japanese|korea|india|indian|pakistan|indonesia|singapore|thailand|vietnam|philippines|malaysia|myanmar|bangladesh") -> Region.ASIA.name
            else -> fallback
        }
    }

    data class ExtractedBody(val paragraphs: List<String>, val author: String, val image: String?)
    fun extract(html: String, url: String): ExtractedBody {
        val doc = Jsoup.parse(html, url)
        if (Regex("\"isAccessibleForFree\"\\s*:\\s*(false|\"false\")", RegexOption.IGNORE_CASE).containsMatchIn(html) ||
            doc.select("meta[property=article:content_tier][content=premium]").isNotEmpty()) {
            throw IllegalStateException("这篇文章需要订阅，请打开原网站阅读")
        }
        val author = doc.selectFirst("meta[name=author]")?.attr("content").orEmpty()
        val image = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.startsWith("https://") }
        doc.select("script,style,nav,header,footer,aside,form,button,[hidden],[aria-hidden=true],.ad,.advertisement,.related,.related-content,.recommended,.newsletter,.social-share,figure").remove()
        val explicitBodies = doc.select("[itemprop=articleBody],[data-gu-name=body],#article-body,.article-body,.article__body,.story-body,.content__article-body,.wysiwyg--all-content")
        val candidates = explicitBodies.ifEmpty { doc.select("article").ifEmpty { doc.select("main,[role=main]") } }
        val root = candidates.maxByOrNull { candidate ->
            candidate.select("p").filter { it.text().length >= 45 }.sumOf { it.text().length }
        } ?: throw IllegalStateException("未找到完整正文，请打开原文，或稍后重试")
        // BBC and CNA interleave recommendation/CTA blocks with the article. Keep only
        // their text blocks when present; short original paragraphs remain intact.
        val textBlocks = root.select("[data-block=text] p,[data-component=text-block] p")
        val cnaBlocks = root.select(".text-long p")
        val paragraphElements = textBlocks.ifEmpty { cnaBlocks.ifEmpty { root.select("p") } }
        val paragraphs = paragraphElements.filter { p ->
            val text = p.text().trim()
            text.isNotBlank() && p.closest("a") == null && p.select("a").sumOf { it.text().length } < text.length * .65 &&
                !Regex("^(sign up|subscribe|read more|related:|advertisement|follow us|share this|our standards:)", RegexOption.IGNORE_CASE).containsMatchIn(text)
        }.map { it.text().trim() }.distinct()
        if (paragraphs.joinToString(" ").split(Regex("\\s+")).size < 100) {
            throw IllegalStateException("网页只提供了摘要或正文不完整，请打开原网站阅读")
        }
        return ExtractedBody(paragraphs, author, image)
    }

    fun dailySelection(items: List<Article>, count: Int): List<Article> {
        val groups = Category.entries.associateWith { category ->
            items.filter { it.category == category.name && it.contentType == "报道" }
                .distinctBy { it.title.lowercase().replace(Regex("[^a-z0-9]"), "") }.toMutableList()
        }
        val chosen = mutableListOf<Article>()
        while (chosen.size < count) {
            var added = false
            for (category in Category.entries) {
                val group = groups.getValue(category)
                if (group.isNotEmpty() && chosen.size < count) { chosen += group.removeAt(0); added = true }
            }
            if (!added) break
        }
        return chosen
    }
}
