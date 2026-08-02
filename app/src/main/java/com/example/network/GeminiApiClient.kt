package com.example.network

import com.example.BuildConfig
import com.example.data.model.WordTimestamp
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini Request / Response Models for Moshi ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = "application/json",
    val temperature: Float? = 0.4f
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?
)

// --- Clips output format schema requested from Gemini ---

@JsonClass(generateAdapter = true)
data class GeminiClipOutput(
    val title: String,
    val startSec: Int,
    val endSec: Int,
    val viralScore: Int,
    val viralReason: String,
    val captions: List<GeminiWordCaption>?
)

@JsonClass(generateAdapter = true)
data class GeminiWordCaption(
    val word: String,
    val startMs: Long,
    val endMs: Long
)

@JsonClass(generateAdapter = true)
data class GeminiClipsListResponse(
    val clips: List<GeminiClipOutput>
)

// --- Retrofit Service Definitions ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-2.0-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiApiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    suspend fun generateShortClips(
        videoTitle: String,
        videoDescription: String,
        transcriptContent: String,
        videoDurationSeconds: Long
    ): GeminiClipsListResponse? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return null // Fallback to mock data generation locally if key is missing/placeholder
        }

        val prompt = """
            You are an advanced AI Video Producer like Opus Clip or Nexus Clip. 
            Analyze the following video details and generate 3 viral short clips (duration between 15 and 60 seconds each, within the total video duration of $videoDurationSeconds seconds).
            
            Video Title: "$videoTitle"
            Video Description: "$videoDescription"
            Original Transcript:
            "$transcriptContent"
            
            Find the most high-impact, engaging hooks or self-contained segments that have viral potential. For each clip, you MUST produce:
            1. An engaging catchy title (e.g., "The Secret to 10x Scale", "Why AI will not replace you").
            2. Exact start and end seconds of the clip (e.g., startSec: 10, endSec: 42).
            3. A viral score from 50 to 100 based on core value, hook strength, and completeness.
            4. A clear, punchy "viralReason" explaining why this clip is viral.
            5. An array of timing-aligned words ("captions") for subtitles within that clip's range. Each word MUST have startMs and endMs relative to the start of the whole video (clip start to end). Make sure the list of words is clean, matches the transcript, and matches the startSec/endSec!
            
            Provide the response STRICTLY as a JSON object matching this schema:
            {
               "clips": [
                  {
                     "title": "Clip Title",
                     "startSec": 15,
                     "endSec": 45,
                     "viralScore": 98,
                     "viralReason": "Brief explanation...",
                     "captions": [
                        {"word": "Let's", "startMs": 15000, "endMs": 15300},
                        {"word": "get", "startMs": 15300, "endMs": 15500},
                        {"word": "started.", "startMs": 15500, "endMs": 16000}
                     ]
                  }
               ]
            }
            Do not include any explanation, markdown, or text outer markers besides the raw JSON.
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
            generationConfig = GeminiGenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.3f
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = "You are a professional social media video editor. You output valid JSON only."))
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val jsonText = response.candidates?.flatMap { it.content?.parts ?: emptyList() }
                ?.firstOrNull()?.text ?: return null
            
            // Clean markdown syntax if present
            val cleanedJsonText = jsonText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            
            val adapter = moshi.adapter(GeminiClipsListResponse::class.java)
            adapter.fromJson(cleanedJsonText)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
