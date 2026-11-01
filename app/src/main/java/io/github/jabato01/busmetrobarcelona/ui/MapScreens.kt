package io.github.jabato01.busmetrobarcelona.ui

import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.jabato01.busmetrobarcelona.AppModel
import io.github.jabato01.busmetrobarcelona.Screen
import io.github.jabato01.busmetrobarcelona.data.Api
import io.github.jabato01.busmetrobarcelona.data.ApiException
import io.github.jabato01.busmetrobarcelona.data.BusFavorite
import io.github.jabato01.busmetrobarcelona.data.Favorite
import io.github.jabato01.busmetrobarcelona.data.Itinerary
import io.github.jabato01.busmetrobarcelona.data.MetroFavorite
import io.github.jabato01.busmetrobarcelona.data.NearbyBus
import io.github.jabato01.busmetrobarcelona.data.NearbyMetro
import io.github.jabato01.busmetrobarcelona.data.NearbyStop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val MAP_ATTRIBUTION = "Mapa: © OpenFreeMap © OpenMapTiles © OpenStreetMap"

private fun NearbyStop.toFavorite(): Favorite = when (this) {
    is NearbyBus -> BusFavorite(stop, "", name)
    is NearbyMetro -> MetroFavorite(line, lineName, color, station, name)
}

private fun AppModel.openStop(s: NearbyStop) = when (s) {
    is NearbyBus -> open(Screen.Bus(s.stop))
    is NearbyMetro -> open(Screen.Metro(s.line, s.station))
}

/** A stop's kind or line, plus its lines, in coloured badges. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StopLines(s: NearbyStop) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (s) {
            is NearbyBus -> {
                Text("Parada ${s.stop}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.lines.forEach { LineBadge(it.name, parseColor(it.color)) }
            }
            is NearbyMetro -> LineBadge(s.lineName, parseColor(s.color))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NearbyScreen(model: AppModel) {
    val radius = 100.0
    val goodAccuracy = 50f
    val moveThreshold = 25.0
    val location = rememberLocation()
    var results by remember { mutableStateOf<List<NearbyStop>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var waited by remember { mutableStateOf(false) }
    var noSignal by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }

    // Accept a less accurate fix after a while; after a minute with nothing, say what to check.
    LaunchedEffect(Unit) {
        delay(30000)
        waited = true
        delay(30000)
        noSignal = true
    }
    val fix = location.fix
    val usable = fix != null && (waited || (fix.accuracy ?: Float.MAX_VALUE) <= goodAccuracy)
    LaunchedEffect(usable, fix?.lat, fix?.lon, request) {
        val f = fix ?: return@LaunchedEffect
        val last = searched
        val moved = last == null || Api.distance(f.lat, f.lon, last.first, last.second) > moveThreshold
        if (!usable || !model.credentials.isComplete || (!moved && request == 0)) return@LaunchedEffect
        searched = f.lat to f.lon
        loading = true
        try {
            results = Api.nearby(f.lat, f.lon, radius, model.credentials)
            error = ""
        } catch (e: ApiException) {
            error = e.message ?: ""
        }
        loading = false
    }

    Scaffold(topBar = {
        Bar("Cerca de mí", onBack = { model.back() }, actions = {
            IconButton(onClick = { model.open(Screen.Map) }) { Icon(Icons.Default.LocationOn, "Mapa") }
            IconButton(onClick = { request++ }) { Icon(Icons.Default.Refresh, "Actualizar") }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (results.isEmpty() && !loading) {
                item {
                    when {
                        !model.credentials.isComplete -> Empty("Faltan las credenciales", "Vuelve atrás y abre Ajustes.")
                        location.permissionDenied -> Empty("Sin permiso de ubicación", "Concédelo en los ajustes del sistema para ver las paradas cercanas.")
                        !location.providerAvailable -> Empty("Ubicación desactivada", "Actívala en los ajustes del sistema.")
                        error.isNotEmpty() -> Empty(error, error = true)
                        searched != null -> Empty("No hay paradas cerca", "Ninguna parada a menos de ${radius.toInt()} m. Prueba con el mapa.")
                        noSignal -> Empty("Sin señal GPS", "Prueba al aire libre. Sigue buscando…")
                        else -> Empty("Localizando señal GPS…", fix?.accuracy?.let { "Precisión actual ±${it.toInt()} m" } ?: "")
                    }
                }
            }
            if (loading && results.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            }
            if (results.isNotEmpty() && fix?.accuracy != null) {
                item {
                    Text(
                        "Precisión GPS ±${fix.accuracy.toInt()} m", Modifier.padding(16.dp, 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(results) { s ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Column(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { model.openStop(s) }, onLongClick = { menu = true })
                            .padding(16.dp, 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LineBadge(if (s is NearbyMetro) "METRO" else "BUS", if (s is NearbyMetro) parseColor(s.color) else BusRed)
                            Spacer(Modifier.width(8.dp))
                            Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(distanceText(s.distance), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StopLines(s)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val fav = s.toFavorite()
                        DropdownMenuItem(
                            text = { Text(if (model.isFavorite(fav)) "Ya está en favoritos" else "Añadir a favoritos") },
                            enabled = !model.isFavorite(fav),
                            onClick = { menu = false; model.addFavorite(fav) },
                        )
                    }
                }
                HorizontalDivider()
            }
            if (results.isNotEmpty()) item { DataSource(dataDate = results.first().dataDate) }
        }
    }
}

/** A MapLibre map following the screen's lifecycle. */
@Composable
private fun StyledMap(modifier: Modifier = Modifier, onReady: (MapLibreMap, Style) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map -> map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style -> onReady(map, style) } }
        }
    }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun point(lat: Double, lon: Double, color: String? = null): JSONObject = JSONObject()
    .put("type", "Feature")
    .put("properties", JSONObject().apply { if (color != null) put("color", color) })
    .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(lon).put(lat)))

private fun collection(features: List<JSONObject>) =
    JSONObject().put("type", "FeatureCollection").put("features", JSONArray(features)).toString()

private fun dots(style: Style, id: String, radius: Float, strokeWidth: Float, color: String? = null, strokeColor: String = "#FFFFFF") {
    style.addSource(GeoJsonSource(id, collection(emptyList())))
    style.addLayer(CircleLayer(id, id).withProperties(
        PropertyFactory.circleRadius(radius),
        if (color != null) PropertyFactory.circleColor(color) else PropertyFactory.circleColor(Expression.get("color")),
        PropertyFactory.circleStrokeWidth(strokeWidth),
        PropertyFactory.circleStrokeColor(strokeColor),
    ))
}

private fun Style.setDots(id: String, features: List<JSONObject>) {
    getSourceAs<GeoJsonSource>(id)?.setGeoJson(collection(features))
}

@Composable
private fun Attribution(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)).padding(8.dp, 2.dp),
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
fun MapScreen(model: AppModel) {
    val minStopsZoom = 14.5
    val maxRadius = 800.0
    val location = rememberLocation()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var stops by remember { mutableStateOf<List<NearbyStop>>(emptyList()) }
    var selected by remember { mutableStateOf<NearbyStop?>(null) }
    var status by remember { mutableStateOf("Mapa") }
    var centered by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }
    var reloads by remember { mutableIntStateOf(0) }

    // Reload the stops once the camera settles.
    LaunchedEffect(reloads) {
        val m = map ?: return@LaunchedEffect
        delay(500)
        val target = m.cameraPosition.target ?: return@LaunchedEffect
        if (m.cameraPosition.zoom < minStopsZoom) {
            stops = emptyList()
            status = "Acerca el mapa"
            return@LaunchedEffect
        }
        if (!model.credentials.isComplete) {
            status = "Faltan las credenciales"
            return@LaunchedEffect
        }
        val id = ++request
        status = "Cargando paradas…"
        val corner = m.projection.fromScreenLocation(PointF(0f, 0f))
        val radius = minOf(maxRadius, Api.distance(target.latitude, target.longitude, corner.latitude, corner.longitude))
        try {
            val items = Api.nearby(target.latitude, target.longitude, radius, model.credentials)
            if (id == request) {
                stops = items
                status = "${items.size} paradas"
            }
        } catch (e: ApiException) {
            if (id == request) status = e.message ?: ""
        }
    }
    LaunchedEffect(stops, style) {
        val st = style ?: return@LaunchedEffect
        st.setDots("bus", stops.filterIsInstance<NearbyBus>().map { point(it.lat, it.lon) })
        st.setDots("metro", stops.filterIsInstance<NearbyMetro>().map { point(it.lat, it.lon, it.color) })
    }
    LaunchedEffect(selected, style) {
        val s = selected
        style?.setDots("selected", if (s != null) listOf(point(s.lat, s.lon)) else emptyList())
    }
    val fix = location.fix
    LaunchedEffect(fix?.lat, fix?.lon, style) {
        val st = style ?: return@LaunchedEffect
        val f = fix ?: return@LaunchedEffect
        st.setDots("me", listOf(point(f.lat, f.lon)))
        if (!centered) {
            centered = true
            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(f.lat, f.lon), 16.0))
        }
    }

    Scaffold(topBar = {
        Bar(status, onBack = { model.back() }, actions = {
            IconButton(enabled = fix != null, onClick = {
                fix?.let { map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 16.0)) }
            }) { Icon(Icons.Default.LocationOn, "Mi ubicación") }
        })
    }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            StyledMap(Modifier.fillMaxSize()) { m, st ->
                m.cameraPosition = CameraPosition.Builder().target(LatLng(41.387, 2.170)).zoom(16.0).build()
                dots(st, "bus", 6f, 2f, "#DA291C")
                dots(st, "metro", 9f, 3f)
                dots(st, "selected", 14f, 4f, "#00000000", "#222222")
                dots(st, "me", 8f, 3f, "#1E88E5")
                m.addOnCameraIdleListener { reloads++ }
                m.addOnMapClickListener { latLng ->
                    val tap = m.projection.toScreenLocation(latLng)
                    selected = stops.minByOrNull {
                        val p = m.projection.toScreenLocation(LatLng(it.lat, it.lon))
                        (p.x - tap.x) * (p.x - tap.x) + (p.y - tap.y) * (p.y - tap.y)
                    }?.takeIf {
                        val p = m.projection.toScreenLocation(LatLng(it.lat, it.lon))
                        val reach = 32 * context.resources.displayMetrics.density
                        (p.x - tap.x) * (p.x - tap.x) + (p.y - tap.y) * (p.y - tap.y) < reach * reach
                    }
                    true
                }
                map = m
                style = st
                reloads++
            }
            Column(Modifier.align(Alignment.TopStart).fillMaxWidth()) {
                Attribution((stops.firstOrNull()?.let { Api.staticSourceText(it.dataDate) + " · " } ?: "") + MAP_ATTRIBUTION)
            }
            selected?.let { s ->
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface).clickable { model.openStop(s) }.padding(16.dp),
                ) {
                    Text(s.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    StopLines(s)
                    Row {
                        TextButton(onClick = { model.openStop(s) }) { Text("Ver tiempos") }
                        TextButton(onClick = {
                            // Lets the user's own map app (Organic Maps, OsmAnd…) take over.
                            val uri = Uri.parse("geo:${s.lat},${s.lon}?q=${s.lat},${s.lon}(${Uri.encode(s.name)})")
                            scope.launch {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            }
                        }) { Text("Abrir en otra app") }
                    }
                }
            }
        }
    }
}

@Composable
fun RouteMapScreen(model: AppModel, itinerary: Itinerary, updated: Long) {
    val title = Api.clockText(itinerary.start) + " – " + Api.clockText(itinerary.end) + "  " +
        itinerary.legs.filterNot { it.isWalk }.joinToString(" › ") { it.line.ifEmpty { it.modeName } }

    Scaffold(topBar = { Bar(title, onBack = { model.back() }) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            StyledMap(Modifier.fillMaxSize()) { map, style ->
                val lines = mutableListOf<JSONObject>()
                val ends = mutableListOf<JSONObject>()
                val all = mutableListOf<Pair<Double, Double>>()
                itinerary.legs.forEach { leg ->
                    val pts = Api.decodePolyline(leg.points)
                    if (pts.size < 2) return@forEach
                    all += pts
                    val color = if (leg.isWalk) "#555555" else leg.color.ifEmpty { "#DA291C" }
                    lines += JSONObject()
                        .put("type", "Feature")
                        .put("properties", JSONObject().put("walk", leg.isWalk).put("color", color))
                        .put("geometry", JSONObject().put("type", "LineString").put("coordinates", JSONArray(pts.map { JSONArray().put(it.second).put(it.first) })))
                    if (!leg.isWalk) {
                        ends += point(pts.first().first, pts.first().second, color)
                        ends += point(pts.last().first, pts.last().second, color)
                    }
                }
                if (all.isNotEmpty()) {
                    ends += point(all.first().first, all.first().second, "#1E88E5")
                    ends += point(all.last().first, all.last().second, "#222222")
                }
                style.addSource(GeoJsonSource("route", collection(lines)))
                style.addLayer(LineLayer("route-walk", "route").withFilter(Expression.eq(Expression.get("walk"), true)).withProperties(
                    PropertyFactory.lineColor(Expression.get("color")),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineDasharray(arrayOf(1f, 2f)),
                ))
                style.addLayer(LineLayer("route-transit", "route").withFilter(Expression.eq(Expression.get("walk"), false)).withProperties(
                    PropertyFactory.lineColor(Expression.get("color")),
                    PropertyFactory.lineWidth(7f),
                ))
                dots(style, "ends", 7f, 3f)
                style.setDots("ends", ends)
                if (all.size >= 2) {
                    val bounds = LatLngBounds.Builder().apply { all.forEach { include(LatLng(it.first, it.second)) } }.build()
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
                }
            }
            Attribution(Api.sourceText(updated) + " · " + MAP_ATTRIBUTION)
        }
    }
}
