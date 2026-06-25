package com.example.saaraapp

import android.content.Context
import android.util.Log
import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


/**
 * Wraps Llama.cpp (via LLamatik) to run FunctionGemma 270M on-device.
 * Using streaming generation for better reliability and stop-sequence handling.
 */
class FunctionGemmaHelper(private val context: Context) {

    // ── Initialise ────────────────────────────────────────────────────────────

    /**
     * Loads the model. If [modelFile] does not exist, it tries to copy it
     * from assets to the internal files directory first.
     */
    suspend fun initialize(modelFile: File) = withContext(Dispatchers.IO) {
        try {
            if (isReady) return@withContext

            var finalFile = modelFile
            
            // If the provided file doesn't exist, try to copy from assets
            if (!finalFile.exists()) {
                val assetName = modelFile.name
                val internalFile = File(context.filesDir, assetName)
                
                if (internalFile.exists() && internalFile.length() > 0) {
                    finalFile = internalFile
                } else {
                    try {
                        context.assets.open(assetName).use { input ->
                            internalFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        finalFile = internalFile
                        Log.i(TAG, "Copied $assetName from assets to internal storage")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not find/copy $assetName from assets: ${e.message}")
                    }
                }
            }

            if (!finalFile.exists()) {
                Log.e(TAG, "Model file not found at ${finalFile.absolutePath}")
                return@withContext
            }

            // Optimized parameters for mobile:
            // useMmap = true allows loading only parts of the model needed, drastically 
            // improving initial load time and reducing RAM pressure.
            LlamaBridge.updateGenerateParams(
                temperature = 0.1f,
                maxTokens = 64,       
                topP = 0.95f,
                topK = 40,
                repeatPenalty = 1.1f,
                contextLength = 1024,  
                numThreads = 4,        
                useMmap = true,        
                flashAttention = true, // Enable if library supports, otherwise it ignores
                batchSize = 256,
                gpuLayers = 0
            )

            val ok = LlamaBridge.initGenerateModel(finalFile.absolutePath)
            if (ok) {
                // Apply again after init to ensure settings are active
                LlamaBridge.updateGenerateParams(
                    temperature = 0.1f,
                    maxTokens = 128,
                    topP = 0.95f,
                    topK = 40,
                    repeatPenalty = 1.1f,
                    contextLength = 1024,
                    numThreads = 4,
                    useMmap = true,
                    flashAttention = false,
                    batchSize = 512,
                    gpuLayers = 0
                )
                isReady = true
                Log.i(TAG, "Qwen3 loaded and tuned from ${finalFile.name}")
            } else {
                Log.e(TAG, "LlamaBridge failed to load model from ${finalFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "LlamaBridge error: ${e.message}")
            isReady = false
        }
    }

    // ── Analyze ───────────────────────────────────────────────────────────────

    /**
     * Analyzes [message] and returns a [GemmaResult].
     */
    suspend fun analyze(message: String): GemmaResult = withContext(Dispatchers.Default) {
        if (!isReady) {
            Log.d(TAG, "Model not ready — using KeywordExtractor fallback")
            return@withContext fallback(message)
        }

        return@withContext try {
            val prompt = buildPrompt(message)
            Log.d(TAG, "Starting generation...")
            
            val rawResponse = generateInternal(prompt)
            Log.d(TAG, "Generation finished. Raw length: ${rawResponse.length}")
            
            // Prepend { because the prompt pre-fills the opening brace
            val response = "{" + rawResponse
            parseResponse(response) ?: fallback(message)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message} — using fallback")
            fallback(message)
        }
    }

    /**
     * General chat method that returns the raw string from the model.
     * Supports an optional [context] block (e.g. today's reminders) to help the model.
     */
    suspend fun askGemma(message: String, context: String? = null): String = withContext(Dispatchers.Default) {
        if (!isReady) return@withContext "Model not ready."

        try {
            val userPrompt = if (context != null) {
                "You are Saara, a personal assistant. Use the context below to help the user.\n\n" +
                "CONTEXT OF TODAY'S REMINDERS:\n$context\n\n" +
                "USER QUESTION: $message"
            } else {
                "You are Saara, a personal assistant. Respond to: $message"
            }
            
            val messages = listOf("user" to userPrompt)
            val prompt = LlamaBridge.applyChatTemplate(messages, true) ?: userPrompt
            Log.d(TAG, "Chatting with prompt: $prompt")
            generateInternal(prompt)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Internal helper that wraps streaming generation into a suspend function.
     * Manually checks for stop sequences to prevent infinite generation.
     */
    private suspend fun generateInternal(prompt: String): String = suspendCancellableCoroutine { cont ->
        val output = StringBuilder()
        var deltaCount = 0
        val callback = object : GenStream {
            override fun onDelta(text: String) {
                deltaCount++
                // Log for debugging to see what's causing cancels
                Log.d(TAG, "onDelta[$deltaCount]: '$text'")

                // Manual stop sequence check. 
                // We wait for a few deltas to pass to avoid catching prompt echo.
                if (deltaCount > 3) {
                    if (text.contains("<|im_end|>") || text.contains("<end_of_turn>") || text.contains("User:")) {
                        Log.d(TAG, "Stop sequence detected, requested cancel.")
                        LlamaBridge.nativeCancelGenerate()
                        if (cont.isActive) cont.resume(output.toString().trim())
                        return
                    }
                }
                output.append(text)
            }

            override fun onComplete() {
                if (cont.isActive) cont.resume(output.toString().trim())
            }

            override fun onError(message: String) {
                Log.e(TAG, "Streaming error: $message")
                if (cont.isActive) cont.resumeWithException(Exception(message))
            }
        }

        LlamaBridge.generateStream(prompt, callback)

        cont.invokeOnCancellation {
            LlamaBridge.nativeCancelGenerate()
        }
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    /**
     * Builds a model prompt using the chat template.
     * Ends with { to force the model to start the JSON immediately.
     */
    private fun buildPrompt(message: String): String {
        val messages = listOf(
            "user" to "Extract reminder data. Return ONLY JSON.\nMessage: \"$message\"\nSchema: {\"is_reminder\":bool, \"category\":string, \"date\":string, \"time\":string, \"tags\":[]}"
        )
        val template = LlamaBridge.applyChatTemplate(messages, true) ?: ""
        // If template doesn't include assistant tag at the end, add Qwen style fallback
        val finalPrompt = if (template.isBlank()) {
            "<|im_start|>user\nExtract reminder data. Return ONLY JSON.\nMessage: \"$message\"\nSchema: {\"is_reminder\":bool, \"category\":string, \"date\":string, \"time\":string, \"tags\":[]}<|im_end|>\n<|im_start|>assistant\n"
        } else template

        return finalPrompt + "{"
    }

    // ── Response parser ───────────────────────────────────────────────────────

    /**
     * Parses the raw model output into a [GemmaResult].
     * Returns null if the JSON is malformed (caller falls back to keyword rules).
     */
    private fun parseResponse(raw: String): GemmaResult? {
        return try {
            // Strip any markdown code fences and find the JSON block
            val json = raw
                .replace(Regex("```json|```"), "")
                .trim()
                .let { text ->
                    val start = text.indexOf('{')
                    val end   = text.lastIndexOf('}')
                    if (start != -1 && end != -1 && end > start) {
                        text.substring(start, end + 1)
                    } else {
                        null
                    }
                } ?: return null // Fail if no valid { } block is found

            val obj        = JSONObject(json)
            val isReminder = obj.optBoolean("is_reminder", false)
            val category   = parseCategory(obj.optString("category", "OTHER"))
            val dateText   = obj.optString("date").takeIf { it.isNotBlank() && it != "null" }
            val timeText   = obj.optString("time").takeIf { it.isNotBlank() && it != "null" }

            val tagsArray  = obj.optJSONArray("tags")
            val tags       = buildList {
                if (tagsArray != null) {
                    for (i in 0 until tagsArray.length()) add(tagsArray.getString(i))
                }
            }

            GemmaResult(
                isReminder   = isReminder,
                category     = category,
                dateText     = dateText,
                timeText     = timeText,
                tags         = tags,
                fromFallback = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse failed: ${e.message}")
            null
        }
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    /** Rule-based fallback using the existing KeywordExtractor. */
    private fun fallback(message: String) = GemmaResult(
        isReminder   = KeywordExtractor.isRelevant(message),
        category     = KeywordExtractor.categorize(message),
        dateText     = null,
        timeText     = null,
        tags         = KeywordExtractor.extractTags(message),
        fromFallback = true
    )

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parseCategory(raw: String): ReminderCategory =
        ReminderCategory.entries.firstOrNull {
            it.name.equals(raw.trim(), ignoreCase = true)
        } ?: ReminderCategory.OTHER

    fun close() {
        LlamaBridge.shutdown()
        isReady = false
    }

    companion object {
        private const val TAG = "FunctionGemma"
        private var isReady: Boolean = false
    }
}
