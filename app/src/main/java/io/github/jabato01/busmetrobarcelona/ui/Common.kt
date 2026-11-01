package io.github.jabato01.busmetrobarcelona.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.jabato01.busmetrobarcelona.data.Api

val BusRed = Color(0xFFDA291C)

fun parseColor(hex: String, fallback: Color = BusRed): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    fallback
}

fun minutesText(m: Int) = if (m <= 0) "Llegando" else "$m min"

fun distanceText(metres: Double) =
    if (metres < 1000) "${Math.round(metres)} m" else String.format(java.util.Locale.ROOT, "%.1f km", metres / 1000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                }
            }
        },
        actions = actions,
    )
}

@Composable
fun LineBadge(text: String, color: Color, textColor: Color = Color.White) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
    )
}

/** Source and update date of the data shown, as TMB's licence requires. */
@Composable
fun DataSource(updated: Long? = null, dataDate: String = "") {
    val text = when {
        dataDate.isNotEmpty() -> Api.staticSourceText(dataDate)
        updated != null -> Api.sourceText(updated)
        else -> return
    }
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    if (text.isEmpty()) return
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(16.dp),
        textAlign = TextAlign.Center,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Centred text for empty screens. */
@Composable
fun Empty(title: String, hint: String = "", error: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(8.dp))
        Hint(hint)
    }
}

/** A position from Android's own location service, with no Google Play dependency. */
data class Fix(val lat: Double, val lon: Double, val accuracy: Float?, val timeMillis: Long)

class LocationState {
    var fix by mutableStateOf<Fix?>(null)
    var permissionDenied by mutableStateOf(false)
    var providerAvailable by mutableStateOf(true)
}

private fun Location.toFix() = Fix(latitude, longitude, if (hasAccuracy()) accuracy else null, time)

/** Requests permission if needed and follows the GPS and network providers while on screen. */
@SuppressLint("MissingPermission")
@Composable
fun rememberLocation(): LocationState {
    val context = LocalContext.current
    val state = remember { LocationState() }
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
        state.permissionDenied = !granted
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager.allProviders.contains(it) && manager.isProviderEnabled(it) }
        state.providerAvailable = providers.isNotEmpty()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val fix = location.toFix()
                val old = state.fix
                // Keep a better recent fix rather than replacing it with a rough one from another provider.
                if (old == null || fix.timeMillis - old.timeMillis > 30000 ||
                    (fix.accuracy ?: Float.MAX_VALUE) <= (old.accuracy ?: Float.MAX_VALUE)
                ) {
                    state.fix = fix
                }
            }
            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) { state.providerAvailable = true }
        }
        providers.forEach { manager.requestLocationUpdates(it, 3000L, 0f, listener, Looper.getMainLooper()) }
        onDispose { manager.removeUpdates(listener) }
    }
    return state
}

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
