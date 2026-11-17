package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val weatherClient = OkHttpClient()

private data class WeatherDayForecast(
    val day: String,
    val high: Double,
    val low: Double,
    val condition: String,
    val icon: String,
)

private data class WeatherDashboardData(
    val city: String,
    val country: String,
    val temperature: Double,
    val humidity: Int,
    val windKph: Double,
    val condition: String,
    val icon: String,
    val forecast: List<WeatherDayForecast>,
)

@Composable
fun WeatherScreen(onBack: () -> Unit) {
    var cityInput by remember { mutableStateOf("New York") }
    var weather by remember { mutableStateOf<WeatherDashboardData?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadWeather() {
        val trimmed = cityInput.trim()
        if (trimmed.isBlank()) {
            weather = null
            error = "Enter a city name"
            return
        }

        loading = true
        error = null

        androidx.compose.runtime.LaunchedEffect(trimmed) {
            try {
                weather = fetchWeatherDashboard(trimmed)
                error = null
            } catch (e: Exception) {
                weather = null
                error = e.message ?: "Unable to load weather data."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadWeather()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Weather Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = cityInput,
                    onValueChange = { cityInput = it },
                    label = { Text("City") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    trailingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                )
                Button(
                    onClick = { loadWeather() },
                    enabled = !loading
                ) {
                    Text(if (loading) "Loading..." else "Update")
                }
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = error ?: "Weather unavailable",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        } else if (weather != null) {
            val current = weather!!

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = current.icon,
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = current.city,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = current.country,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Text(
                        text = "${current.temperature.toInt()}°C",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = current.condition,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Icon(
                        imageVector = Icons.Filled.WbSunny,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    WeatherStat(label = "Humidity", value = "${current.humidity}%")
                    WeatherStat(label = "Wind", value = "${current.windKph.toInt()} km/h")
                }
            }

            Text(
                text = "7-Day Forecast",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                current.forecast.forEach { day ->
                    Card(
                        modifier = Modifier.width(120.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = day.day, style = MaterialTheme.typography.labelLarge)
                            Text(text = day.icon, style = MaterialTheme.typography.headlineSmall)
                            Text(text = day.condition, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "${day.high.toInt()}° / ${day.low.toInt()}°",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherStat(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private suspend fun fetchWeatherDashboard(city: String): WeatherDashboardData = withContext(Dispatchers.IO) {
    val encodedCity = URLEncoder.encode(city, "UTF-8")
    val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=en&format=json"
    val geoRequest = Request.Builder().url(geoUrl).build()
    val geoResponse = weatherClient.newCall(geoRequest).execute()
    if (!geoResponse.isSuccessful) {
        geoResponse.close()
        throw IOException("Could not find weather for '$city'.")
    }

    val geoBody = geoResponse.body?.string() ?: throw IOException("No data returned for '$city'.")
    geoResponse.close()

    val geoJson = JSONObject(geoBody)
    val results = geoJson.optJSONArray("results") ?: throw IOException("Could not find city '$city'.")
    if (results.length() == 0) {
        throw IOException("Could not find city '$city'.")
    }

    val location = results.getJSONObject(0)
    val latitude = location.getDouble("latitude")
    val longitude = location.getDouble("longitude")
    val cityName = location.optString("name", city)
    val countryName = location.optString("country", "Unknown")

    val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7"
    val forecastRequest = Request.Builder().url(forecastUrl).build()
    val forecastResponse = weatherClient.newCall(forecastRequest).execute()
    if (!forecastResponse.isSuccessful) {
        forecastResponse.close()
        throw IOException("Weather service unavailable right now.")
    }

    val forecastBody = forecastResponse.body?.string() ?: throw IOException("No forecast data returned.")
    forecastResponse.close()

    val forecastJson = JSONObject(forecastBody)
    val current = forecastJson.getJSONObject("current")
    val daily = forecastJson.getJSONObject("daily")
    val times = daily.optJSONArray("time") ?: throw IOException("Daily forecast is missing.")
    val maxTemps = daily.optJSONArray("temperature_2m_max") ?: throw IOException("High temperatures are missing.")
    val minTemps = daily.optJSONArray("temperature_2m_min") ?: throw IOException("Low temperatures are missing.")
    val codes = daily.optJSONArray("weather_code") ?: throw IOException("Weather codes are missing.")

    val weatherCode = current.optInt("weather_code", 0)
    val condition = weatherLabel(weatherCode)
    val icon = weatherIcon(weatherCode)

    val forecast = mutableListOf<WeatherDayForecast>()
    val maxItems = minOf(times.length(), maxTemps.length(), minTemps.length(), codes.length(), 7)
    for (index in 0 until maxItems) {
        val dateText = times.getString(index)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateText) ?: Date()
        val dayLabel = SimpleDateFormat("EEE", Locale.US).format(date)
        forecast.add(
            WeatherDayForecast(
                day = dayLabel,
                high = maxTemps.getDouble(index),
                low = minTemps.getDouble(index),
                condition = weatherLabel(codes.getInt(index)),
                icon = weatherIcon(codes.getInt(index))
            )
        )
    }

    WeatherDashboardData(
        city = cityName,
        country = countryName,
        temperature = current.optDouble("temperature_2m", 0.0),
        humidity = current.optInt("relative_humidity_2m", 0),
        windKph = current.optDouble("wind_speed_10m", 0.0),
        condition = condition,
        icon = icon,
        forecast = forecast
    )
}

private fun weatherLabel(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Foggy"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77, 85, 86 -> "Snow"
    80, 81, 82 -> "Rain showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "Fair"
}

private fun weatherIcon(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "⛅"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57 -> "🌦️"
    61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "❄️"
    95, 96, 99 -> "⛈️"
    else -> "🌤️"
}
