package com.jody.freshfood.ml

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloader(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadAndVerify(modelName: String, downloadUrl: String, expectedSha256: String): Result<File> {
        return try {
            val request = Request.Builder().url(downloadUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("HTTP ${response.code} when downloading $modelName"))
                }

                val body: ResponseBody = response.body ?: return Result.failure(Exception("Empty response body for $modelName"))

                val tempFile = File(context.cacheDir, "${modelName}_temp.tflite")
                if (tempFile.exists()) tempFile.delete()

                var digest = MessageDigest.getInstance("SHA-256")

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                        }
                        output.flush()
                    }
                }

                val computed = digest.digest().joinToString("") { "%02x".format(it) }
                if (!computed.equals(expectedSha256, ignoreCase = true)) {
                    tempFile.delete()
                    return Result.failure(SecurityException("SHA256 mismatch for $modelName"))
                }

                val modelsDir = File(context.filesDir, "models")
                if (!modelsDir.exists()) modelsDir.mkdirs()
                val finalFile = File(modelsDir, "$modelName.tflite")
                if (finalFile.exists()) finalFile.delete()
                val moved = tempFile.renameTo(finalFile)
                if (!moved) {
                    // fallback to copy
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }

                Result.success(finalFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Streaming SHA256 for arbitrary files (unused here but available)
    fun sha256Stream(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }
}
