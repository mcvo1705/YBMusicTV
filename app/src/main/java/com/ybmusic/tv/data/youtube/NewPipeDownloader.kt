package com.ybmusic.tv.data.youtube

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request  as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "NewPipe/Downloader"

class NewPipeDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: NPRequest): NPResponse {
        val url    = request.url()
        val method = request.httpMethod()
        val rb     = Request.Builder().url(url)

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

        when (method) {
            "GET"    -> rb.get()
            "POST"   -> rb.post(body ?: "".toRequestBody())
            "PUT"    -> rb.put(body  ?: "".toRequestBody())
            "PATCH"  -> rb.patch(body ?: "".toRequestBody())
            "DELETE" -> rb.delete()
            "HEAD"   -> rb.head()
        }

        // Lỗi mạng (timeout, DNS, mất kết nối...) — log rõ rồi ném IOException để
        // NewPipe biết request thất bại, thay vì crash không rõ lý do.
        val resp = try {
            client.newCall(rb.build()).execute()
        } catch (e: IOException) {
            Log.e(TAG, "$method $url — network failure: ${e.message}", e)
            throw e
        }

        // Đọc body MỘT lần (string() tiêu thụ stream) rồi dùng lại cho cả log + trả về.
        val bodyStr = resp.body?.string() ?: ""

        if (resp.code == 429) {
            Log.w(TAG, "$method $url — HTTP 429 rate limited")
            resp.close()
            throw ReCaptchaException("Rate limited", url)
        }

        // Log phản hồi lỗi của extractor (HTTP >= 400) kèm một đoạn body để debug
        // khi YouTube trả về lỗi (chặn, đổi định dạng...).
        if (resp.code !in 200..299) {
            Log.w(TAG, "$method $url — HTTP ${resp.code} ${resp.message}; " +
                "body[0..300]=${bodyStr.take(300)}")
        }

        return NPResponse(
            resp.code,
            resp.message,
            resp.headers.toMultimap(),
            bodyStr,
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
