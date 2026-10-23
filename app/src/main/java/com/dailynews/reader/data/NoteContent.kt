package com.dailynews.reader.data

private fun String.normalizedNoteText() = trim().replace(Regex("\\s+"), " ")
fun sameNoteText(first: String, second: String) = first.normalizedNoteText() == second.normalizedNoteText()

/** Older sentence notes may actually contain a whole paragraph. Keep their original scope. */
fun StudyNote.isSentence() = kind == "sentence" && (!sameNoteText(term, context) ||
    analysis()?.sentences.orEmpty().any { it.paragraphId == paragraphId && sameNoteText(it.quote, term) })

data class NoteContent(
    val original: String,
    val translation: String,
    val paragraphOriginal: String,
    val paragraphTranslation: String,
    val sentences: List<SentenceInsight>,
)

fun StudyNote.readingContent(analysis: ArticleAnalysis?): NoteContent {
    val paragraphTranslation = analysis?.translations?.firstOrNull { it.paragraphId == paragraphId }?.translation.orEmpty()
    val sentences = analysis?.sentences.orEmpty().filter { it.paragraphId == paragraphId }
    if (isSentence()) {
        val matching = sentences.filter { sameNoteText(it.quote, term) }
        return NoteContent(term, matching.firstOrNull()?.translation?.takeIf { it.isNotBlank() } ?: meaning,
            context, paragraphTranslation, matching)
    }
    return NoteContent(term, if (isWord()) meaning else paragraphTranslation.ifBlank { meaning },
        context, paragraphTranslation, sentences.filter { sentence ->
            (if (isWord()) context else term).contains(sentence.quote) && sentence.quote.isNotBlank()
        })
}

fun sentenceNote(article: Article, paragraph: Paragraph, sentence: SentenceInsight): StudyNote {
    require(sentence.paragraphId == paragraph.id && sentence.quote.isNotBlank() && paragraph.text.contains(sentence.quote))
    val analysis = article.analysis()?.forParagraph(paragraph.id)?.copy(sentences = listOf(sentence))
        ?: ArticleAnalysis(sentences = listOf(sentence))
    return StudyNote(id = digest("${article.id}|${paragraph.id}|sentence|${sentence.quote}"),
        articleId = article.id, articleTitle = article.title, paragraphId = paragraph.id,
        term = sentence.quote, meaning = sentence.translation, context = paragraph.text, kind = "sentence",
        analysisJson = newsJson.encodeToString(ArticleAnalysis.serializer(), analysis))
}
