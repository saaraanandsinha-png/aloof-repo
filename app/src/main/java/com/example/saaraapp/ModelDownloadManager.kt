package com.example.saaraapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the FunctionGemma 270M (int8) model file on first launch
 * and stores it in the app's private files directory.
 *
 * The model is only downloaded once — subsequent launches reuse the cached file.
 */
object ModelDownloadManager {

    private const val TAG = "ModelDownload"

    /**
     * Filename used to store the model locally.
     * Updated to match the actual file in assets.
     */
    private const val MODEL_FILE_NAME = "functiongemma-270m-it-Q4_K_M.gguf"

    /**
     * FunctionGemma 270M IT in GGUF format.
     */
    private const val MODEL_URL =
        "https://huggingface.co/Llama-2-7b-chat-hf/resolve/main/Llama-2-7b-chat-hf.Q4_K_M.gguf" // Placeholder URL

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the local model [File] if it already exists, otherwise null. */
    fun getModelFileIfExists(context: Context): File? {
        val file = modelFile(context)
        return if (file.exists() && file.length() > 0L) file else null
    }

    /**
     * Downloads the model if it hasn't been downloaded yet.
     *
     * @param onProgress  Called with download progress 0–100.
     * @param onComplete  Called with the [File] on success, or null on failure.
     */
    suspend fun downloadIfNeeded(
        context: Context,
        onProgress: (Int) -> Unit = {},
        onComplete: (File?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val file = modelFile(context)

        // Already downloaded — nothing to do
        if (file.exists() && file.length() > 0L) {
            Log.i(TAG, "Model already cached at ${file.absolutePath}")
            withContext(Dispatchers.Main) { onComplete(file) }
            return@withContext
        }

        Log.i(TAG, "Starting model download from $MODEL_URL")

        try {
            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout    = 60_000
                requestMethod  = "GET"
                connect()
            }

            val totalBytes = connection.contentLengthLong
            var downloaded = 0L

            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (totalBytes > 0) {
                            val progress = ((downloaded * 100) / totalBytes).toInt()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                }
            }

            Log.i(TAG, "Model downloaded successfully (${file.length() / 1_048_576} MB)")
            withContext(Dispatchers.Main) { onComplete(file) }

        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}")
            file.delete() // remove partial file
            withContext(Dispatchers.Main) { onComplete(null) }
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun modelFile(context: Context): File =
        File(context.filesDir, MODEL_FILE_NAME)
}
