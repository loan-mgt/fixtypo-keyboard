package org.futo.inputmethod.latin.uix.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

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
    val stream: Boolean = false,
    val response_format: DeepSeekResponseFormat? = null
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
                response_format = DeepSeekResponseFormat(type = "json_object")
            )

            val requestBody = json.encodeToString(DeepSeekRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .post(requestBody)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(httpRequest).execute()

            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorResponse = try {
                    json.decodeFromString(DeepSeekErrorResponse.serializer(), responseBody)
                } catch (_: Exception) {
                    null
                }
                val errorMsg = errorResponse?.error?.message ?: "HTTP ${response.code}"
                return@withContext Result.failure(Exception("DeepSeek API error: $errorMsg"))
            }

            val deepSeekResponse = json.decodeFromString(DeepSeekResponse.serializer(), responseBody)
            val content = deepSeekResponse.choices.firstOrNull()?.message?.content
                ?: return@withContext Result.failure(Exception("Empty response from DeepSeek"))

            val output = try {
                json.decodeFromString(AIJsonOutput.serializer(), content)
            } catch (_: Exception) {
                try {
                    val corrected = content.trim()
                        .removeSurrounding("{", "}")
                        .substringAfter("\"corrected_text\":")
                        .trim()
                        .removeSurrounding("\"")
                        .replace("\\n", "\n")
                    AIJsonOutput(corrected_text = corrected)
                } catch (_: Exception) {
                    AIJsonOutput(corrected_text = content.trim())
                }
            }

            if (output.corrected_text.isBlank()) {
                return@withContext Result.failure(Exception("Empty correction from DeepSeek"))
            }

            Result.success(output.corrected_text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
