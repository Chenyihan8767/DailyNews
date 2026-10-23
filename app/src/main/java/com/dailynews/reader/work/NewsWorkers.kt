package com.dailynews.reader.work

import android.content.Context
import androidx.work.*
import com.dailynews.reader.NewsApplication
import com.dailynews.reader.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class NewsScheduler(private val context: Context, private val dao: NewsDao, private val settingsStore: SettingsStore) {
    suspend fun prepare(id: String, priority: Int = 50) {
        val article = dao.article(id) ?: return
        val settings = settingsStore.current()
        if (article.isDemo || (article.analysisJson != null && article.analysisKey == AnalysisValidation.cacheKey(article, settings))) return
        dao.queue(id, priority, System.currentTimeMillis())
        wake()
    }

    suspend fun wake() {
        val request = OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setConstraints(constraints(settingsStore.current()))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        // Appending closes the race where new work arrives just as a drain worker exits.
        WorkManager.getInstance(context).enqueueUniqueWork("article-processing", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun schedule(settings: ReaderSettings) {
        val manager = WorkManager.getInstance(context)
        if (!settings.dailyEnabled) { manager.cancelUniqueWork("daily-brief"); return }
        val now = ZonedDateTime.now()
        var target = now.withHour(settings.dailyHour).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DailyBriefWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, target).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints(settings)).build()
        manager.enqueueUniquePeriodicWork("daily-brief", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    suspend fun prepareDaily(): Int {
        val config = settingsStore.current()
        val since = LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val items = NewsParsing.dailySelection(dao.dailyCandidates(since), config.dailyCount)
        for (article in items) prepare(article.id, 10)
        return items.size
    }

    companion object {
        private fun constraints(settings: ReaderSettings) = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
    }
}

class ProcessingWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = (applicationContext as NewsApplication).container
        app.repository.initialize()
        app.dao.recoverInterrupted()
        while (true) {
            val item = app.dao.nextQueued() ?: break
            try {
                if (app.settings.apiKey().isBlank()) {
                    app.dao.processingState(item.id, "waiting_key", "填写 DeepSeek API Key 后继续")
                    continue
                }
                app.dao.processingState(item.id, "running")
                val article = app.repository.ensureBody(item.id)
                val config = app.settings.current()
                val key = AnalysisValidation.cacheKey(article, config)
                if (article.analysisKey == key && article.analysisJson != null) {
                    app.dao.processingState(item.id, "ready")
                    continue
                }
                val analysis = app.ai.analyze(article)
                // Never label a response as belonging to settings changed during the request.
                if (AnalysisValidation.cacheKey(article, app.settings.current()) != key) {
                    app.dao.processingState(item.id, "queued")
                    continue
                }
                app.dao.saveAnalysis(item.id, newsJson.encodeToString(analysis), key)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { app.dao.processingState(item.id, "queued") }
                throw e
            } catch (e: Exception) {
                app.dao.processingState(item.id, "failed", e.localizedMessage ?: "处理失败，已保留原文，可手动重试")
            }
        }
        return Result.success()
    }
}

class DailyBriefWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = (applicationContext as NewsApplication).container
        if (!app.settings.current().dailyEnabled) return Result.success()
        app.repository.initialize()
        val result = app.repository.refresh()
        if (result.successes == 0 && result.failures > 0) return Result.retry()
        app.scheduler.prepareDaily()
        return Result.success()
    }
}
