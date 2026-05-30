package com.alex.android_telemetry.ui.savefish

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

import com.alex.android_telemetry.R
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource


import androidx.compose.ui.graphics.drawscope.scale

import androidx.compose.ui.text.style.TextAlign


import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.runtime.saveable.rememberSaveable

import com.alex.android_telemetry.telemetry.glass.GlassGameApi
import com.alex.android_telemetry.telemetry.glass.GlassGameBatchDto
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import androidx.compose.ui.res.stringResource


private data class Droplet(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val radius: Float,
)

@Composable
fun SaveFishGameScreen(
    deviceId: String,
    driverId: String?,
    sessionId: String,
    glassGameApi: GlassGameApi,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    val gameId = rememberSaveable { UUID.randomUUID().toString() }
    val windowOpenedAtMs = rememberSaveable { System.currentTimeMillis() }
    var gameStartedAtMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var gameEndedAtMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var didUploadGlassGame by rememberSaveable { mutableStateOf(false) }

    var energy by remember { mutableStateOf(0f) }
    var spillSeverity by remember { mutableStateOf(0f) }

    var phase by remember { mutableStateOf(0f) }
    var waterLevel01 by remember { mutableStateOf(0.90f) }
    var spilledTotal01 by remember { mutableStateOf(0f) }
    var refilledTotal01 by remember { mutableStateOf(0f) }
    var isGameOver by remember { mutableStateOf(false) }
    var droplets by remember { mutableStateOf<List<Droplet>>(emptyList()) }

    val startFill01 = 0.90f
    val netSpilled01 = max(0f, spilledTotal01 - refilledTotal01)
    val progress01 = min(1f, netSpilled01 / startFill01)
    val spilledPercent = (progress01 * 100f).coerceIn(0f, 100f)
    val refillBonusPercent = refilledTotal01 / startFill01 * 100f

    fun isoUtc(ms: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(ms))
    }

    fun uploadGlassGameIfNeeded(aborted: Boolean) {
        if (didUploadGlassGame) return
        didUploadGlassGame = true

        val now = System.currentTimeMillis()

        if (gameEndedAtMs == null) {
            gameEndedAtMs = now
        }

        if (gameStartedAtMs == null) {
            gameStartedAtMs = windowOpenedAtMs
        }

        val started = gameStartedAtMs ?: windowOpenedAtMs
        val ended = gameEndedAtMs ?: now

        val batch = GlassGameBatchDto(
            deviceId = deviceId,
            driverId = driverId,
            sessionId = sessionId,
            gameId = gameId,
            windowOpenedAt = isoUtc(windowOpenedAtMs),
            gameStartedAt = isoUtc(started),
            gameEndedAt = isoUtc(ended),
            windowClosedAt = isoUtc(now),
            maxSpillLevel = progress01.toDouble(),
            totalRefilled01 = refilledTotal01.toDouble(),
            gameDurationSec = (ended.toDouble() - started.toDouble()) / 1000.0,
            windowDurationSec = (now.toDouble() - windowOpenedAtMs.toDouble()) / 1000.0,
            backgroundEvents = emptyMap(),
            analytics = mapOf(
                "background_count" to 0.0,
                "active_play_s" to ((ended.toDouble() - started.toDouble()) / 1000.0),
            ),
            aborted = aborted,
        )

        scope.launch {
            runCatching {
                glassGameApi.upload(batch)
            }
        }
    }



    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val linearAcceleration = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        var lastAccMag = 0f
        var lastTs = 0L
        var gravityX = 0f
        var gravityY = 0f
        var gravityZ = 0f
        var rotMag = 0f
        var energyLp = 0f
        var severityLp = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = SystemClock.elapsedRealtimeNanos()
                val dt = if (lastTs > 0L) {
                    max(0.001f, (now - lastTs) / 1_000_000_000f)
                } else {
                    0.02f
                }
                lastTs = now

                when (event.sensor.type) {
                    Sensor.TYPE_GYROSCOPE -> {
                        val rx = event.values[0]
                        val ry = event.values[1]
                        val rz = event.values[2]
                        rotMag = sqrt(rx * rx + ry * ry + rz * rz)
                    }

                    Sensor.TYPE_LINEAR_ACCELERATION -> {
                        val ax = event.values[0] / 9.80665f
                        val ay = event.values[1] / 9.80665f
                        val az = event.values[2] / 9.80665f
                        val accMag = sqrt(ax * ax + ay * ay + az * az)
                        val jerk = abs(accMag - lastAccMag) / dt
                        lastAccMag = accMag

                        val severityRaw = accMag * 1.10f + jerk * 0.08f + rotMag * 0.15f
                        severityLp = severityLp * 0.85f + severityRaw * 0.15f

                        val input = 0.9f * accMag + 0.15f * rotMag
                        energyLp = min(2f, energyLp * 0.92f + input * 0.35f)

                        spillSeverity = severityLp
                        energy = energyLp
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        if (linearAcceleration != null) return

                        gravityX = gravityX * 0.92f + event.values[0] * 0.08f
                        gravityY = gravityY * 0.92f + event.values[1] * 0.08f
                        gravityZ = gravityZ * 0.92f + event.values[2] * 0.08f

                        val ax = (event.values[0] - gravityX) / 9.80665f
                        val ay = (event.values[1] - gravityY) / 9.80665f
                        val az = (event.values[2] - gravityZ) / 9.80665f
                        val accMag = sqrt(ax * ax + ay * ay + az * az)
                        val jerk = abs(accMag - lastAccMag) / dt
                        lastAccMag = accMag

                        val severityRaw = accMag * 1.10f + jerk * 0.08f + rotMag * 0.15f
                        severityLp = severityLp * 0.85f + severityRaw * 0.15f

                        val input = 0.9f * accMag + 0.15f * rotMag
                        energyLp = min(2f, energyLp * 0.92f + input * 0.35f)

                        spillSeverity = severityLp
                        energy = energyLp
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(
            listener,
            linearAcceleration ?: accelerometer,
            SensorManager.SENSOR_DELAY_GAME,
        )

        gyroscope?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            uploadGlassGameIfNeeded(
                aborted = !(isGameOver || progress01 >= 0.999f)
            )
        }
    }

    LaunchedEffect(Unit) {
        var lastFrame = 0L
        var gameStartedAt = 0L
        var lastSpillAt = 0L

        while (true) {
            withFrameNanos { frame ->
                if (lastFrame == 0L) {
                    lastFrame = frame
                    gameStartedAt = frame
                    if (gameStartedAtMs == null) {
                        gameStartedAtMs = System.currentTimeMillis()
                    }
                    return@withFrameNanos
                }

                val dt = min(1f / 20f, (frame - lastFrame) / 1_000_000_000f)
                lastFrame = frame

                if (isGameOver) return@withFrameNanos

                phase += dt * 2.6f

                droplets = droplets.mapNotNull { d ->
                    val life = d.life - dt
                    if (life <= 0f) {
                        null
                    } else {
                        val vy = d.vy + 1800f * dt
                        d.copy(
                            x = d.x + d.vx * dt,
                            y = d.y + vy * dt,
                            vy = vy,
                            life = life,
                        )
                    }
                }

                val allowRefill = progress01 < 0.99f
                if (allowRefill) {
                    val refillRate01PerSec = startFill01 * 0.00002f
                    val wantAdd = refillRate01PerSec * dt
                    val add = min(wantAdd, max(0f, spilledTotal01 - refilledTotal01))
                    refilledTotal01 += add
                }

                val gracePassed = (frame - gameStartedAt) / 1_000_000_000f > 1.5f
                val severityThreshold = 0.05f
                val severityGain01PerSec = 0.10f
                val spillCooldownSec = 0.10f
                val timeSinceLastSpill = (frame - lastSpillAt) / 1_000_000_000f

                val sevExcess = max(0f, spillSeverity - severityThreshold)

                if (gracePassed && sevExcess > 0f && timeSinceLastSpill > spillCooldownSec) {
                    val spillAmount01 = min(0.030f, sevExcess * severityGain01PerSec * dt)

                    if (spillAmount01 > 0f) {
                        spilledTotal01 += spillAmount01
                        lastSpillAt = frame

                        if (spilledTotal01 - refilledTotal01 > startFill01) {
                            spilledTotal01 = startFill01 + refilledTotal01
                        }

                        val splashPower = min(30f, sevExcess * 14f)
                        val count = max(4, min(20, (splashPower * 0.6f).toInt()))

                        droplets = droplets + List(count) {
                            Droplet(
                                x = Random.nextFloat(),
                                y = Random.nextFloat(),
                                vx = Random.nextFloat() * 640f - 320f,
                                vy = -(Random.nextFloat() * 520f + 420f),
                                life = Random.nextFloat() * 0.30f + 0.30f,
                                radius = Random.nextFloat() * 1.8f + 1.5f,
                            )
                        }
                    }
                }

                waterLevel01 = waterLevel01.coerceIn(0.05f, 1.06f)

                if (progress01 >= 0.9995f) {
                    if (!isGameOver) {
                        gameEndedAtMs = System.currentTimeMillis()
                        isGameOver = true
                        uploadGlassGameIfNeeded(aborted = false)
                    }
                }
            }
        }
    }



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Button(
            onClick = {
                uploadGlassGameIfNeeded(
                    aborted = !(isGameOver || progress01 >= 0.999f)
                )
                onBack()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 14.dp, start = 12.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0073F2).copy(alpha = 0.90f),
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.fish_game_close),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(54.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val gameOverVisual = isGameOver || progress01 >= 0.999f

                Text(
                    text = if (gameOverVisual) stringResource(R.string.fish_game_finished) else stringResource(R.string.fish_game_title),
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = if (gameOverVisual) Color.Red.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.9f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(
                        R.string.fish_game_spilled_total,
                        spilledPercent,
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.90f),
                )

                Text(
                    text = stringResource(
                        R.string.fish_game_bonus,
                        refillBonusPercent,
                    ),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF34C759).copy(alpha = 0.90f),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            val gameOverVisual = isGameOver || progress01 >= 0.999f

            val fishRes = when {
                gameOverVisual -> R.drawable.fish_high
                progress01 < 0.33f -> R.drawable.fish_low
                progress01 < 0.66f -> R.drawable.fish_mid
                else -> R.drawable.fish_high
            }

            val fishBitmap = ImageBitmap.imageResource(id = fishRes)

            Box(
                modifier = Modifier
                    .width(270.dp)
                    .height(480.dp),
            ) {
                WaterGlassCanvas(
                    modifier = Modifier.fillMaxSize(),
                    fishBitmap = fishBitmap,
                    phase = phase,
                    energy = energy,
                    progress01 = progress01,
                    droplets = droplets,
                    isGameOver = gameOverVisual,
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.fish_game_description),

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.82f),
            )
            Spacer(modifier = Modifier.height(28.dp))


        }
    }
}

@Composable
private fun WaterGlassCanvas(
    modifier: Modifier,
    fishBitmap: ImageBitmap,
    phase: Float,
    energy: Float,
    progress01: Float,
    droplets: List<Droplet>,
    isGameOver: Boolean,
) {
    Canvas(modifier = modifier) {
        val glassInset = 18f
        val glassWidthScale = 1.20f

        val baseGlass = Rect(
            left = glassInset,
            top = glassInset,
            right = size.width - glassInset,
            bottom = size.height - glassInset,
        )

        val extraWidth = baseGlass.width * (glassWidthScale - 1f)
        val glass = Rect(
            left = baseGlass.left - extraWidth / 2f,
            top = baseGlass.top,
            right = baseGlass.right + extraWidth / 2f,
            bottom = baseGlass.bottom,
        )

        fun glassPath(r: Rect): Path {
            val topInset = 10f
            val bottomInset = 34f
            val corner = 26f
            val rimDrop = 10f

            val topLeft = Offset(r.left + topInset, r.top + rimDrop)
            val topRight = Offset(r.right - topInset, r.top + rimDrop)
            val bottomRight = Offset(r.right - bottomInset, r.bottom)
            val bottomLeft = Offset(r.left + bottomInset, r.bottom)

            return Path().apply {
                moveTo(topLeft.x + corner, topLeft.y)
                lineTo(topRight.x - corner, topRight.y)
                quadraticTo(topRight.x, topRight.y, topRight.x, topRight.y + corner)
                lineTo(bottomRight.x, bottomRight.y - corner)
                quadraticTo(bottomRight.x, bottomRight.y, bottomRight.x - corner, bottomRight.y)
                lineTo(bottomLeft.x + corner, bottomLeft.y)
                quadraticTo(bottomLeft.x, bottomLeft.y, bottomLeft.x, bottomLeft.y - corner)
                lineTo(topLeft.x, topLeft.y + corner)
                quadraticTo(topLeft.x, topLeft.y, topLeft.x + corner, topLeft.y)
                close()
            }
        }

        fun waterPath(r: Rect): Path {
            val spillLipPx = 14f
            val headroomPx = 32f
            val baseLevel = min(r.top + spillLipPx + headroomPx, r.bottom - 8f)
            val amp = min(24f, max(0f, energy) * 55f)
            val amp2 = amp * 0.25f
            val samples = 64

            return Path().apply {
                for (i in 0..samples) {
                    val x01 = i.toFloat() / samples.toFloat()
                    val x = r.left + x01 * r.width
                    val w1 = amp * sin((2f * PI.toFloat()) * (1.6f * x01 + phase))
                    val w2 = amp2 * sin((2f * PI.toFloat()) * (3.2f * x01 + phase * 1.35f))
                    val y = baseLevel + w1 + w2

                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }

                lineTo(r.right, r.bottom)
                lineTo(r.left, r.bottom)
                close()
            }
        }

        val outerPath = glassPath(glass)
        val innerPath = glassPath(
            Rect(
                left = glass.left + 6f,
                top = glass.top + 6f,
                right = glass.right - 6f,
                bottom = glass.bottom - 6f,
            )
        )

        val initialWaterHeight = glass.height * 0.90f
        val markerY = glass.bottom - progress01 * initialWaterHeight
        val effectiveWaterRect = Rect(
            left = glass.left,
            top = glass.top,
            right = glass.right,
            bottom = max(glass.top + 1f, markerY),
        )

        val water = waterPath(effectiveWaterRect)

        clipPath(outerPath) {
            drawPath(
                path = water,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF40B3FF).copy(alpha = 0.85f),
                        Color(0xFF0059F2).copy(alpha = 0.82f),
                        Color(0xFF0033BF).copy(alpha = 0.85f),
                    ),
                ),
            )

            val glintX = effectiveWaterRect.left +
                    effectiveWaterRect.width * ((sin(phase * 0.60f) + 1f) * 0.5f)

            rotate(
                degrees = -18f,
                pivot = Offset(glintX, effectiveWaterRect.top + effectiveWaterRect.height * 0.35f),
            ) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0f),
                        ),
                    ),
                    topLeft = Offset(glintX - effectiveWaterRect.width * 0.14f, effectiveWaterRect.top),
                    size = Size(effectiveWaterRect.width * 0.28f, effectiveWaterRect.height * 1.35f),
                    alpha = 0.75f,
                )
            }

            drawRect(
                color = Color(0xFF053F1A).copy(alpha = 0.85f),
                topLeft = Offset(glass.left, markerY),
                size = Size(glass.width, max(0f, glass.bottom - markerY)),
            )

            drawLine(
                color = Color(0xFF73471F).copy(alpha = 0.95f),
                start = Offset(glass.left, markerY),
                end = Offset(glass.right, markerY),
                strokeWidth = 10f,
            )

            val fishSize = glass.width * 0.30f
            val fishPhase = phase * 0.8f
            val fishProgress = (cos(fishPhase) + 1f) * 0.5f
            val fishMovingRight = sin(fishPhase) < 0f

            val waterSurfaceY = effectiveWaterRect.top + 46f
            val blueBottomY = markerY - fishSize * 0.62f
            val blueCenterY = waterSurfaceY + max(0f, blueBottomY - waterSurfaceY) * 0.50f

            val fishX = if (isGameOver) {
                glass.left + glass.width * 0.50f
            } else {
                glass.left + 86f + fishProgress * (glass.width - 172f)
            }

            val fishY = if (isGameOver) {
                glass.bottom - fishSize * 0.55f
            } else {
                blueCenterY
            }

            val fishScaleX = when {
                isGameOver -> 1f
                fishMovingRight -> -1f
                else -> 1f
            }

            rotate(
                degrees = if (isGameOver) 180f else 0f,
                pivot = Offset(fishX, fishY),
            ) {
                scale(
                    scaleX = fishScaleX,
                    scaleY = 1f,
                    pivot = Offset(fishX, fishY),
                ) {
                    drawImage(
                        image = fishBitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            (fishX - fishSize / 2f).toInt(),
                            (fishY - fishSize / 2f).toInt(),
                        ),
                        dstSize = androidx.compose.ui.unit.IntSize(
                            fishSize.toInt(),
                            fishSize.toInt(),
                        ),
                    )
                }
            }
        }

        drawPath(
            path = innerPath,
            color = Color.White.copy(alpha = 0.35f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
        )
        droplets.forEach { d ->

            val splashX =
                glass.left + d.x * glass.width

            val splashY =
                glass.top - 42f +
                        d.y * 40f +
                        (0.6f - d.life) * 760f

            drawCircle(
                color = Color.White.copy(
                    alpha = min(1f, d.life / 0.6f) * 0.65f
                ),
                radius = d.radius * 1.25f,
                center = Offset(
                    splashX,
                    splashY,
                ),
            )

            drawCircle(
                color = Color(0xFF6EC6FF).copy(
                    alpha = min(1f, d.life / 0.6f) * 0.35f
                ),
                radius = d.radius * 0.75f,
                center = Offset(
                    splashX,
                    splashY,
                ),
            )
        }

        drawPath(
            path = outerPath,
            color = Color.White.copy(alpha = 0.75f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
        )
}
}
