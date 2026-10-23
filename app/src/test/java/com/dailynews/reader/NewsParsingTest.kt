package com.dailynews.reader

import com.dailynews.reader.data.*
import org.junit.Assert.*
import org.junit.Test

class NewsParsingTest {
    private val source = NewsSource("test", "Test", "https://example.org/feed", Category.POLITICS.name)

    @Test fun `RSS preserves attribution metadata and strips tracking without losing article identity`() {
        val feed = """<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><item>
            <title>Officials weigh a new ceasefire proposal</title>
            <link>https://example.org/story?id=42&amp;utm_source=rss#comments</link>
            <description><![CDATA[<p>Officials <strong>said</strong> talks could resume.</p>]]></description>
            <pubDate>Thu, 22 Oct 2026 07:10:55 +0000</pubDate>
            <media:thumbnail url="https://example.org/photo.jpg" />
            </item></channel></rss>"""
        val item = NewsParsing.feed(feed, source).single()
        assertEquals("https://example.org/story?id=42", item.url)
        assertEquals("Officials said talks could resume.", item.description)
        assertEquals(Category.SECURITY.name, item.category)
        assertEquals("https://example.org/photo.jpg", item.imageUrl)
        assertTrue(item.publishedAt > 0)
    }

    @Test fun `Atom relative alternate links and ISO dates are accepted`() {
        val feed = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Culture and heritage</title>
            <link rel="self" href="https://example.org/api/1"/><link rel="alternate" href="/story/1"/>
            <summary>Preserving a museum collection.</summary><updated>2026-10-22T10:00:00Z</updated>
            </entry></feed>"""
        val item = NewsParsing.feed(feed, source).single()
        assertEquals("https://example.org/story/1", item.url)
        assertEquals(Category.CULTURE.name, item.category)
        assertTrue(item.publishedAt > 0)
    }

    @Test(expected = IllegalArgumentException::class) fun `HTML error pages cannot masquerade as an empty feed`() {
        NewsParsing.feed("<html><body>Access denied</body></html>", source)
    }

    @Test fun `body extraction excludes navigation ads and related stories`() {
        val paragraphs = DemoContent.paragraphs
        val html = """<html><head><meta name="author" content="Test Author"/></head><body>
            <nav><p>${"Navigation words ".repeat(100)}</p></nav>
            <article>${paragraphs.joinToString("") { "<p>$it</p>" }}
            <aside><p>${"Unrelated recommendation ".repeat(100)}</p></aside>
            <p>Subscribe to our newsletter for more stories every day.</p></article></body></html>"""
        val body = NewsParsing.extract(html, "https://example.org/story")
        assertEquals(paragraphs, body.paragraphs)
        assertEquals("Test Author", body.author)
    }

    @Test(expected = IllegalStateException::class) fun `an abstract is not treated as a full article`() {
        NewsParsing.extract("<article><p>This is just a short abstract for a subscriber-only story.</p></article>", "https://example.org/story")
    }

    @Test(expected = IllegalStateException::class) fun `subscription-only articles are left to the original site`() {
        NewsParsing.extract("""<script type="application/ld+json">{"isAccessibleForFree":false}</script><article>
            ${DemoContent.paragraphs.joinToString("") { "<p>$it</p>" }}</article>""", "https://example.org/story")
    }

    @Test fun `daily selection covers categories and does not include opinion or live updates`() {
        val items = Category.entries.flatMap { category -> (1..4).map {
            Article("${category.name}$it", "test", "Test", "https://example.org/${category.name}/$it", "${category.name} story $it", category = category.name)
        } } + Article("opinion", "test", "Test", "https://example.org/opinion", "An opinion", contentType = "评论")
        val chosen = NewsParsing.dailySelection(items, Category.entries.size)
        assertEquals(Category.entries.size, chosen.size)
        assertEquals(Category.entries.map { it.name }.toSet(), chosen.map { it.category }.toSet())
        assertFalse(chosen.any { it.id == "opinion" })
    }
}
