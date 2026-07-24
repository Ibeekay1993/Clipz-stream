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
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody
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

@JsonClass(generateAdapter = true)
data class CreateJobResponse(
    val job_id: String,
    val status: String
)

@JsonClass(generateAdapter = true)
data class JobStatusResponse(
    val job_id: String,
    val status: String,
    val progress: Int = 0,
    val current_step: String? = null,
    val result: BackendProcessResponse? = null,
    val error: String? = null
)

interface BackendApiService {
    @POST("api/process")
    suspend fun processVideo(
        @Body request: BackendProcessRequest
    ): BackendProcessResponse

    @POST("api/jobs/create")
    suspend fun createJob(
        @Body request: BackendProcessRequest
    ): CreateJobResponse

    @retrofit2.http.GET("api/jobs/status/{job_id}")
    suspend fun getJobStatus(
        @retrofit2.http.Path("job_id") jobId: String
    ): JobStatusResponse

    @Multipart
    @POST("api/upload")
    suspend fun uploadVideo(
        @Part file: MultipartBody.Part,
        @Part("num_clips") numClips: okhttp3.RequestBody
    ): BackendProcessResponse
}

object BackendApiClient {
    // Primary Modal serverless GPU endpoint that runs Whisper & FFmpeg
    private const val DEFAULT_URL = "https://ibeekay1993--clipz-stream-fastapi-app-fastapi-app.modal.run/"

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
