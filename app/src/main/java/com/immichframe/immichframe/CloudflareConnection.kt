package com.immichframe.immichframe

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal class CloudflareConnection(
    url: String,
    slideshowSecret: String,
    clientId: String,
    clientSecret: String,
    deviceKey: String,
) {
    val origin: HttpUrl = requireNotNull(url.trim().toHttpUrlOrNull()) { "Enter a valid HTTPS server URL." }

    init {
        require(origin.isHttps && origin.encodedPath == "/" && origin.query == null &&
            origin.fragment == null && origin.username.isEmpty() && origin.password.isEmpty()) {
            "Cloudflare mode requires an HTTPS hostname without a path."
        }
        require(clientId.isNotBlank() && clientSecret.isNotBlank() && deviceKey.length >= 32) {
            "Enter the Cloudflare Client ID, Client Secret, and device key."
        }
    }

    private val prefix = "/api/cloudflare/frame/"
    val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            check(original.url.isHttps && original.url.host == origin.host && original.url.port == origin.port)
            val path = when (original.url.encodedPath) {
                "/api/Config" -> prefix + "config"
                "/api/Weather" -> prefix + "weather"
                else -> original.url.encodedPath
            }
            check(path.startsWith(prefix))
            val request = original.newBuilder()
                .url(original.url.newBuilder().encodedPath(path).build())
                .header("CF-Access-Client-Id", clientId)
                .header("CF-Access-Client-Secret", clientSecret)
                .header("X-ImmichFrame-Device-Key", deviceKey)
            if (slideshowSecret.isNotEmpty()) request.header("Authorization", "Bearer $slideshowSecret")
            chain.proceed(request.build())
        }.build()

    fun url(path: String): HttpUrl = requireNotNull(origin.resolve(prefix + path))

    fun checkIn(frame: String, current: String): JSONObject? {
        val report = JSONObject().put("frame", frame)
            .put("current", current.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
        val request = Request.Builder().url(url("check-in"))
            .post(report.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Frame check-in HTTP ${response.code}" }
            return JSONObject(requireNotNull(response.body).string()).optJSONObject("command")
        }
    }
}
