package com.immichframe.immichframe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

internal object GalleryImage {
    class PhotoMissing : IllegalStateException("Photo is no longer in the gallery")
    fun load(base: String, secret: String, frame: String, cursor: String, step: Int): Pair<String, Bitmap> {
        fun read(path: String): ByteArray {
            val connection = URL(base + "api/gallery" + path).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 7000
                connection.readTimeout = 15000
                if (secret.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $secret")
                if (path.startsWith("?from=") && connection.responseCode == 404) throw PhotoMissing()
                check(connection.responseCode == 200) { "Gallery HTTP ${connection.responseCode}" }
                return connection.inputStream.use { it.readBytes() }
            } finally { connection.disconnect() }
        }
        val query = if (cursor.isEmpty()) "?limit=1" else "?from=" + URLEncoder.encode(cursor, "UTF-8") + "&step=$step"
        val row = JSONObject(String(read(query), Charsets.UTF_8)).getJSONArray("photos").getJSONObject(0)
        val key = row.getString("key")
        val edit = JSONObject(String(read("/$key/crop/$frame"), Charsets.UTF_8))
        val bytes = read("/$key/image")
        val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val matrix = Matrix().apply {
            postRotate(90f * edit.getInt("turns"))
            postScale(if (edit.getBoolean("flipX")) -1f else 1f, if (edit.getBoolean("flipY")) -1f else 1f)
        }
        val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (oriented !== source) source.recycle()
        val x = (edit.getDouble("x") * oriented.width).roundToInt().coerceIn(0, oriented.width - 1)
        val y = (edit.getDouble("y") * oriented.height).roundToInt().coerceIn(0, oriented.height - 1)
        val width = (edit.getDouble("width") * oriented.width).roundToInt().coerceIn(1, oriented.width - x)
        val height = (edit.getDouble("height") * oriented.height).roundToInt().coerceIn(1, oriented.height - y)
        val result = Bitmap.createBitmap(oriented, x, y, width, height)
        if (result !== oriented) oriented.recycle()
        return key to result
    }
}
