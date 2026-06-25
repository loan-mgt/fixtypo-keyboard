package org.futo.inputmethod.latin.uix.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String
)

@Serializable
data class DeepSeekRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.0,
    val max_tokens: Int = 1024,
    val stream: Boolean = true,
    val response_format: DeepSeekResponseFormat? = null,
    val thinking: DeepSeekThinking? = null
)

@Serializable
data class DeepSeekThinking(
    val type: String
)

@Serializable
data class DeepSeekResponseFormat(
    val type: String
)

@Serializable
data class DeepSeekChoice(
    val index: Int = 0,
    val message: DeepSeekMessage? = null
)

@Serializable
data class DeepSeekUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)

@Serializable
data class DeepSeekResponse(
    val id: String = "",
    val choices: List<DeepSeekChoice> = emptyList(),
    val usage: DeepSeekUsage? = null
)

@Serializable
data class DeepSeekErrorResponse(
    val error: DeepSeekErrorDetail? = null
)

@Serializable
data class DeepSeekErrorDetail(
    val message: String = "",
    val type: String = "",
    val code: String = ""
)

@Serializable
data class AIJsonOutput(
    val corrected_text: String = ""
)

@Serializable
data class DeepSeekStreamDelta(
    val content: String? = null
)

@Serializable
data class DeepSeekStreamChoice(
    val delta: DeepSeekStreamDelta? = null,
    val finish_reason: String? = null
)

@Serializable
data class DeepSeekStreamChunk(
    val choices: List<DeepSeekStreamChoice>? = null
)

private val SYSTEM_PROMPT = """
You are a text correction tool. The user will provide text that may contain typos or grammar errors. Return ONLY a JSON object with the corrected text. Do NOT explain the changes. The output must be exactly: {"corrected_text": "the corrected text here"}

Rules:
- Fix ONLY spelling mistakes and grammar errors
- Do NOT change capitalization, punctuation, or word choice
- Preserve the original meaning, tone, emojis, casing, and all punctuation marks as-is
- Do NOT add or remove information
- Return the SAME text if there are no errors
""".trimIndent()

class DeepSeekClient(
    val apiKey: String,
    private val model: String = "deepseek-v4-flash"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun correctText(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = DeepSeekRequest(
                model = model,
                messages = listOf(
                    DeepSeekMessage(role = "system", content = SYSTEM_PROMPT),
                    DeepSeekMessage(role = "user", content = "Correct this text: $text")
                ),
                temperature = 0.0,
                max_tokens = 1024,
                thinking = DeepSeekThinking(type = "disabled")
            )

            val requestBody = json.encodeToString(DeepSeekRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(httpRequest)

                continuation.invokeOnCancellation {
                    call.cancel()
                }

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onResponse(call: Call, response: okhttp3.Response) {
                        try {
                            response.use { resp ->
                                if (!resp.isSuccessful) {
                                    val body = resp.body?.string() ?: ""
                                    if (continuation.isActive) {
                                        continuation.resume(Result.failure(Exception("DeepSeek API error: HTTP ${resp.code} $body")))
                                    }
                                    return
                                }

                                val contentType = resp.header("content-type") ?: ""
                                val bodyString = resp.body?.string() ?: ""

                                if (!continuation.isActive) return

                                val fullContent = if (contentType?.contains("stream") == true || bodyString.startsWith("data: ")) {
                                    parseStreamResponse(bodyString)
                                } else {
                                    parseJsonResponse(bodyString)
                                }

                                if (fullContent == null) {
                                    if (continuation.isActive) {
                                        continuation.resume(Result.failure(Exception("Empty stream response")))
                                    }
                                    return
                                }

                                if (fullContent.isBlank()) {
                                    if (continuation.isActive) {
                                        continuation.resume(Result.failure(Exception("Empty correction output")))
                                    }
                                    return
                                }

                                val output = parseOutput(fullContent)
                                if (!continuation.isActive) return
                                if (output.corrected_text.isBlank()) {
                                    continuation.resume(Result.failure(Exception("Empty correction from DeepSeek")))
                                } else {
                                    continuation.resume(Result.success(output.corrected_text))
                                }
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                        }
                    }

                    private fun parseStreamResponse(body: String): String? {
                        val accumulated = StringBuilder()
                        var hasData = false

                        body.lines().forEach { line ->
                            if (!line.startsWith("data: ")) return@forEach
                            val data = line.removePrefix("data: ")
                            if (data == "[DONE]") return@forEach

                            try {
                                val chunk = json.decodeFromString(DeepSeekStreamChunk.serializer(), data)
                                val delta = chunk.choices?.firstOrNull()?.delta?.content
                                if (delta != null) {
                                    accumulated.append(delta)
                                    hasData = true
                                }
                            } catch (_: Exception) {}
                        }

                        return if (hasData) accumulated.toString().trim() else null
                    }

                    private fun parseJsonResponse(body: String): String? {
                        return try {
                            val resp = json.decodeFromString(DeepSeekResponse.serializer(), body)
                            resp.choices.firstOrNull()?.message?.content
                        } catch (_: Exception) {
                            null
                        }
                    }
                })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseOutput(content: String): AIJsonOutput {
        return try {
            json.decodeFromString(AIJsonOutput.serializer(), content)
        } catch (_: Exception) {
            try {
                val corrected = content
                    .removeSurrounding("{", "}")
                    .substringAfter("\"corrected_text\":")
                    .trim()
                    .removeSurrounding("\"")
                    .replace("\\n", "\n")
                AIJsonOutput(corrected_text = corrected)
            } catch (_: Exception) {
                AIJsonOutput(corrected_text = content)
            }
        }
    }
}
