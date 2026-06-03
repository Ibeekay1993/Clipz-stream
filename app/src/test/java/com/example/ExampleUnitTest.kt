package com.example

import com.example.network.BackendApiClient
import com.example.network.BackendClipOutput
import com.example.network.BackendProcessRequest
import com.example.network.BackendProcessResponse
import com.example.network.BackendWordCaption
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  
  private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testBackendBaseUrlFallback() {
    val baseUrl = BackendApiClient.getBaseUrl()
    assertNotNull(baseUrl)
    assertTrue(baseUrl.startsWith("http"))
    // Ensure the fallback points to our core hosted Render backend
    assertTrue(baseUrl.contains("clipz-stream.onrender.com") || baseUrl.contains("com.example.BuildConfig"))
  }

  @Test
  fun testMoshiRequestAndResponseSerialization() {
    // 1. Verify Request model serialization
    val request = BackendProcessRequest(
      url = "https://www.youtube.com/watch?v=jNQXAC9IVRw",
      num_clips = 3
    )
    val requestAdapter = moshi.adapter(BackendProcessRequest::class.java)
    val requestJson = requestAdapter.toJson(request)
    
    assertNotNull(requestJson)
    assertTrue(requestJson.contains("num_clips"))
    assertTrue(requestJson.contains("jNQXAC9IVRw"))

    // 2. Verify Response model serialization & compatibility with typical Render backend JSON outputs
    val dummyResponseJson = """
      {
        "url": "https://www.youtube.com/watch?v=jNQXAC9IVRw",
        "duration": 180.5,
        "clips": [
          {
            "title": "Unlocking AI Synergy",
            "startSec": 5,
            "endSec": 35,
            "viralScore": 92,
            "viralReason": "High attention-grabbing opening hook about tech nodes.",
            "captions": [
              {
                "word": "Welcome",
                "startMs": 5000,
                "endMs": 5300
              },
              {
                "word": "today",
                "startMs": 5300,
                "endMs": 5600
              }
            ]
          }
        ]
      }
    """.trimIndent()

    val responseAdapter = moshi.adapter(BackendProcessResponse::class.java)
    val response = responseAdapter.fromJson(dummyResponseJson)

    assertNotNull(response)
    assertEquals("https://www.youtube.com/watch?v=jNQXAC9IVRw", response?.url)
    assertEquals(180.5, response?.duration ?: 0.0, 0.01)
    assertEquals(1, response?.clips?.size)
    
    val clip = response?.clips?.get(0)
    assertEquals("Unlocking AI Synergy", clip?.title)
    assertEquals(5, clip?.startSec)
    assertEquals(35, clip?.endSec)
    assertEquals(92, clip?.viralScore)
    assertEquals("High attention-grabbing opening hook about tech nodes.", clip?.viralReason)
    assertNotNull(clip?.captions)
    assertEquals(2, clip?.captions?.size)
    
    val word1 = clip?.captions?.get(0)
    assertEquals("Welcome", word1?.word)
    assertEquals(5000L, word1?.startMs)
    assertEquals(5300L, word1?.endMs)
  }
}
