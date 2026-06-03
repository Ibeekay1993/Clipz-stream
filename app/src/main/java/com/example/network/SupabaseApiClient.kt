package com.example.network

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class SupabaseProject(
    val id: Long? = null,
    val title: String,
    @Json(name = "source_url") val sourceUrl: String,
    @Json(name = "thumbnail_url") val thumbnailUrl: String,
    @Json(name = "duration_seconds") val durationSeconds: Long,
    val transcript: String
)

@JsonClass(generateAdapter = true)
data class SupabaseClip(
    val id: Long? = null,
    @Json(name = "project_id") val projectId: Long,
    val title: String,
    @Json(name = "start_sec") val startSec: Int,
    @Json(name = "end_sec") val endSec: Int,
    @Json(name = "viral_score") val viralScore: Int,
    @Json(name = "viral_reason") val viralReason: String,
    @Json(name = "aspect_ratio") val aspectRatio: String,
    @Json(name = "caption_style") val captionStyle: String,
    @Json(name = "pan_offset") val panOffset: Float,
    @Json(name = "captions_json") val captionsJson: String,
    @Json(name = "is_exported") val isExported: Boolean
)

interface SupabaseApiService {
    @POST("projects")
    suspend fun insertProject(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body project: SupabaseProject
    ): List<SupabaseProject>

    @POST("clips")
    suspend fun insertClips(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Body clips: List<SupabaseClip>
    ): List<SupabaseClip>
}

object SupabaseApiClient {
    private const val DEFAULT_PROJECT_ID = "cgwlmqhdmcmkxkyoqiix"
    private const val BASE_REST_URL = "https://cgwlmqhdmcmkxkyoqiix.supabase.co/rest/v1/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getBaseUrl(): String {
        return try {
            val configUrl = try {
                com.example.BuildConfig::class.java.getField("SUPABASE_URL").get(null) as? String
            } catch (e: Exception) {
                null
            }
            if (!configUrl.isNullOrBlank() && configUrl != "https://cgwlmqhdmcmkxkyoqiix.supabase.co/") {
                if (configUrl.endsWith("rest/v1/")) {
                    configUrl
                } else if (configUrl.endsWith("/")) {
                    "${configUrl}rest/v1/"
                } else {
                    "$configUrl/rest/v1/"
                }
            } else {
                BASE_REST_URL
            }
        } catch (e: Exception) {
            BASE_REST_URL
        }
    }

    val service: SupabaseApiService by lazy {
        Retrofit.Builder()
            .baseUrl(getBaseUrl())
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseApiService::class.java)
    }

    /**
     * Helper to synchronise a local project and its clips to Supabase Cloud vault.
     */
    suspend fun syncToSupabase(
        anonKey: String,
        project: com.example.data.model.Project,
        clips: List<com.example.data.model.Clip>
    ): Boolean {
        if (anonKey.trim().isBlank()) {
            Log.e("SupabaseSync", "Aborting sync: Anon Key is empty.")
            return false
        }

        return try {
            val bearer = "Bearer ${anonKey.trim()}"
            val sProject = SupabaseProject(
                title = project.title,
                sourceUrl = project.sourceUrl,
                thumbnailUrl = project.thumbnailUrl,
                durationSeconds = project.durationSeconds,
                transcript = project.transcript
            )

            Log.d("SupabaseSync", "Uploading project definition to Supabase: ${project.title}")
            val resultProjects = service.insertProject(
                apiKey = anonKey.trim(),
                bearer = bearer,
                project = sProject
            )

            val uploadedId = resultProjects.firstOrNull()?.id
            if (uploadedId == null) {
                Log.e("SupabaseSync", "Failed to retrieve generated ID from Supabase response.")
                return false
            }

            Log.d("SupabaseSync", "Project synced. Assigned Cloud ID: $uploadedId")

            if (clips.isNotEmpty()) {
                val sClips = clips.map { c ->
                    SupabaseClip(
                        projectId = uploadedId,
                        title = c.title,
                        startSec = c.startSec,
                        endSec = c.endSec,
                        viralScore = c.viralScore,
                        viralReason = c.viralReason,
                        aspectRatio = c.aspectRatio,
                        captionStyle = c.captionStyle,
                        panOffset = c.panOffset,
                        captionsJson = c.captionsJson,
                        isExported = c.isExported
                    )
                }

                Log.d("SupabaseSync", "Uploading ${sClips.size} child clips to Supabase...")
                service.insertClips(
                    apiKey = anonKey.trim(),
                    bearer = bearer,
                    clips = sClips
                )
            }

            Log.d("SupabaseSync", "Sync completed successfully!")
            true
        } catch (e: Exception) {
            Log.e("SupabaseSync", "Sync exception occurred: ${e.message}", e)
            false
        }
    }
}
