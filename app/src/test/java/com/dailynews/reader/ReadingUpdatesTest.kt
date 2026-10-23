package com.dailynews.reader

import com.dailynews.reader.data.*
import org.junit.Assert.*
import org.junit.Test

class ReadingUpdatesTest {
    @Test fun defaultsUseFlashAnd384K() {
        assertEquals("deepseek-flash", ReaderSettings().model)
        assertEquals(384 * 1024, ReaderSettings().maxOutputTokens)
    }
    @Test fun topicAndRegionAreIndependentAndRegionalFallbackIsPreserved() {
        val source = NewsSource("test", "Test", "https://example.org/rss", Category.GENERAL.name, Region.AFRICA.name)
        val article = NewsParsing.feed("""<rss><channel><item><title>Kenya expands vaccine programme</title><link>https://example.org/a</link></item></channel></rss>""", source).single()
        assertEquals(Category.HEALTH.name, article.category)
        assertEquals(Region.AFRICA.name, article.region)
        assertEquals(Region.EUROPE.name, NewsParsing.region("A local story", Region.EUROPE.name))
        assertEquals(Region.HONG_KONG_MACAO_TAIWAN.name, NewsParsing.region("Taiwan election", Region.CHINA_MAINLAND.name))
    }
    @Test fun aggregateRegionFiltersContainTheirSubregions() {
        assertTrue(Region.ASIA_PACIFIC.includes(Region.CHINA_MAINLAND.name))
        assertTrue(Region.ASIA_PACIFIC.includes(Region.OCEANIA.name))
        assertTrue(Region.ASIA.includes(Region.HONG_KONG_MACAO_TAIWAN.name))
        assertFalse(Region.ASIA.includes(Region.EUROPE.name))
    }
    @Test fun paragraphNotesDoNotRepeatOriginalContextAndLegacyNotesStillWork() {
        val note = StudyNote("test", "a", "Title", "p1", "Original sentence", "译文", "Original paragraph", kind = "sentence")
        assertFalse(note.showSeparateContext())
        assertFalse(note.isWord())
        assertTrue(note.copy(kind = "word").showSeparateContext())
        assertFalse(note.copy(kind = "word", context = note.term).showSeparateContext())
    }
    @Test fun savedAnalysisRetainsOnlyRelevantParagraphAndCompleteWordDetails() {
        val word = DemoContent.analysis.vocabulary.first()
        val note = StudyNote("test", "a", "Title", word.paragraphId, word.term, word.meaning, "Context",
            insightJson = newsJson.encodeToString(WordInsight.serializer(), word),
            analysisJson = newsJson.encodeToString(ArticleAnalysis.serializer(), DemoContent.analysis.forParagraph(word.paragraphId)))
        assertEquals(word, note.insight())
        assertTrue(note.analysis()!!.vocabulary.all { it.paragraphId == word.paragraphId })
        assertEquals(1, note.analysis()!!.translations.size)
    }
    @Test fun sourceCatalogHasUniqueSecureAddressesAndCoversMoreThanFourTopics() {
        val sources = SourceCatalog.sources
        assertEquals(27, sources.size)
        assertEquals(sources.size, sources.map { it.id }.toSet().size)
        assertEquals(sources.size, sources.map { it.feedUrl }.toSet().size)
        assertTrue(sources.all { it.feedUrl.startsWith("https://") })
        assertTrue(sources.map { it.category }.toSet().size > 4)
        assertTrue(sources.any { it.region == Region.CHINA_MAINLAND.name })
        assertTrue(sources.any { it.region == Region.HONG_KONG_MACAO_TAIWAN.name })
    }
}
