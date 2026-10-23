package com.dailynews.reader

import android.app.Application
import androidx.room.Room
import com.dailynews.reader.data.*
import com.dailynews.reader.work.NewsScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

class NewsApplication : Application() {
    val container by lazy { AppContainer(this) }
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.repository.initialize()
            container.settings.settings.map { listOf(it.dailyEnabled, it.dailyCount, it.dailyHour, it.wifiOnly) }
                .distinctUntilChanged().collect { container.scheduler.schedule(container.settings.current()) }
        }
    }
}

class AppContainer(application: Application) {
    val database = Room.databaseBuilder(application, NewsDatabase::class.java, "dailynews.db").addMigrations(NEWS_MIGRATION_1_2).build()
    val dao = database.newsDao()
    val settings = SettingsStore(application)
    val repository = NewsRepository(dao, catalogStore = settings)
    val ai = DeepSeekClient(settings, File(application.filesDir, "analysis-cache"))
    val scheduler = NewsScheduler(application, dao, settings)
}
