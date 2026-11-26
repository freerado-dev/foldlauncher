package sk.fold.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Aktuálne počasie. code = WMO kód z Open-Meteo. */
data class WeatherNow(val temp: Int, val code: Int, val isDay: Boolean, val place: String, val at: Long)

/** Počasie z Open-Meteo (bez účtu a kľúča). */
object Weather {
    val state = MutableStateFlow<WeatherNow?>(null)
    /** Posledná chyba pre zobrazenie v nastaveniach. */
    val error = MutableStateFlow<String?>(null)
    private val lock = Mutex()
    private const val MAX_AGE = 30 * 60 * 1000L

    fun hasLocationPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(ctx: Context, force: Boolean = false): Unit = lock.withLock {
        val s = Store.settings.value
        if (!s.showWeather) return@withLock
        val cur = state.value
        if (!force && cur != null && System.currentTimeMillis() - cur.at < MAX_AGE) return@withLock
        withContext(Dispatchers.IO) {
            runCatching {
                val (lat, lon, place) = coords(ctx, s) ?: run {
                    error.value = if (s.weatherCity.isBlank()) "Povoľ polohu alebo zadaj mesto" else "Mesto „${s.weatherCity}“ sa nenašlo"
                    return@runCatching
                }
                val unit = if (s.weatherF) "&temperature_unit=fahrenheit" else ""
                val o = JSONObject(get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,weather_code,is_day&timezone=auto$unit"
                )).getJSONObject("current")
                state.value = WeatherNow(
                    Math.round(o.getDouble("temperature_2m")).toInt(),
                    o.optInt("weather_code", 0), o.optInt("is_day", 1) == 1, place, System.currentTimeMillis(),
                )
                error.value = null
            }.onFailure { error.value = "Počasie sa nepodarilo načítať (internet?)" }
        }
        Unit
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    private fun coords(ctx: Context, s: Settings): Triple<Double, Double, String>? {
        val city = s.weatherCity.trim()
        if (city.isNotEmpty()) {
            val p = prefs(ctx)
            if (p.getString("city", null) == city) {
                return Triple(p.getFloat("lat", 0f).toDouble(), p.getFloat("lon", 0f).toDouble(), p.getString("name", city) ?: city)
            }
            val r = JSONObject(get(
                "https://geocoding-api.open-meteo.com/v1/search?count=1&language=sk&name=" + URLEncoder.encode(city, "UTF-8")
            )).optJSONArray("results")?.optJSONObject(0) ?: return null
            val lat = r.getDouble("latitude")
            val lon = r.getDouble("longitude")
            val name = r.optString("name", city)
            p.edit().putString("city", city).putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat()).putString("name", name).apply()
            return Triple(lat, lon, name)
        }
        if (!hasLocationPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        var best: Location? = null
        for (prov in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)) {
            val l = runCatching { lm.getLastKnownLocation(prov) }.getOrNull() ?: continue
            if (best == null || l.time > best.time) best = l
        }
        val b = best ?: return null
        return Triple(b.latitude, b.longitude, "")
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "FoldLauncher")
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }
}
