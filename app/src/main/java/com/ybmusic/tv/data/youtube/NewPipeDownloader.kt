package com.ybmusic.tv.data.youtube

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request  as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: NPRequest): NPResponse {
        val url = request.url()
        val rb  = Request.Builder().url(url)

        request.headers().forEach { (k, vs) -> vs.forEach { rb.addHeader(k, it) } }

        if (request.headers()["User-Agent"].isNullOrEmpty()) {
            rb.addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 11; Xiaomi TV) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            )
        }

        val body = request.dataToSend()
            ?.toRequestBody("application/json; charset=utf-8".toMediaType())

        when (request.httpMethod()) {
            "GET"    -> rb.get()
            "POST"   -> rb.post(body ?: "".toRequestBody())
            "PUT"    -> rb.put(body  ?: "".toRequestBody())
            "PATCH"  -> rb.patch(body ?: "".toRequestBody())
            "DELETE" -> rb.delete()
            "HEAD"   -> rb.head()
        }

        val resp = client.newCall(rb.build()).execute()
        if (resp.code == 429) { resp.close(); throw ReCaptchaException("Rate limited", url) }

        return NPResponse(
            resp.code,
            resp.message,
            resp.headers.toMultimap(),
            resp.body?.string() ?: "",
            resp.request.url.toString(),
        )
    }

    companion object {
        @Volatile private var INSTANCE: NewPipeDownloader? = null

        fun instance(): NewPipeDownloader = INSTANCE ?: synchronized(this) {
            INSTANCE ?: NewPipeDownloader(
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build(),
            ).also { INSTANCE = it }
        }
    }
}
