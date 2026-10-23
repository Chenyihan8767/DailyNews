package com.dailynews.reader

import com.dailynews.reader.data.*
import com.dailynews.reader.ui.ReaderText
import org.junit.Assert.*
import org.junit.Test

class AnalysisValidationTest {
    private val paragraphs = listOf(Paragraph("p1", "Officials did not rule out further rate hikes."), Paragraph("p2", "They will weigh the evidence."))
    private val valid = ArticleAnalysis("A cautious outlook.", "谨慎的展望。", listOf(
        ParagraphTranslation("p2", "他们会权衡证据。"), ParagraphTranslation("p1", "官员们没有排除进一步加息的可能性。")))

    @Test fun `translations are realigned using IDs rather than response order`() {
        val result = AnalysisValidation.validate(valid, paragraphs)
        assertEquals(listOf("p1", "p2"), result.translations.map { it.paragraphId })
    }
    @Test(expected = IllegalArgumentException::class) fun `missing translation is rejected`() {
        AnalysisValidation.validate(valid.copy(translations = valid.translations.take(1)), paragraphs)
    }
    @Test(expected = IllegalArgumentException::class) fun `duplicate IDs cannot hide a missing paragraph`() {
        AnalysisValidation.validate(valid.copy(translations = listOf(ParagraphTranslation("p1", "a"), ParagraphTranslation("p1", "b"))), paragraphs)
    }
    @Test fun `invented quotes and vocabulary cannot be attached to original text`() {
        val result = AnalysisValidation.validate(valid.copy(vocabulary = listOf(
            WordInsight("p1", "rate hikes", "加息"), WordInsight("p1", "rate cuts", "降息")), sentences = listOf(
            SentenceInsight("p1", "Officials cut rates.", "官员降息了。", "主谓宾"))), paragraphs)
        assertEquals(listOf("rate hikes"), result.vocabulary.map { it.term })
        assertTrue(result.sentences.isEmpty())
    }
    @Test fun `article version model and endpoint all affect processing cache identity`() {
        val base = DemoContent.article.copy(contentHash = "one")
        val settings = ReaderSettings()
        val keys = listOf(AnalysisValidation.cacheKey(base, settings),
            AnalysisValidation.cacheKey(base.copy(contentHash = "two"), settings),
            AnalysisValidation.cacheKey(base, settings.copy(model = "another-model")),
            AnalysisValidation.cacheKey(base, settings.copy(endpoint = "https://other.example.org")))
        assertEquals(4, keys.toSet().size)
    }
    @Test fun `chunking neither drops nor duplicates paragraphs`() {
        val input = (1..20).map { Paragraph("p$it", "A".repeat(900)) }
        val chunks = AnalysisValidation.chunks(input)
        assertEquals(input, chunks.flatten())
        assertTrue(chunks.all { group -> group.sumOf { it.text.length } <= 2400 })
    }
    @Test fun `tap resolves the longest known phrase and ignores punctuation`() {
        val text = paragraphs[0].text
        val words = listOf(WordInsight("p1", "rate", "利率"), WordInsight("p1", "rate hikes", "加息"))
        assertEquals("rate hikes", ReaderText.termAt(text, text.indexOf("hikes") + 1, words))
        assertEquals("Officials", ReaderText.termAt(text, 2, words))
        assertNull(ReaderText.termAt(text, text.lastIndex, words))
        assertNull(ReaderText.termAt(text, -1, words))
    }
}
