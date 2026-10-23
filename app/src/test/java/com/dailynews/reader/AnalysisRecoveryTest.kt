package com.dailynews.reader

import com.dailynews.reader.data.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test

class AnalysisRecoveryTest {
    private fun result(paragraphs: List<Paragraph>) = ArticleAnalysis("Summary", "摘要",
        paragraphs.map { ParagraphTranslation(it.id, "译文${it.text.length}") })

    @Test fun `configured flash output limit is sent without a hardcoded cap and survives persistence`() {
        val config = ReaderSettings(model = "deepseek-flash", maxOutputTokens = 393216,
            chunkCharacters = 48000, aiTimeoutSeconds = 900, splitOnOutputLimit = false)
        val restored = newsJson.decodeFromString<ReaderSettings>(newsJson.encodeToString(config))
        assertEquals(config, restored)
        assertEquals(393216, AiRequest.payload("test", restored, "JSON")["max_tokens"]!!.jsonPrimitive.int)
        val paragraphs = (1..12).map { Paragraph("p$it", "English ".repeat(200)) }
        assertEquals(1, AnalysisValidation.chunks(paragraphs, restored.chunkCharacters).size)
    }

    @Test fun `existing saved settings without advanced fields remain readable`() {
        val restored = newsJson.decodeFromString<ReaderSettings>("""{"model":"custom-flash","endpoint":"https://example.org/v1"}""")
        assertEquals("custom-flash", restored.model)
        assertEquals(393216, restored.maxOutputTokens)
        assertEquals(2400, restored.chunkCharacters)
        assertEquals(210, restored.aiTimeoutSeconds)
    }

    @Test fun `length limit subdivides without losing paragraph IDs or translations`() = runTest {
        val input = (1..4).map { Paragraph("p$it", "Original $it") }
        var calls = 0
        val analysis = AnalysisRecovery.analyze(input) {
            calls++
            if (it.size > 1) throw AiOutputLimitException()
            result(it)
        }
        assertEquals(input.map { it.id }, analysis.translations.map { it.paragraphId })
        assertEquals(7, calls)
    }

    @Test fun `one oversized paragraph is translated in parts and reassembled under original ID`() = runTest {
        val input = listOf(Paragraph("original", "English text. ".repeat(180)))
        val processed = mutableListOf<String>()
        val analysis = AnalysisRecovery.analyze(input) {
            if (it.single().text.length > 1300) throw AiOutputLimitException()
            processed += it.single().text
            result(it)
        }
        assertEquals(input.single().text, processed.joinToString(""))
        assertEquals("original", analysis.translations.single().paragraphId)
        assertEquals(2, analysis.translations.single().translation.lines().size)
    }

    @Test fun `subdivision has a strict depth limit`() = runTest {
        var calls = 0
        try {
            AnalysisRecovery.analyze((1..32).map { Paragraph("p$it", "Text") }) {
                calls++
                throw AiOutputLimitException()
            }
            fail("Expected output limit")
        } catch (_: AiOutputLimitException) { assertEquals(4, calls) }
    }

    @Test fun `authentication and other failures never trigger paid retries`() = runTest {
        var calls = 0
        try {
            AnalysisRecovery.analyze(listOf(Paragraph("p1", "Text"), Paragraph("p2", "Text"))) {
                calls++
                throw AiException("API Key 无效")
            }
            fail("Expected API failure")
        } catch (_: AiException) { assertEquals(1, calls) }
    }

    @Test fun `response distinguishes length limits from content filtering`() {
        fun response(reason: String) = """{"choices":[{"finish_reason":"$reason","message":{"content":"{}"}}]}"""
        assertEquals("{}", AiResponse.content(response("stop")))
        try {
            AiResponse.content(response("length"))
            fail("Expected output limit")
        } catch (_: AiOutputLimitException) { }
        try {
            AiResponse.content(response("content_filter"))
            fail("Expected filtering error")
        } catch (e: AiException) { assertFalse(e.message.orEmpty().contains("截断")) }
    }

    @Test fun `many short paragraphs have bounded JSON overhead per request`() {
        val input = (1..30).map { Paragraph("p$it", "Short") }
        val chunks = AnalysisValidation.chunks(input)
        assertEquals(input, chunks.flatten())
        assertTrue(chunks.all { it.size <= 6 })
    }
}
