package com.example.engine

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GeminiService"
        const val MODEL_NAME = "gemini-3.5-flash"
    }

    /**
     * تواصل مع Gemini API باستخدام نموذج gemini-3.5-flash
     */
    suspend fun chatWithGemini(
        prompt: String,
        systemInstruction: String = "",
        history: List<Pair<String, String>> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("لم يتم تهيئة مفتاح API الخاص بـ Gemini. يرجى إدخال المفتاح بلقطة الأسرار (Secrets Panel) لتشغيل الواجهة الذكية."))
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

            val contentsArray = JSONArray()

            // إضافة السجل التفاعلي من المحادثة لحفظ السياق
            for (turn in history) {
                val role = if (turn.first == "user") "user" else "model"
                contentsArray.put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().put(JSONObject().put("text", turn.second)))
                })
            }

            // إضافة الرسالة الحالية
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            })

            val jsonBody = JSONObject().apply {
                put("contents", contentsArray)

                if (systemInstruction.isNotEmpty()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                    })
                }

                // إضافة التكوينات القياسية لخفض التكلفة والانحياز
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 2048)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed with code ${response.code}: $bodyStr")
                    return@withContext Result.failure(Exception("خطأ من السيرفر (${response.code}): ${response.message}"))
                }

                val responseJson = JSONObject(bodyStr)
                val candidates = responseJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val replyText = parts.getJSONObject(0).optString("text") ?: ""
                        return@withContext Result.success(replyText)
                    }
                }
                
                return@withContext Result.failure(Exception("تجاوب فارغ من نموذج Gemini."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during chatWithGemini", e)
            Result.failure(e)
        }
    }
}
