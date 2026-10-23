@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dailynews.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailynews.reader.data.*
import kotlin.math.roundToInt

@Composable fun SettingsScreen(model: NewsViewModel) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val hasKey by model.hasKey.collectAsStateWithLifecycle()
    val status by model.connectionStatus.collectAsStateWithLifecycle()
    var endpoint by rememberSaveable(settings.endpoint) { mutableStateOf(settings.endpoint) }
    var apiModel by rememberSaveable(settings.model) { mutableStateOf(settings.model) }
    var outputTokens by rememberSaveable(settings.maxOutputTokens) { mutableStateOf(settings.maxOutputTokens.toString()) }
    var chunkCharacters by rememberSaveable(settings.chunkCharacters) { mutableStateOf(settings.chunkCharacters.toString()) }
    var timeoutSeconds by rememberSaveable(settings.aiTimeoutSeconds) { mutableStateOf(settings.aiTimeoutSeconds.toString()) }
    var splitOnLimit by rememberSaveable(settings.splitOnOutputLimit) { mutableStateOf(settings.splitOnOutputLimit) }
    val validOutput = outputTokens.toIntOrNull()?.let { it in 1..1_048_576 } == true
    val validChunk = chunkCharacters.toIntOrNull()?.let { it in 500..1_000_000 } == true
    val validTimeout = timeoutSeconds.toIntOrNull()?.let { it in 30..3600 } == true
    // Credentials deliberately never enter saved-instance state or a preview.
    var apiKey by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { PageHeader("Make it yours.", "设置", "舒适阅读，按自己的节奏理解世界。") }
        item {
            SettingsSection("外观", "Ink & Moss") {
                ChoiceRow("显示主题", settings.appearance.label, Appearance.entries.map { it to it.label }) { choice ->
                    model.updateSettings { it.copy(appearance = choice) }
                }
                SettingSwitch("系统动态配色", "关闭时使用暖黑、米白与苔绿主题", settings.dynamicColor) { value ->
                    model.updateSettings { it.copy(dynamicColor = value) }
                }
            }
        }
        item {
            SettingsSection("阅读", "纯英文 · 按需辅助") {
                ChoiceRow("点词释义方式", settings.glossMode.label, GlossMode.entries.map { it to it.label }) { value ->
                    model.updateSettings { it.copy(glossMode = value) }
                }
                Text(when (settings.glossMode) {
                    GlossMode.COMBINED -> "先显示简短浮卡，点「详情」展开底部面板。"
                    GlossMode.POPUP -> "词语附近显示语境释义和收藏按钮。"
                    GlossMode.SHEET -> "点词后直接展开详细解释与原文语境。"
                }, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Text("正文字号 · ${settings.fontSize} sp", style = MaterialTheme.typography.titleSmall)
                    Slider(settings.fontSize.toFloat(), { value -> model.updateSettings { it.copy(fontSize = value.roundToInt()) } },
                        valueRange = 16f..26f, steps = 9)
                    Text("行距 · ${String.format(java.util.Locale.ROOT, "%.2f", settings.lineHeight)}", style = MaterialTheme.typography.titleSmall)
                    Slider(settings.lineHeight, { value -> model.updateSettings { it.copy(lineHeight = value) } }, valueRange = 1.35f..2f)
                }
                SettingSwitch("显示重点词标记", "为预处理识别的词语加下划线", settings.showHighlights) { value ->
                    model.updateSettings { it.copy(showHighlights = value) }
                }
            }
        }
        item {
            SettingsSection("AI预处理", "三个入口，共用本地缓存") {
                SettingSwitch("打开文章时处理", "先显示英文，翻译和分析在后台生成", settings.processOnOpen) { value ->
                    model.updateSettings { it.copy(processOnOpen = value) }
                }
                SettingSwitch("收藏后自动处理", "提前准备，完成后可离线阅读", settings.processOnBookmark) { value ->
                    model.updateSettings { it.copy(processOnBookmark = value) }
                }
                SettingSwitch("每日自动精选", "按分类轮选当日最新报道", settings.dailyEnabled) { value ->
                    model.updateSettings { it.copy(dailyEnabled = value) }
                }
                ChoiceRow("每日处理数量", "${settings.dailyCount} 篇", listOf(3, 5, 10, 15, 20).map { it to "$it 篇" }) { value ->
                    model.updateSettings { it.copy(dailyCount = value) }
                }
                ChoiceRow("每日开始时间", "%02d:00".format(settings.dailyHour), (0..23).map { it to "%02d:00".format(it) }) { value ->
                    model.updateSettings { it.copy(dailyHour = value) }
                }
                SettingSwitch("后台处理仅使用Wi-Fi", "使用系统识别的不限流量网络", settings.wifiOnly) { value ->
                    model.updateSettings { it.copy(wifiOnly = value) }
                }
                Text("自动任务由 Android 调度，可能晚于设定时间。处理量会产生相应 API 用量；连接测试和即时查词也会调用接口。",
                    Modifier.padding(18.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SettingsSection("DeepSeek", if (hasKey) "密钥已保存在本机" else "等待配置 API Key") {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text("API地址") },
                        placeholder = { Text("https://api.deepseek.com") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(16.dp))
                    OutlinedTextField(apiModel, { apiModel = it }, Modifier.fillMaxWidth(), label = { Text("模型名称") },
                        singleLine = true, shape = RoundedCornerShape(16.dp))
                    OutlinedTextField(outputTokens, { outputTokens = it }, Modifier.fillMaxWidth(), label = { Text("单次最大输出 tokens") },
                        supportingText = { Text("1–1048576，按接口上限填写；384K 可填 393216（以服务商定义为准）") },
                        isError = !validOutput, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp))
                    OutlinedTextField(chunkCharacters, { chunkCharacters = it }, Modifier.fillMaxWidth(), label = { Text("每块正文字符数") },
                        supportingText = { Text("500–1000000，按段落分块；字符数不等于 tokens，单个长段可能超出此值") },
                        isError = !validChunk, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp))
                    OutlinedTextField(timeoutSeconds, { timeoutSeconds = it }, Modifier.fillMaxWidth(), label = { Text("AI请求超时（秒）") },
                        supportingText = { Text("30–3600；较大分块或较长输出可增加等待时间") },
                        isError = !validTimeout, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp))
                    SettingSwitch("输出超限时自动拆分", "仅在接口返回长度超限时拆小重试，最多3层，会产生额外调用", splitOnLimit) { splitOnLimit = it }
                    OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text(if (hasKey) "替换 API Key（留空保留）" else "API Key") },
                        singleLine = true, visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, "切换密钥显示") } },
                        shape = RoundedCornerShape(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = {
                            model.saveApi(endpoint, apiModel, apiKey, outputTokens.toInt(), chunkCharacters.toInt(), timeoutSeconds.toInt(), splitOnLimit)
                            apiKey = ""
                        }, enabled = validOutput && validChunk && validTimeout) { Text("保存配置") }
                        OutlinedButton(onClick = model::testConnection, enabled = hasKey && status != "正在测试…") { Text("测试连接") }
                    }
                    if (status != null) Text(status!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    if (hasKey) TextButton(onClick = model::clearKey) { Text("清除本机密钥") }
                    Text("API Key 使用 Android Keystore 加密。新闻正文和选中的词句会发送到你配置的接口；笔记保留在手机本地。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text("DAILYNEWS  /  0.1.0\n个人英文新闻阅读器\nKotlin · Jetpack Compose · Material 3",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SettingsSection(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) { Column(content = content) }
    }
}

@Composable private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked, onCheckedChange = onChange)
    }
}

@Composable fun <T> ChoiceRow(title: String, value: String, choices: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Default.ExpandMore, null, Modifier.padding(start = 6.dp).size(18.dp))
        }
        if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text(title) },
            text = { LazyColumn { items(choices.size) { index ->
                val choice = choices[index]
                Row(Modifier.fillMaxWidth().clickable { onSelect(choice.first); expanded = false }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = choice.second == value, onClick = { onSelect(choice.first); expanded = false })
                    Text(choice.second)
                }
            } } },
            confirmButton = { TextButton(onClick = { expanded = false }) { Text("关闭") } })
    }
}
