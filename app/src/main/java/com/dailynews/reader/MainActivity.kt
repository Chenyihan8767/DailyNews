package com.dailynews.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.os.Build
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.dailynews.reader.ui.NewsApp
import com.dailynews.reader.ui.NewsViewModel
import com.dailynews.reader.ui.theme.DailyNewsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        setContent {
            val model: NewsViewModel = viewModel(factory = NewsViewModel.Factory((application as NewsApplication).container))
            val settings by model.settings.collectAsStateWithLifecycle()
            DailyNewsTheme(settings) { NewsApp(model) }
        }
    }
}
