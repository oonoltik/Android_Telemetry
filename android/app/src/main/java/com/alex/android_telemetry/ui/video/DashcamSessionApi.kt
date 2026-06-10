package com.alex.android_telemetry.ui.video

import android.content.Context
import android.util.Log
import com.alex.android_telemetry.BuildConfig
import com.alex.android_telemetry.telemetry.auth.TelemetryAuthApi
import com.alex.android_telemetry.telemetry.auth.TelemetryAuthManager
import com.alex.android_telemetry.telemetry.auth.TelemetryDeviceIdProvider
import com.alex.android_telemetry.telemetry.auth.TelemetryKeyIdStore
import com.alex.android_telemetry.telemetry.auth.TelemetryTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@Serializable
data class VideoSessionStopPayload(
    val video_session_id: String,
    val ended_at: String,
    val stop_reason: String,
    val final_linked_trip_session_id: String? = null,
    val segments_count: Int? = null,
    val total_size_bytes: Long? = null,
)

@Serializable
data class DashcamCameraLogPayload(
    val video_session_id: String,
    val linked_trip_session_id: String? = null,
    val driver_id: String,
    val device_id: String,
    val started_at: String,
    val ended_at: String? = null,

    val recording_start_lat: Double? = null,
    val recording_start_lon: Double? = null,
    val recording_end_lat: Double? = null,
    val recording_end_lon: Double? = null,

    val session_start_sample_t: String? = null,
    val session_end_sample_t: String? = null,
    val total_samples: Int? = null,
    val total_events: Int? = null,

    val session_start_speed_kmh: Double? = null,
    val session_end_speed_kmh: Double? = null,
    val session_event_types: List<String>? = null,

    val stop_reason: String? = null,
    val camera_mode: String,
    val audio_enabled: Boolean,

    val is_crash_log: Boolean = false,
    val crash_detected_at: String? = null,
    val crash_lat: Double? = null,
    val crash_lon: Double? = null,
    val crash_max_g: Double? = null,

    val total_size_bytes: Long? = null,
    val total_segments_count: Int? = null,

    val archive_normal_count: Int,
    val archive_crash_count: Int,
    val archive_normal_size_bytes: Long,
    val archive_crash_size_bytes: Long,
)

class DashcamSessionApi(
    context: Context,
) {
    private val appContext =
        context.applicationContext

    private val euBaseUrl =
        "https://api.drivetelemetry.com"

    private val ruBaseUrl =
        "https://ru-api.drivetelemetry.com"

    private val activeBaseUrl =
        ruBaseUrl

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val deviceIdProvider =
        TelemetryDeviceIdProvider(appContext)

    private val authManager =
        TelemetryAuthManager(
            authApi = TelemetryAuthApi(
                euBaseUrl = euBaseUrl,
                ruBaseUrl = ruBaseUrl,
                androidRegisterKey = BuildConfig.ANDROID_REGISTER_KEY,
                client = client,
                json = json,
            ),
            tokenStore = TelemetryTokenStore(appContext),
            keyIdStore = TelemetryKeyIdStore(appContext),
            deviceIdProvider = deviceIdProvider,
        )

    suspend fun startVideoSession(
        payload: VideoSessionStartPayload,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val token =
                authManager.getValidToken()

            postJson(
                path = "/video/session/start",
                token = token,
                bodyString = json.encodeToString(payload),
            )

            true
        }

    suspend fun stopVideoSession(
        payload: VideoSessionStopPayload,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val token =
                authManager.getValidToken()

            postJson(
                path = "/video/session/stop",
                token = token,
                bodyString = json.encodeToString(payload),
            )

            true
        }

    suspend fun postCameraLog(
        payload: DashcamCameraLogPayload,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val token =
                authManager.getValidToken()

            val bodyString =
                json.encodeToString(payload)

            Log.d(
                "DashcamSessionApi",
                "camera-log payload=$bodyString",
            )

            postJson(
                path = "/video/camera-log",
                token = token,
                bodyString = bodyString,
            )

            true
        }
    private fun postJson(
        path: String,
        token: String,
        bodyString: String,
    ) {
        Log.d("DashcamSessionApi", "POST $path started")

        val request =
            Request.Builder()
                .url("${activeBaseUrl.trimEnd('/')}$path")
                .post(bodyString.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Connection", "close")
                .build()

        client.newCall(request).execute().use { response ->
            val responseBody =
                response.body?.string().orEmpty()

            Log.d(
                "DashcamSessionApi",
                "POST $path response=${response.code} body=$responseBody",
            )

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "POST $path failed: HTTP ${response.code}: $responseBody"
                )
            }
        }
    }

    companion object {
        fun isoUtc(
            timestampMs: Long,
        ): String {
            return SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.US,
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(timestampMs))
        }
    }
}