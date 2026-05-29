package com.alex.android_telemetry.ui.video

import android.content.Context
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
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

import java.util.Locale
import kotlin.math.ceil
import android.util.Log
import java.util.concurrent.TimeUnit

@Serializable
data class VideoSessionStartPayload(
    val video_session_id: String,
    val device_id: String,
    val driver_id: String,
    val started_at: String,
    val linked_trip_session_id: String? = null,
    val trip_source: String,
    val camera_mode: String,
    val audio_enabled: Boolean,
    val app_version: String? = null,
    val ios_version: String? = null,
    val device_model: String? = null,
)

@Serializable
data class CrashLogPayload(
    val crash_id: String,
    val video_session_id: String?,
    val trip_session_id: String?,
    val crash_detected_at: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val max_g: Double?,
    val active_segment_id: String?,
    val pre_window_sec: Int,
    val post_window_sec: Int,
    val nearest_sample_timestamp: String? = null,
    val nearest_speed_kmh: Double? = null,
    val nearest_heading: Double? = null,
    val event_types_nearby: List<String>? = null,
)

@Serializable
data class CrashClipMetadataPayload(
    val crash_clip_id: String,
    val video_session_id: String,
    val linked_trip_session_id: String? = null,
    val crash_detected_at: String,
    val pre_seconds: Int,
    val post_seconds: Int,
    val segment_ids: List<String>,
    val lat: Double? = null,
    val lon: Double? = null,
    val max_g: Double? = null,
    val speed_kmh: Double? = null,
)

@Serializable
data class UploadInitResponse(
    val session_id: String,
    val chunk_size: Int,
    val total_chunks: Int,
)

@Serializable
data class UploadStatusResponse(
    val status: String,
    val upload_session_id: String? = null,
    val chunk_size: Int = 0,
    val total_chunks: Int = 0,
    val uploaded_chunks: List<Int> = emptyList(),
    val next_chunk_index: Int = 0,
)

class CrashClipUploadApi(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val euBaseUrl =
        "https://api.drivetelemetry.com"

    private val ruBaseUrl =
        "https://ru-api.drivetelemetry.com"

//    private val activeBaseUrl =
//        euBaseUrl

    private val activeBaseUrl =
        ruBaseUrl

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
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

    suspend fun uploadCrashPackage(
        entity: CrashClipEntity,
        driverId: String,
        deviceId: String,
        cameraType: DashcamCameraType,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val mergedPath =
                entity.mergedClipPath ?: return@withContext false

            val file =
                File(mergedPath)

            if (!file.exists() || file.length() <= 0L) {
                return@withContext false
            }

            val videoSessionId =
                entity.rollingSessionId
                    ?: entity.crashId

            val token =
                authManager.getValidToken()

            val crashIso =
                java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    Locale.US,
                ).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(
                    java.util.Date(entity.detectedAtMs)
                )

            val cameraMode =
                when (cameraType) {
                    DashcamCameraType.ROAD -> "road"
                    DashcamCameraType.DRIVER -> "driver"
                }

            Log.d("CrashClipUpload", "step session/start crashId=${entity.crashId}")

            postJson(
                path = "/video/session/start",
                token = token,
                payload = VideoSessionStartPayload(
                    video_session_id = videoSessionId,
                    device_id = deviceId,
                    driver_id = driverId,
                    started_at = crashIso,
                    linked_trip_session_id = entity.telemetrySnapshot?.tripSessionId,
                    camera_mode = cameraMode,
                    app_version = "android-${BuildConfig.VERSION_NAME}",
                    device_model = android.os.Build.MODEL,
                    trip_source = "dashcam",
                    audio_enabled = true,
                ),
            )

            Log.d("CrashClipUpload", "step crash-log crashId=${entity.crashId}")

            postJson(
                path = "/video/crash-log",
                token = token,
                payload = CrashLogPayload(
                    crash_id = entity.crashId,
                    video_session_id = videoSessionId,
                    trip_session_id = entity.telemetrySnapshot?.tripSessionId,
                    crash_detected_at = crashIso,
                    latitude = entity.telemetrySnapshot?.lat,
                    longitude = entity.telemetrySnapshot?.lon,
                    max_g = entity.gForce,
                    active_segment_id = entity.segmentPaths.firstOrNull()?.substringAfterLast('/'),
                    pre_window_sec = (entity.preCrashMs / 1000L).toInt(),
                    post_window_sec = (entity.postCrashMs / 1000L).toInt(),
                    nearest_sample_timestamp = entity.telemetrySnapshot?.capturedAtIso,
                    nearest_speed_kmh = entity.telemetrySnapshot?.speedKmh,
                    nearest_heading = entity.telemetrySnapshot?.headingDeg,
                    event_types_nearby = emptyList(),
                ),
            )

            Log.d("CrashClipUpload", "step crash-clip metadata crashId=${entity.crashId}")

            postJson(
                path = "/video/crash-clip",
                token = token,
                payload = CrashClipMetadataPayload(
                    crash_clip_id = entity.crashId,
                    video_session_id = videoSessionId,
                    linked_trip_session_id = entity.telemetrySnapshot?.tripSessionId,
                    crash_detected_at = crashIso,
                    pre_seconds = (entity.preCrashMs / 1000L).toInt(),
                    post_seconds = (entity.postCrashMs / 1000L).toInt(),
                    segment_ids = entity.segmentPaths.map { path ->
                        path.substringAfterLast('/')
                    },
                    lat = entity.telemetrySnapshot?.lat,
                    lon = entity.telemetrySnapshot?.lon,
                    max_g = entity.gForce,
                    speed_kmh = entity.telemetrySnapshot?.speedKmh,
                ),
            )

            Log.d("CrashClipUpload", "step upload/init crashId=${entity.crashId} fileSize=${file.length()}")

            val init =
                initUpload(
                    token = token,
                    crashClipId = entity.crashId,
                    totalSize = file.length(),
                    requestedChunkSize = 256 * 1024,
                )

            Log.d("CrashClipUpload", "step upload/chunks crashId=${entity.crashId} chunks=${init.total_chunks} chunkSize=${init.chunk_size}")

            uploadChunks(
                token = token,
                uploadSessionId = init.session_id,
                file = file,
                chunkSize = init.chunk_size,
                totalChunks = init.total_chunks,
            )

            Log.d("CrashClipUpload", "step done crashId=${entity.crashId}")

            completeUpload(
                token = token,
                crashClipId = entity.crashId,
            )

            true
        }

    private fun postJson(
        path: String,
        token: String,
        payload: Any,
    ) {

        Log.d("CrashClipUpload", "POST $path started")

        val bodyString =
            when (payload) {
                is VideoSessionStartPayload ->
                    json.encodeToString(payload)

                is CrashLogPayload ->
                    json.encodeToString(payload)

                is CrashClipMetadataPayload ->
                    json.encodeToString(payload)

                else ->
                    error("Unsupported payload: ${payload::class.java.simpleName}")
            }

        val request =
            Request.Builder()
                .url("${activeBaseUrl.trimEnd('/')}$path")
                .post(bodyString.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()

            Log.d("CrashClipUpload", "POST $path response=${response.code} body=$responseBody")

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "POST $path failed: HTTP ${response.code}: $responseBody"
                )
            }
        }
    }

    private fun initUpload(
        token: String,
        crashClipId: String,
        totalSize: Long,
        requestedChunkSize: Int,
    ): UploadInitResponse {

        Log.d("CrashClipUpload", "upload/init started crashClipId=$crashClipId totalSize=$totalSize")

        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("crash_clip_id", crashClipId)
                .addFormDataPart("total_size", totalSize.toString())
                .addFormDataPart("chunk_size", requestedChunkSize.toString())
                .build()

        val request =
            Request.Builder()
                .url("${activeBaseUrl.trimEnd('/')}/crash-clips/upload/init")
                .post(body)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("Connection", "close")
                .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()

            Log.d("CrashClipUpload", "upload/init response=${response.code} body=$responseBody")

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "upload/init failed: HTTP ${response.code}: $responseBody"
                )
            }

            return json.decodeFromString(responseBody)
        }
    }

    private fun uploadChunks(
        token: String,
        uploadSessionId: String,
        file: File,
        chunkSize: Int,
        totalChunks: Int,
    ) {

        file.inputStream().use { input ->
            val buffer =
                ByteArray(chunkSize)

            var chunkIndex = 0

            while (chunkIndex < totalChunks) {
                val read =
                    input.read(buffer)

                if (read <= 0) {
                    break
                }

                val chunkFile =
                    File.createTempFile(
                        "dashcam_chunk_${chunkIndex}_",
                        ".part",
                        appContext.cacheDir,
                    )

                try {
                    chunkFile.writeBytes(buffer.copyOf(read))

                    uploadChunk(
                        token = token,
                        uploadSessionId = uploadSessionId,
                        chunkIndex = chunkIndex,
                        chunkFile = chunkFile,
                    )
                } finally {
                    chunkFile.delete()
                }

                chunkIndex += 1
            }
        }
    }

    private fun uploadChunk(
        token: String,
        uploadSessionId: String,
        chunkIndex: Int,
        chunkFile: File,
    ) {
        Log.d(
            "CrashClipUpload",
            "upload/chunk started session=$uploadSessionId index=$chunkIndex size=${chunkFile.length()}"
        )

        val fileBody =
            chunkFile.asRequestBody(
                "application/octet-stream".toMediaType()
            )

        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_session_id", uploadSessionId)
                .addFormDataPart("chunk_index", chunkIndex.toString())
                .addFormDataPart(
                    "file",
                    "chunk-$chunkIndex.part",
                    fileBody
                )
                .build()

        val request =
            Request.Builder()
                .url("${activeBaseUrl.trimEnd('/')}/crash-clips/upload/chunk")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("Connection", "close")
                .post(body)
                .build()

        client.newCall(request).execute().use { response ->
            val responseBody =
                response.body?.string().orEmpty()

            Log.d(
                "CrashClipUpload",
                "upload/chunk response=${response.code} index=$chunkIndex body=$responseBody"
            )

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "upload/chunk $chunkIndex failed: HTTP ${response.code}: $responseBody"
                )
            }
        }
    }

    private fun completeUpload(
        token: String,
        crashClipId: String,
    ) {

        Log.d("CrashClipUpload", "upload/complete started crashClipId=$crashClipId")
        val body =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("crash_clip_id", crashClipId)
                .build()

        val request =
            Request.Builder()
                .url("${activeBaseUrl.trimEnd('/')}/crash-clips/upload/complete")
                .post(body)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("Connection", "close")
                .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()

            Log.d("CrashClipUpload", "upload/complete response=${response.code} body=$responseBody")

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "upload/complete failed: HTTP ${response.code}: $responseBody"
                )
            }
        }
    }
}