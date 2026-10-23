package com.dailynews.reader

import com.dailynews.reader.data.*
import org.junit.Assert.*
import org.junit.Test

class NoteContentTest {
    private val paragraph = Paragraph("p1", "First sentence. Second sentence.")
    private val sentence = SentenceInsight("p1", "First sentence.", "第一句。", "主谓结构")
    private val analysis = ArticleAnalysis(translations = listOf(
        ParagraphTranslation("other", "无关译文"), ParagraphTranslation("p1", "第一句。第二句。")),
        sentences = listOf(sentence, SentenceInsight("other", "Other.", "无关", "无关")))
    private fun note(kind: String, term: String, meaning: String) = StudyNote("n", "a", "Title", "p1", term,
        meaning, paragraph.text, kind = kind)

    @Test fun legacySentenceUsesOnlyItsMatchingTranslationAndRetainsWholeContextSeparately() {
        val note = note("sentence", sentence.quote, "旧译文")
        val content = note.readingContent(analysis)
        assertTrue(note.isSentence())
        assertEquals(sentence.quote, content.original)
        assertEquals(sentence.translation, content.translation)
        assertEquals(paragraph.text, content.paragraphOriginal)
        assertEquals("第一句。第二句。", content.paragraphTranslation)
        assertEquals(listOf(sentence), content.sentences)
    }
    @Test fun paragraphOriginalAndTranslationCoverSameScopeAndUseMatchingParagraphId() {
        val note = note("paragraph", paragraph.text, "旧译文")
        val content = note.readingContent(analysis)
        assertFalse(note.isSentence())
        assertEquals(paragraph.text, content.original)
        assertEquals("第一句。第二句。", content.translation)
        assertEquals(listOf(sentence), content.sentences)
    }
    @Test fun legacyWholeParagraphIsStillAPargaphEvenWhenStoredAsSentence() {
        val note = note("sentence", paragraph.text, "已存整段译文")
        assertFalse(note.isSentence())
        assertEquals("第一句。第二句。", note.readingContent(analysis).translation)
        assertEquals("已存整段译文", note.readingContent(null).translation)
    }
    @Test fun unknownSentenceNeverBorrowsAnotherSentenceOrParagraphTranslation() {
        val note = note("sentence", "Second sentence.", "已存第二句译文")
        assertEquals("已存第二句译文", note.readingContent(analysis).translation)
        assertTrue(note.readingContent(analysis).sentences.isEmpty())
    }
    @Test fun savingOneSentenceKeepsItsQuoteTranslationAndStructureTogether() {
        val article = DemoContent.article.copy(analysisJson = newsJson.encodeToString(ArticleAnalysis.serializer(), analysis))
        val note = sentenceNote(article, paragraph, sentence)
        assertEquals(sentence.quote, note.term)
        assertEquals(sentence.translation, note.meaning)
        assertEquals(paragraph.text, note.context)
        assertEquals(listOf(sentence), note.analysis()!!.sentences)
        assertNotEquals(digest("${article.id}|${paragraph.id}|sentence"), note.id)
    }
    @Test fun explicitlySavedSentenceIsStillASentenceWhenItOccupiesAnEntireParagraph() {
        val note = sentenceNote(DemoContent.article, paragraph.copy(text = sentence.quote), sentence)
        assertTrue(note.isSentence())
        assertEquals(sentence.translation, note.readingContent(note.analysis()).translation)
    }
    @Test(expected = IllegalArgumentException::class) fun sentenceCannotBeSavedUnderAnUnrelatedParagraph() {
        sentenceNote(DemoContent.article, paragraph, sentence.copy(paragraphId = "p2"))
    }
}
