package com.dailynews.reader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun publicHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS)
    .callTimeout(50, TimeUnit.SECONDS).build()

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { response.close() }
        }
    })
}

suspend fun OkHttpClient.fetchPublic(url: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder().url(url).header("User-Agent", "DailyNews/0.1 (personal RSS reader)")
        .header("Accept", "application/rss+xml, application/atom+xml, text/html, application/xml;q=0.9, */*;q=0.5").build()
    newCall(request).awaitResponse().use { response ->
        if (!response.isSuccessful) throw IOException(when (response.code) {
            401, 402, 403 -> "网站限制了正文访问，请打开原网站阅读"
            429 -> "来源请求过于频繁，请稍后重试"
            else -> "来源请求失败（HTTP ${response.code}）"
        })
        val body = response.body ?: throw IOException("来源没有返回内容")
        body.byteStream().use { stream ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                if (output.size() + read > 8 * 1024 * 1024) throw IOException("页面过大，暂不处理")
                output.write(buffer, 0, read)
            }
            String(output.toByteArray(), body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
        }
    }
}
