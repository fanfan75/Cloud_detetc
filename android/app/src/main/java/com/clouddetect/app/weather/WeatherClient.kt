package com.clouddetect.app.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

private const val TAG = "WeatherClient"
private const val API_URL = "https://api.open-meteo.com/v1/forecast"

/** Conditions météo relevées à la position de l'observateur. */
data class CurrentWeather(
    val temperatureC: Double,
    val humidityPct: Int,
    val cloudCoverPct: Int,
    val pressureHpa: Double,
    val windSpeedKmh: Double,
    val precipitationMm: Double,
    val weatherCode: Int,
    val latitude: Double,
    val longitude: Double,
) {
    val description: String
        get() = WEATHER_CODES[weatherCode] ?: "Conditions indéterminées"
}

/** Table des codes météo WMO (code table 4677) utilisée par Open-Meteo. */
private val WEATHER_CODES: Map<Int, String> = mapOf(
    0 to "Ciel dégagé",
    1 to "Principalement dégagé",
    2 to "Partiellement nuageux",
    3 to "Couvert",
    45 to "Brouillard",
    48 to "Brouillard givrant",
    51 to "Bruine légère",
    53 to "Bruine modérée",
    55 to "Bruine dense",
    56 to "Bruine verglaçante légère",
    57 to "Bruine verglaçante dense",
    61 to "Pluie légère",
    63 to "Pluie modérée",
    65 to "Pluie forte",
    66 to "Pluie verglaçante légère",
    67 to "Pluie verglaçante forte",
    71 to "Neige légère",
    73 to "Neige modérée",
    75 to "Neige forte",
    77 to "Grains de neige",
    80 to "Averses légères",
    81 to "Averses modérées",
    82 to "Averses violentes",
    85 to "Averses de neige légères",
    86 to "Averses de neige fortes",
    95 to "Orage",
    96 to "Orage avec grêle légère",
    99 to "Orage avec grêle forte",
)

/**
 * Récupère la position de l'appareil : dernière position connue si disponible, sinon une
 * mesure ponctuelle (avec délai maximal), sinon null.
 *
 * L'appelant doit avoir vérifié la permission de localisation au préalable.
 */
@SuppressLint("MissingPermission")
suspend fun awaitLocation(context: Context): Pair<Double, Double>? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    lastKnownLocation(manager)?.let { return it }

    val provider = when {
        manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        else -> return null
    }

    return withTimeoutOrNull(20_000L) {
        suspendCancellableCoroutine<Pair<Double, Double>?> { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(location.latitude to location.longitude)
                    }
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (e: Exception) {
                Log.e(TAG, "Impossible de demander la position", e)
                if (continuation.isActive) continuation.resume(null)
            }
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        }
    }
}

@SuppressLint("MissingPermission")
private fun lastKnownLocation(manager: LocationManager): Pair<Double, Double>? {
    var best: Location? = null
    for (provider in manager.getProviders(true)) {
        val location = try {
            manager.getLastKnownLocation(provider)
        } catch (e: SecurityException) {
            null
        } ?: continue
        val currentBest = best
        if (currentBest == null || location.accuracy < currentBest.accuracy) best = location
    }
    return best?.let { it.latitude to it.longitude }
}

/** Interroge Open-Meteo (gratuit, sans clé) pour les conditions actuelles. */
suspend fun fetchCurrentWeather(latitude: Double, longitude: Double): CurrentWeather? =
    withContext(Dispatchers.IO) {
        val url = URL(
            "$API_URL?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,precipitation,weather_code," +
                "cloud_cover,pressure_msl,wind_speed_10m"
        )
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            if (connection.responseCode !in 200..299) {
                Log.e(TAG, "Open-Meteo a répondu ${connection.responseCode}")
                return@withContext null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            CurrentWeather(
                temperatureC = current.optDouble("temperature_2m", Double.NaN),
                humidityPct = current.optInt("relative_humidity_2m", -1),
                cloudCoverPct = current.optInt("cloud_cover", -1),
                pressureHpa = current.optDouble("pressure_msl", Double.NaN),
                windSpeedKmh = current.optDouble("wind_speed_10m", Double.NaN),
                precipitationMm = current.optDouble("precipitation", 0.0),
                weatherCode = current.optInt("weather_code", -1),
                latitude = latitude,
                longitude = longitude,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Échec de la récupération météo", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
