package com.clouddetect.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.clouddetect.app.data.CLOUD_DATABASE
import com.clouddetect.app.ml.ClassificationResult
import com.clouddetect.app.ml.CloudClassifier
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private const val CLASSIFICATION_INTERVAL_MS = 800L
private const val TAG = "CameraScreen"

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("L'accès à la caméra est nécessaire pour reconnaître les nuages.")
        }
        return
    }

    val classifier = remember {
        runCatching { CloudClassifier(context) }
            .onFailure { Log.e(TAG, "Échec du chargement du modèle TFLite", it) }
            .getOrNull()
    }
    var result by remember { mutableStateOf<ClassificationResult?>(null) }
    var lastAnalysisTime by remember { mutableStateOf(0L) }
    var isPaused by remember { mutableStateOf(false) }

    if (classifier == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Impossible de charger le modèle de reconnaissance.")
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // Format YUV_420_888 par défaut : c'est le seul format de sortie garanti
                    // pris en charge par tous les appareils pour ImageAnalysis (contrairement
                    // à RGBA_8888, qui a fait planter l'app sur certains appareils/caméras).
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        try {
                            if (!isPaused) {
                                val now = System.currentTimeMillis()
                                if (now - lastAnalysisTime >= CLASSIFICATION_INTERVAL_MS) {
                                    lastAnalysisTime = now
                                    val bitmap = imageProxy.toRotatedBitmap()
                                    if (bitmap != null) {
                                        result = classifier.classify(bitmap)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Erreur lors de l'analyse d'une frame", e)
                        } finally {
                            imageProxy.close()
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    } catch (_: Exception) {
                        // La liaison peut échouer si l'écran change pendant l'initialisation ; sans conséquence.
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        Button(
            onClick = { isPaused = !isPaused },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Icon(
                if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isPaused) "Reprendre" else "Stopper")
        }

        val current = result
        val info = current?.let { r -> CLOUD_DATABASE.find { it.code == r.code } }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (current == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Text("Pointe l'appareil vers le ciel pour identifier le nuage…")
                    }
                }
            } else if (info != null) {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = info.images.first()),
                                contentDescription = "Photo de référence : ${info.nameFr}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Text(
                                "${info.nameFr} (${info.code})",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        LinearProgressIndicator(
                            progress = { current.confidence },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                        Text("Confiance : ${(current.confidence * 100).toInt()}%")
                        Text(
                            info.meteoAssociee,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Convertit une frame CameraX au format YUV_420_888 (le seul garanti sur tous les appareils)
 * en Bitmap orienté comme l'aperçu, via un passage NV21 -> JPEG -> Bitmap : plus lent qu'une
 * copie directe de buffer, mais nettement plus fiable d'un appareil à l'autre.
 */
private fun ImageProxy.toRotatedBitmap(): Bitmap? {
    if (format != ImageFormat.YUV_420_888 || planes.size < 3) return null

    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val vSize = vBuffer.remaining()
    val uSize = uBuffer.remaining()

    val nv21 = ByteArray(ySize + vSize + uSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
    val bytes = out.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    val rotation = imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
