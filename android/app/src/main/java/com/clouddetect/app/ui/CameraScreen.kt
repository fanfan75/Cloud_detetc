package com.clouddetect.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.clouddetect.app.data.CLOUD_DATABASE
import com.clouddetect.app.data.CloudInfo
import com.clouddetect.app.data.analyserNuageEtMeteo
import com.clouddetect.app.ml.ClassificationResult
import com.clouddetect.app.ml.CloudClassifier
import com.clouddetect.app.weather.CurrentWeather
import com.clouddetect.app.weather.awaitLocation
import com.clouddetect.app.weather.fetchCurrentWeather
import com.clouddetect.app.weather.formatCoordinates
import com.clouddetect.app.weather.reverseGeocode
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

private const val CLASSIFICATION_INTERVAL_MS = 800L
private const val TAG = "CameraScreen"

/**
 * Fraction de la plus petite dimension de l'image occupée par le repère de cadrage, et donc
 * par la zone effectivement envoyée au classifieur. Une valeur < 1 exclut les bords de l'image
 * (souvent du sol, des bâtiments ou des arbres) qui n'apportent aucun signal utile sur le
 * nuage et peuvent perturber la classification.
 */
private const val FRAMING_FRACTION = 0.8f

/**
 * Dès qu'une classification atteint cette confiance, l'analyse s'arrête automatiquement
 * (comme un appui sur « Stopper ») pour que le résultat reste affiché sans changer toutes
 * les 800ms tant que l'utilisateur ne bouge pas l'appareil.
 */
private const val AUTO_LOCK_CONFIDENCE = 0.70f

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(isGranted(context, Manifest.permission.CAMERA))
    }
    var hasLocationPermission by remember {
        mutableStateOf(isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val requestedPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasCameraPermission = granted[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasLocationPermission =
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] ?: hasLocationPermission
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasLocationPermission) {
            permissionLauncher.launch(requestedPermissions)
        }
    }

    if (!hasCameraPermission) {
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
    var results by remember { mutableStateOf<List<ClassificationResult>>(emptyList()) }
    var lastFrame by remember { mutableStateOf<Bitmap?>(null) }
    var lastAnalysisTime by remember { mutableStateOf(0L) }
    var isPaused by remember { mutableStateOf(false) }
    var isAutoLocked by remember { mutableStateOf(false) }
    var isDetailExpanded by remember { mutableStateOf(false) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    var weather by remember { mutableStateOf<CurrentWeather?>(null) }
    var weatherStatus by remember { mutableStateOf("Météo locale non chargée") }
    var weatherRefreshKey by remember { mutableStateOf(0) }
    var locationLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hasLocationPermission, weatherRefreshKey) {
        if (!hasLocationPermission) {
            weatherStatus = "Localisation refusée : l'analyse météo est désactivée."
            return@LaunchedEffect
        }
        weatherStatus = "Recherche de ta position…"
        val position = awaitLocation(context)
        if (position == null) {
            weatherStatus = "Position indisponible. Vérifie que la localisation est activée."
            return@LaunchedEffect
        }
        weatherStatus = "Récupération de la météo locale…"
        val fetched = fetchCurrentWeather(position.first, position.second)
        if (fetched == null) {
            weatherStatus = "Météo indisponible. Vérifie ta connexion internet."
        } else {
            weather = fetched
            weatherStatus = ""
        }
        locationLabel = reverseGeocode(context, position.first, position.second)
    }

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
                previewViewRef = previewView
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
                                        // On classifie la zone centrale cadrée par le repère
                                        // affiché à l'écran, pas l'image entière : ça exclut le
                                        // sol/les bâtiments qui polluent le signal si l'appareil
                                        // n'est pas parfaitement à la verticale.
                                        val classified = classifier.classify(bitmap.centerCrop(FRAMING_FRACTION))
                                        results = classified
                                        lastFrame = bitmap

                                        // Confiance suffisante : on fige le résultat au lieu de
                                        // continuer à le remplacer toutes les 800ms.
                                        val topConfidence = classified.firstOrNull()?.confidence ?: 0f
                                        if (topConfidence >= AUTO_LOCK_CONFIDENCE) {
                                            isAutoLocked = true
                                            isPaused = true
                                            isDetailExpanded = false
                                        }
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

        FramingOverlay(Modifier.fillMaxSize())

        val top = results.firstOrNull()
        val info = top?.let { r -> CLOUD_DATABASE.find { it.code == r.code } }
        val analyse = info?.let { analyserNuageEtMeteo(it, weather) } ?: emptyList()

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val frameToCapture = lastFrame
            val infoToCapture = info
            if (isPaused && frameToCapture != null && infoToCapture != null) {
                val resultsToCapture = results
                val weatherToCapture = weather
                val locationLabelToCapture = locationLabel
                Button(onClick = {
                    // On capture une frame fraîche de l'aperçu au moment précis de l'appui,
                    // plutôt que la frame figée au moment du dernier passage du classifieur
                    // (lastFrame) : entre l'auto-lock et l'appui sur « Capturer », l'appareil
                    // a pu bouger (ex. redescendre vers le sol), et lastFrame ne reflète plus
                    // ce que l'utilisateur vise réellement.
                    val liveFrame = previewViewRef?.bitmap ?: frameToCapture
                    val saved = captureFiche(
                        context = context,
                        frame = liveFrame,
                        info = infoToCapture,
                        results = resultsToCapture,
                        weather = weatherToCapture,
                        locationLabel = locationLabelToCapture,
                        analyse = analyse,
                    )
                    Toast.makeText(
                        context,
                        if (saved) "Capture enregistrée dans la galerie"
                        else "Échec de l'enregistrement de la capture",
                        Toast.LENGTH_SHORT,
                    ).show()
                }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Capturer")
                }
            }

            Button(onClick = {
                isPaused = !isPaused
                if (!isPaused) {
                    isAutoLocked = false
                } else {
                    isDetailExpanded = false
                }
            }) {
                Icon(
                    if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isPaused) "Reprendre" else "Stopper")
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (top == null || info == null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                        Text("Pointe l'appareil vers le ciel pour identifier le nuage…")
                    }
                }
            } else {
                // En pause, la fiche démarre repliée (juste le résultat principal) pour ne pas
                // masquer l'aperçu du ciel : un appui dessus la déplie pour lire le détail, la
                // météo et l'analyse.
                val expanded = isPaused && isDetailExpanded
                Card(
                    modifier = if (isPaused) {
                        Modifier.clickable { isDetailExpanded = !isDetailExpanded }
                    } else {
                        Modifier
                    },
                ) {
                    Column(
                        Modifier
                            .heightIn(max = if (expanded) 420.dp else 220.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        ResultHeader(info, top)

                        LinearProgressIndicator(
                            progress = { top.confidence },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                        Text("Confiance : ${percent(top.confidence)} %")
                        if (isAutoLocked) {
                            Text(
                                "Résultat figé automatiquement (confiance ≥ ${percent(AUTO_LOCK_CONFIDENCE)} %). " +
                                    "Appuie sur « Reprendre » pour relancer la détection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        val alternatives = results.drop(1)
                        if (alternatives.isNotEmpty()) {
                            Text(
                                "Autres hypothèses : " + alternatives.joinToString(", ") { alt ->
                                    val nom = CLOUD_DATABASE.find { it.code == alt.code }?.nameFr ?: alt.code
                                    "$nom ${percent(alt.confidence)} %"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        Text(
                            info.meteoAssociee,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )

                        if (!isPaused) {
                            Text(
                                "Appuie sur « Stopper » pour figer le résultat, lire le détail " +
                                    "et enregistrer une capture.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else if (!expanded) {
                            Text(
                                "Touche la fiche pour voir le détail, la météo et l'analyse.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            DetailSection(info)
                            WeatherSection(
                                weather = weather,
                                weatherStatus = weatherStatus,
                                locationLabel = locationLabel,
                                onRefresh = { weatherRefreshKey++ },
                            )
                            AnalysisSection(analyse)
                        }
                    }
                }
            }
        }
    }
}

/** Repère visuel montrant la zone effectivement analysée par le classifieur. */
@Composable
private fun FramingOverlay(modifier: Modifier = Modifier) {
    Box(modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxSize = size.minDimension * FRAMING_FRACTION
            val left = (size.width - boxSize) / 2f
            val top = (size.height - boxSize) / 2f
            drawRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(left, top),
                size = Size(boxSize, boxSize),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
        Text(
            "Cadre le nuage dans le repère",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ResultHeader(info: CloudInfo, top: ClassificationResult) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val illustration = info.images.firstOrNull()
        if (illustration != null) {
            Image(
                painter = painterResource(id = illustration),
                contentDescription = "Photo de référence : ${info.nameFr}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                "${info.nameFr} (${info.code})",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(info.famille, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DetailSection(info: CloudInfo) {
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Description", style = MaterialTheme.typography.labelLarge)
    Text(info.description, style = MaterialTheme.typography.bodySmall)

    Text(
        "Caractéristiques",
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text("Altitude : ${info.altitude}", style = MaterialTheme.typography.bodySmall)
    Text("Forme : ${info.forme}", style = MaterialTheme.typography.bodySmall)
    Text("Couleur : ${info.couleur}", style = MaterialTheme.typography.bodySmall)
    Text("Composition : ${info.composition}", style = MaterialTheme.typography.bodySmall)
    Text("Précipitations : ${info.precipitations}", style = MaterialTheme.typography.bodySmall)

    if (info.especes.isNotEmpty()) {
        Text(
            "Espèces et variétés",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        info.especes.forEach { espece ->
            Text(
                "${espece.nom} — ${espece.description}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun WeatherSection(
    weather: CurrentWeather?,
    weatherStatus: String,
    locationLabel: String?,
    onRefresh: () -> Unit,
) {
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Météo locale", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Actualiser")
        }
    }

    if (weather == null) {
        Text(
            weatherStatus.ifBlank { "Météo locale non chargée" },
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Text(weather.description, style = MaterialTheme.typography.bodyMedium)
        Text(weatherLine(weather), style = MaterialTheme.typography.bodySmall)
        Text(
            "Position : " + locationText(weather.latitude, weather.longitude, locationLabel),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AnalysisSection(analyse: List<String>) {
    if (analyse.isEmpty()) return
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text("Analyse nuage + météo", style = MaterialTheme.typography.labelLarge)
    analyse.forEach { point ->
        Text(
            "• $point",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun isGranted(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun percent(value: Float): Int = (value * 100).roundToInt()

private fun weatherLine(weather: CurrentWeather): String = buildString {
    if (!weather.temperatureC.isNaN()) append("${weather.temperatureC.roundToInt()} °C")
    if (weather.humidityPct >= 0) append(" · humidité ${weather.humidityPct} %")
    if (weather.cloudCoverPct >= 0) append(" · couverture ${weather.cloudCoverPct} %")
    if (!weather.pressureHpa.isNaN()) append(" · ${weather.pressureHpa.roundToInt()} hPa")
    if (!weather.windSpeedKmh.isNaN()) append(" · vent ${weather.windSpeedKmh.roundToInt()} km/h")
}

/** Coordonnées GPS lisibles, avec le nom de lieu entre parenthèses si le géocodage a réussi. */
private fun locationText(latitude: Double, longitude: Double, locationLabel: String?): String {
    val coords = formatCoordinates(latitude, longitude)
    return if (locationLabel.isNullOrBlank()) coords else "$coords ($locationLabel)"
}

/** Compose la fiche (photo + texte) et l'enregistre dans la galerie. */
private fun captureFiche(
    context: android.content.Context,
    frame: Bitmap,
    info: CloudInfo,
    results: List<ClassificationResult>,
    weather: CurrentWeather?,
    locationLabel: String?,
    analyse: List<String>,
): Boolean {
    val top = results.firstOrNull() ?: return false
    val horodatage = SimpleDateFormat("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE).format(Date())

    val lines = buildList {
        add("Confiance : ${percent(top.confidence)} %")
        val alternatives = results.drop(1)
        if (alternatives.isNotEmpty()) {
            add("Autres hypothèses : " + alternatives.joinToString(", ") { alt ->
                val nom = CLOUD_DATABASE.find { it.code == alt.code }?.nameFr ?: alt.code
                "$nom ${percent(alt.confidence)} %"
            })
        }
        add("Altitude : ${info.altitude}")
        add("Forme : ${info.forme}")
        add("Couleur : ${info.couleur}")
        add("Composition : ${info.composition}")
        add("Précipitations : ${info.precipitations}")
        add(info.description)
        add("Météo associée : ${info.meteoAssociee}")
        if (weather != null) {
            add("Météo locale : ${weather.description} — ${weatherLine(weather)}")
            add("Position GPS : ${locationText(weather.latitude, weather.longitude, locationLabel)}")
        }
        analyse.forEach { add("• $it") }
        add("Observé le $horodatage avec Cloud Detect")
    }

    val bitmap = buildCaptureBitmap(
        photo = frame,
        title = "${info.nameFr} (${info.code})",
        subtitle = info.famille,
        lines = lines,
    )
    return saveBitmapToGallery(context, bitmap, "clouddetect_${System.currentTimeMillis()}.jpg")
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

/** Recadre sur un carré central couvrant `fraction` de la plus petite dimension. */
private fun Bitmap.centerCrop(fraction: Float): Bitmap {
    val cropSize = (minOf(width, height) * fraction).toInt().coerceAtLeast(1)
    val x = ((width - cropSize) / 2).coerceAtLeast(0)
    val y = ((height - cropSize) / 2).coerceAtLeast(0)
    return Bitmap.createBitmap(this, x, y, cropSize, cropSize)
}
