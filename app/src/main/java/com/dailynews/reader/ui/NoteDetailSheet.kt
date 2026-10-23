@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.dailynews.reader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailynews.reader.data.*

@Composable fun NoteDetailSheet(note: StudyNote, model: NewsViewModel, onDismiss: () -> Unit) {
    var insight by remember(note.id) { mutableStateOf(note.insight()) }
    var analysis by remember(note.id) { mutableStateOf(note.analysis()) }
    // Old notes did not persist analysis; recover available local article/gloss data without an API call.
    LaunchedEffect(note.id) {
        val article = model.app.dao.article(note.articleId)
        if (analysis == null) analysis = article?.analysis()?.forParagraph(note.paragraphId)
        if (insight == null && note.isWord()) {
            insight = analysis?.vocabulary?.firstOrNull { it.term.equals(note.term, true) }
            if (insight == null && article != null) {
                val settings = model.app.settings.current()
                val key = digest("${article.contentHash}|${note.paragraphId}|${note.term}|${settings.endpoint}|${settings.model}")
                insight = model.app.dao.cachedGloss(key)?.let {
                    runCatching { newsJson.decodeFromString<WordInsight>(it.insightJson) }.getOrNull()
                }
            }
        }
    }
    val content = note.readingContent(analysis)
    var showParagraph by remember(note.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp).padding(bottom = 32.dp).testTag("note-detail"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (note.isWord()) "词语详情" else if (note.isSentence()) "句子详情" else "段落详情", style = MaterialTheme.typography.headlineSmall)
            SelectionContainer { Text(content.original, style = if (note.isWord()) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge) }
            insight?.let { word ->
                if (word.pronunciation.isNotBlank() || word.partOfSpeech.isNotBlank()) Text("${word.pronunciation}  ${word.partOfSpeech}")
                if (word.lemma.isNotBlank() && word.lemma != word.term) Text("词形 · ${word.lemma}")
            }
            val meaning = insight?.meaning?.ifBlank { null } ?: content.translation
            if (meaning.isNotBlank()) {
                if (!note.isWord()) Text(if (note.isSentence()) "句子译文" else "段落译文", color = MaterialTheme.colorScheme.primary)
                SelectionContainer { Text(meaning, style = MaterialTheme.typography.bodyLarge) }
            }
            insight?.explanation?.takeIf { it.isNotBlank() }?.let {
                Text("语境分析", color = MaterialTheme.colorScheme.primary)
                Text(it)
            }
            if (note.isWord() && note.context.isNotBlank() && note.context != note.term) {
                Text("原文语境", color = MaterialTheme.colorScheme.primary)
                SelectionContainer { Text(note.context, style = MaterialTheme.typography.bodyLarge) }
            }
            if (note.isWord() && content.paragraphTranslation.isNotBlank()) {
                Text("段落译文", color = MaterialTheme.colorScheme.primary)
                SelectionContainer { Text(content.paragraphTranslation) }
            }
            content.sentences.forEach { sentence ->
                Text("句子分析", color = MaterialTheme.colorScheme.primary)
                if (!sameNoteText(sentence.quote, content.original) && !sameNoteText(sentence.quote, note.context)) {
                    SelectionContainer { Text(sentence.quote) }
                }
                // The main translation already covers this sentence. Show structure only here.
                Text(sentence.structure)
            }
            if (note.isSentence() && content.paragraphOriginal.isNotBlank() && !sameNoteText(content.original, content.paragraphOriginal)) {
                TextButton(onClick = { showParagraph = !showParagraph }) { Text(if (showParagraph) "收起所在段落" else "查看所在段落") }
                if (showParagraph) {
                    Text("所在段落原文", color = MaterialTheme.colorScheme.primary)
                    SelectionContainer { Text(content.paragraphOriginal) }
                    if (content.paragraphTranslation.isNotBlank()) {
                        Text("所在段落译文", color = MaterialTheme.colorScheme.primary)
                        SelectionContainer { Text(content.paragraphTranslation) }
                    }
                }
            }
            if (!note.isWord() && !note.isSentence()) analysis?.vocabulary.orEmpty().filter { it.paragraphId == note.paragraphId }.forEach { word ->
                Text(word.term, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(word.meaning)
                if (word.explanation.isNotBlank()) Text(word.explanation)
            }
            if (insight == null && analysis == null) Text("尚无已保存的AI分析，可返回文章准备精读。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            TextButton(onClick = { onDismiss(); model.openArticle(note.articleId) }) {
                Text(note.articleTitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
