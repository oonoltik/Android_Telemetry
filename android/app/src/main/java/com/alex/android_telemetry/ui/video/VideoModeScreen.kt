package com.alex.android_telemetry.ui.video

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Locale

@Composable
fun VideoModeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isRecording by remember {
        mutableStateOf(false)
    }

    var recordingSeconds by remember {
        mutableStateOf(0)
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            kotlinx.coroutines.delay(1000)
            recordingSeconds++
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Назад", color = Color.Black)
            }

            Text(
                text = formatRecordingTime(recordingSeconds),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
        ) {

            AndroidView(
                factory = {
                    PreviewView(it).apply {

                        layoutParams = android.view.ViewGroup.LayoutParams(
                            MATCH_PARENT,
                            MATCH_PARENT
                        )

                        val cameraProviderFuture =
                            ProcessCameraProvider.getInstance(it)

                        cameraProviderFuture.addListener({

                            val cameraProvider =
                                cameraProviderFuture.get()

                            val preview =
                                androidx.camera.core.Preview.Builder()
                                    .build()

                            preview.setSurfaceProvider(this.surfaceProvider)

                            val cameraSelector =
                                CameraSelector.DEFAULT_BACK_CAMERA

                            try {

                                cameraProvider.unbindAll()

                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview
                                )

                            } catch (_: Exception) {
                            }

                        }, ContextCompat.getMainExecutor(it))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isRecording) {

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Red)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "REC",
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {

                isRecording = !isRecording

                if (!isRecording) {
                    recordingSeconds = 0
                }
            },
            modifier = Modifier
                .size(82.dp)
                .align(Alignment.CenterHorizontally),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) {
                    Color.Red
                } else {
                    Color.White
                }
            )
        ) {

        }
    }
}

private fun formatRecordingTime(seconds: Int): String {

    val mins = seconds / 60
    val secs = seconds % 60

    return String.format(
        Locale.US,
        "%02d:%02d",
        mins,
        secs
    )
}