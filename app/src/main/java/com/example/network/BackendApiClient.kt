package com.example.network

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class BackendProcessRequest(
    val url: String,
    val num_clips: Int = 3
)

@JsonClass(generateAdapter = true)
data class BackendProcessResponse(
    val url: String,
    val duration: Double,
    val clips: List<BackendClipOutput>
)

@JsonClass(generateAdapter = true)
data class BackendClipOutput(
    val title: String,
    val startSec: Int,
    val endSec: Int,
    val viralScore: Int,
    val viralReason: String,
    val captions: List<BackendWordCaption>?,
    val clipUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendWordCaption(
    val word: String,
    val startMs: Long,
    val endMs: Long
)

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.Part

interface BackendApiService {
    @POST("api/process")
    suspend fun processVideo(
        @Body request: BackendProcessRequest
    ): BackendProcessResponse

    @Multipart
    @POST("api/upload")
    suspend fun uploadVideo(
        @Part file: MultipartBody.Part,
        @Part("num_clips") numClips: okhttp3.RequestBody
    ): BackendProcessResponse
}

object BackendApiClient {
    // Primary Render endpoint that downloads audio, transcribes with Whisper, and segments highlights
    private const val DEFAULT_URL = "https://clipz-stream.onrender.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(180, TimeUnit.SECONDS) // FastAPI Whisper transcription can take a while on CPU
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    fun getBaseUrl(): String {
        return try {
            val configUrl = try {
                com.example.BuildConfig::class.java.getField("BACKEND_URL").get(null) as? String
            } catch (e: Exception) {
                null
            }
            if (!configUrl.isNullOrBlank() && configUrl != "https://YOUR-RENDER-URL.onrender.com/") {
                if (configUrl.endsWith("/")) configUrl else "$configUrl/"
            } else {
                DEFAULT_URL
            }
        } catch (e: Exception) {
            DEFAULT_URL
        }
    }

    val service: BackendApiService by lazy {
        Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BackendApiService::class.java)
    }
}
