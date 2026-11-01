package io.github.jabato01.busmetrobarcelona.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jabato01.busmetrobarcelona.AppModel
import io.github.jabato01.busmetrobarcelona.PlannerPlace
import io.github.jabato01.busmetrobarcelona.Screen
import io.github.jabato01.busmetrobarcelona.data.Api
import io.github.jabato01.busmetrobarcelona.data.ApiException
import io.github.jabato01.busmetrobarcelona.data.Credentials
import io.github.jabato01.busmetrobarcelona.data.Favorite
import io.github.jabato01.busmetrobarcelona.data.Itinerary
import io.github.jabato01.busmetrobarcelona.data.Leg
import io.github.jabato01.busmetrobarcelona.data.Place
import io.github.jabato01.busmetrobarcelona.data.PlanTime
import io.github.jabato01.busmetrobarcelona.data.ServiceAlert
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import kotlinx.coroutines.launch

private fun duration(seconds: Int): String {
    val m = Math.round(seconds / 60.0).toInt()
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}

/** Full-screen place chooser: my location, recent places or an address search run on demand. */
@Composable
private fun PlacePicker(model: AppModel, title: String, onPick: (PlannerPlace) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun search() {
        val q = text.trim()
        if (q.length < 3) return
        loading = true
        error = ""
        results = null
        scope.launch {
            try {
                results = Api.geocode(q, model.geocoder)
            } catch (e: ApiException) {
                error = e.message ?: ""
                results = emptyList()
            }
            loading = false
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Scaffold(topBar = { Bar(title, onBack = onDismiss) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Row(Modifier.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        text,
                        { text = it; if (it.isBlank()) { results = null; error = "" } },
                        Modifier.weight(1f), label = { Text("Dirección o lugar") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                    )
                    TextButton(onClick = { search() }, enabled = text.trim().length >= 3) { Text("Buscar") }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    val found = results
                    if (found == null && !loading) {
                        item {
                            ListItem(
                                leadingContent = { Icon(Icons.Default.LocationOn, null) },
                                headlineContent = { Text("Mi ubicación") },
                                supportingContent = { Text("Posición GPS del teléfono") },
                                modifier = Modifier.clickable { onPick(PlannerPlace.MyLocation) },
                            )
                            HorizontalDivider()
                        }
                    }
                    items(found ?: if (loading) emptyList() else model.recentPlaces) { p ->
                        ListItem(
                            headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(p.detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { model.addRecentPlace(p); onPick(PlannerPlace.Fixed(p)) },
                        )
                        HorizontalDivider()
                    }
                    if (loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
                    if (error.isNotEmpty()) item { Hint(error, error = true) }
                    else if (found != null && found.isEmpty() && !loading) item { Hint("Sin resultados. Prueba con el nombre de la calle y el número.") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(model: AppModel) {
    val location = rememberLocation()
    var picking by remember { mutableStateOf<String?>(null) }
    var whenMode by remember { mutableIntStateOf(0) }
    var whenTime by remember { mutableStateOf(Date()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var request by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var itineraries by model::plannerItineraries
    var next by model::plannerNext
    var updated by model::plannerUpdated

    val origin = model.plannerOrigin
    val destination = model.plannerDestination
    val needsGps = origin is PlannerPlace.MyLocation || destination is PlannerPlace.MyLocation
    val fix = location.fix
    val ready = destination != null && (!needsGps || fix != null)

    fun clear() {
        request++
        itineraries = emptyList()
        next = ""
        error = ""
        loading = false
    }
    fun coords(p: PlannerPlace): Pair<Double, Double> = when (p) {
        is PlannerPlace.Fixed -> p.place.lat to p.place.lon
        PlannerPlace.MyLocation -> fix!!.lat to fix.lon
    }
    fun text(p: PlannerPlace?) = when (p) {
        null -> "Elige destino"
        is PlannerPlace.Fixed -> p.place.name
        PlannerPlace.MyLocation -> if (fix == null) "Mi ubicación (localizando…)" else "Mi ubicación"
    }
    fun search(more: Boolean) {
        val to = destination ?: return
        if (!ready || !model.credentials.isComplete) return
        if (!more) clear()
        val id = ++request
        loading = true
        error = ""
        scope.launch {
            try {
                val time = if (whenMode == 0) null else PlanTime(whenMode == 2, whenTime)
                val r = Api.plan(coords(origin), coords(to), time, if (more) next else "", model.credentials)
                if (id == request) {
                    itineraries = itineraries + r.itineraries
                    next = r.next
                    updated = System.currentTimeMillis()
                }
            } catch (e: ApiException) {
                if (id == request) error = e.message ?: ""
            }
            if (id == request) loading = false
        }
    }

    Scaffold(topBar = {
        Bar("Cómo llegar", onBack = { model.back() }, actions = {
            TextButton(enabled = destination != null, onClick = {
                val d = destination ?: return@TextButton
                model.plannerDestination = origin
                model.plannerOrigin = d
                clear()
            }) { Text("Intercambiar") }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                ListItem(headlineContent = { Text("Origen") }, supportingContent = { Text(text(origin)) }, modifier = Modifier.clickable { picking = "Origen" })
                HorizontalDivider()
                ListItem(headlineContent = { Text("Destino") }, supportingContent = { Text(text(destination)) }, modifier = Modifier.clickable { picking = "Destino" })
                HorizontalDivider()
                FlowRow(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Salir ahora", "Salir a las…", "Llegar a las…").forEachIndexed { i, label ->
                        FilterChip(selected = whenMode == i, onClick = { whenMode = i; if (i > 0 && whenTime.before(Date())) whenTime = Date(); clear() }, label = { Text(label) })
                    }
                }
                if (whenMode > 0) {
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        TextButton(onClick = { pickDate = true }) { Text("Día: " + Api.dateText(whenTime.time)) }
                        TextButton(onClick = { pickTime = true }) { Text("Hora: " + Api.clockText(whenTime.time)) }
                    }
                }
                Box(Modifier.fillMaxWidth(), Alignment.Center) {
                    Button(onClick = { search(false) }, enabled = ready && !loading) { Text("Buscar rutas") }
                }
                when {
                    !model.credentials.isComplete -> Hint("Faltan las credenciales de TMB (Ajustes)")
                    error.isNotEmpty() -> Hint(error, error = true)
                    needsGps && location.permissionDenied -> Hint("Sin permiso de ubicación: concédelo o elige otro origen")
                    needsGps && fix == null -> Hint("Localizando señal GPS… Si tarda, escribe el origen a mano.")
                }
            }
            items(itineraries) { it ->
                ItineraryRow(it) {
                    model.open(Screen.Trip(it, updated, text(origin), (destination as? PlannerPlace.Fixed)?.place?.name ?: text(destination)))
                }
                HorizontalDivider()
            }
            item {
                if (loading) Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                else if (itineraries.isNotEmpty() && next.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) {
                        TextButton(onClick = { search(true) }) { Text("Más opciones") }
                    }
                }
                if (itineraries.isNotEmpty()) DataSource(updated)
            }
        }
    }

    picking?.let { which ->
        Box(Modifier.fillMaxSize()) {
            PlacePicker(model, which, { p ->
                if (which == "Origen") model.plannerOrigin = p else model.plannerDestination = p
                picking = null
                clear()
            }, { picking = null })
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = whenTime.time)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
                        whenTime = Calendar.getInstance().apply {
                            time = whenTime
                            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
                        }.time
                    }
                    pickDate = false
                    clear()
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val cal = Calendar.getInstance().apply { time = whenTime }
        val state = rememberTimePickerState(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    whenTime = cal.apply {
                        set(Calendar.HOUR_OF_DAY, state.hour)
                        set(Calendar.MINUTE, state.minute)
                        set(Calendar.SECOND, 0)
                    }.time
                    pickTime = false
                    clear()
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancelar") } },
            text = { TimePicker(state) },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ItineraryRow(it: Itinerary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(Api.clockText(it.start) + " – " + Api.clockText(it.end), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(duration(it.duration), color = MaterialTheme.colorScheme.primary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            it.legs.forEach { leg ->
                if (leg.isWalk) {
                    if (leg.duration >= 60) {
                        Text("a pie ${Math.round(leg.duration / 60.0)}'", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LineBadge(leg.line.ifEmpty { leg.modeName }, parseColor(leg.color, Color.DarkGray), parseColor(leg.textColor, Color.White))
                }
            }
        }
        Text(
            "A pie ${duration(it.walkTime)} · " + when (it.transfers) {
                0 -> "sin transbordos"
                1 -> "1 transbordo"
                else -> "${it.transfers} transbordos"
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TripScreen(model: AppModel, trip: Screen.Trip) {
    val it = trip.itinerary
    var codes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        if (it.legs.any { l -> l.mode == "SUBWAY" }) {
            codes = try { Api.metroLines(model.credentials).associate { l -> l.name to l.code } } catch (e: ApiException) { emptyMap() }
        }
    }
    fun live(l: Leg) = l.from.stopCode.isNotEmpty() && ((l.mode == "BUS" && l.agency == "TMB") || (l.mode == "SUBWAY" && l.line in codes))

    Scaffold(topBar = {
        Bar(Api.clockText(it.start) + " – " + Api.clockText(it.end), onBack = { model.back() }, actions = {
            IconButton(onClick = { model.open(Screen.RouteMap(it, trip.updated)) }) { Icon(Icons.Default.LocationOn, "Ver en el mapa") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("${trip.origin} → ${trip.destination}", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            it.legs.forEach { leg ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(enabled = live(leg)) {
                        if (leg.mode == "BUS") model.open(Screen.Bus(leg.from.stopCode, leg.line))
                        else model.open(Screen.Metro(codes[leg.line] ?: "", leg.from.stopCode))
                    }.padding(16.dp, 8.dp),
                ) {
                    Text(Api.clockText(leg.start), Modifier.width(52.dp), color = MaterialTheme.colorScheme.primary)
                    Box(
                        Modifier.width(if (leg.isWalk) 2.dp else 6.dp).fillMaxHeight()
                            .then(Modifier.padding(0.dp)),
                    ) {
                        Surface(
                            Modifier.fillMaxSize(),
                            color = if (leg.isWalk) Color.Gray else parseColor(leg.color, Color.DarkGray),
                        ) {}
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        if (leg.isWalk) {
                            val dist = if (leg.distance < 1000) "${Math.round(leg.distance / 10) * 10} m" else String.format(java.util.Locale.ROOT, "%.1f km", leg.distance / 1000)
                            Text("Camina $dist (${duration(leg.duration)}) hasta ${leg.to.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("${leg.modeName} ${leg.line}" + if (leg.headsign.isEmpty()) "" else " dirección ${leg.headsign}", fontWeight = FontWeight.Bold)
                            Text(
                                "Sube en ${leg.from.name}\n" + (if (leg.stops == 1) "1 parada" else "${leg.stops} paradas") + " · ${duration(leg.duration)}\nBaja en ${leg.to.name} a las ${Api.clockText(leg.end)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (live(leg)) {
                                Text(
                                    "Toca para ver los próximos " + if (leg.mode == "BUS") "buses" else "trenes",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(16.dp, 8.dp)) {
                Text(Api.clockText(it.end), Modifier.width(64.dp), color = MaterialTheme.colorScheme.primary)
                Text("Llegada a ${trip.destination}", fontWeight = FontWeight.Bold)
            }
            DataSource(trip.updated)
        }
    }
}

@Composable
fun AlertsScreen(model: AppModel, favorite: Favorite?) {
    var query by remember { mutableStateOf("") }
    var onlyFavorites by remember { mutableStateOf(false) }
    val shown: List<ServiceAlert> = model.alerts.filter { a ->
        if (favorite != null) return@filter a in model.alertsFor(favorite)
        if (onlyFavorites && model.favorites.none { a in model.alertsFor(it) }) return@filter false
        val q = query.trim().uppercase()
        q.isEmpty() || a.lines.any { it.uppercase() == q } || q in a.stops || a.title.uppercase().contains(q)
    }.sortedWith(compareBy({ !it.isMetro }, { (it.lines.firstOrNull() ?: "").length }, { it.lines.firstOrNull() ?: "" }))

    Scaffold(topBar = {
        Bar(favorite?.let { "Avisos · ${it.name}" } ?: "Avisos de servicio", onBack = { model.back() }, actions = {
            IconButton(onClick = { model.loadAlerts() }) { Icon(Icons.Default.Refresh, "Actualizar") }
        })
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (favorite == null) {
                item {
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                        label = { Text("Filtrar por línea o parada (p. ej. L1, V15)") }, singleLine = true,
                    )
                    if (model.favorites.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(16.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Solo los que afectan a mis favoritos", Modifier.weight(1f))
                            Switch(onlyFavorites, { onlyFavorites = it })
                        }
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    Hint(
                        when {
                            model.alertsError.isNotEmpty() -> model.alertsError
                            model.alertsUpdated == null -> "Cargando avisos…"
                            favorite != null || onlyFavorites -> "Ningún aviso afecta a tus favoritos"
                            query.isNotBlank() -> "Ningún aviso para «$query»"
                            else -> "Sin avisos"
                        },
                        error = model.alertsError.isNotEmpty(),
                    )
                }
            }
            items(shown.size) { i -> AlertRow(shown[i], i == 0 || shown[i - 1].isMetro != shown[i].isMetro) }
            item { DataSource(model.alertsUpdated) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AlertRow(a: ServiceAlert, firstOfKind: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (firstOfKind) {
            Text(if (a.isMetro) "METRO" else "BUS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            a.lines.forEach { LineBadge(it, if (a.isMetro) metroColor(it) else BusRed) }
            Text(a.title, fontWeight = FontWeight.Bold)
        }
        Text(
            a.text, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded && a.period.isNotEmpty()) {
            Text(a.period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    HorizontalDivider()
}

private val METRO_COLORS = mapOf(
    "L1" to "#CE1126", "L2" to "#93248F", "L3" to "#1EB53A", "L4" to "#F7A30E", "L5" to "#005A97",
    "L9N" to "#F68B1F", "L9S" to "#F68B1F", "L10N" to "#00A6D6", "L10S" to "#00A6D6", "L11" to "#89B94C",
)

private fun metroColor(name: String) = parseColor(METRO_COLORS[name] ?: "", BusRed)

@Composable
fun SettingsScreen(model: AppModel) {
    var appId by remember { mutableStateOf(model.credentials.appId) }
    var appKey by remember { mutableStateOf(model.credentials.appKey) }
    var provider by remember { mutableStateOf(model.geocoder) }
    val can = appId.isNotBlank() && appKey.isNotBlank()

    Scaffold(topBar = { Bar("Ajustes", onBack = { model.back() }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Esta app no incluye datos ni claves. Regístrate en developer.tmb.cat con tu propia cuenta, acepta sus condiciones y pon aquí las claves de tu aplicación. Se guardan cifradas, solo en este teléfono.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(appId, { appId = it }, Modifier.fillMaxWidth(), label = { Text("APP_ID") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false))
            OutlinedTextField(appKey, { appKey = it }, Modifier.fillMaxWidth(), label = { Text("APP_KEY") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
            Text("Buscador de direcciones (Cómo llegar)", fontWeight = FontWeight.Bold)
            listOf(
                "auto" to "Automático: ICGC primero si escribes un número",
                "icgc" to "ICGC: direcciones con número",
                "osm" to "OpenStreetMap: lugares, comercios y monumentos",
            ).forEach { (id, label) ->
                Row(Modifier.fillMaxWidth().clickable { provider = id }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(provider == id, { provider = id })
                    Text(label)
                }
            }
            Button(
                onClick = {
                    model.saveSettings(Credentials(appId.trim(), appKey.trim()), provider)
                    model.back()
                },
                enabled = can, modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Guardar") }
            Text("Acerca de", fontWeight = FontWeight.Bold)
            Text(
                "Datos de la API de TMB, usados bajo sus condiciones de reutilización. App no oficial y sin ánimo de lucro, sin ninguna vinculación con TMB ni con ningún ente público. Cada persona responde del uso que haga con sus claves. Pensada para móviles Android sin servicios de Google. Desarrollada mediante Claude Code (vibecodeada).\n\nSe ofrece sin garantía: los tiempos, avisos y rutas pueden no ser exactos; úsala como ayuda orientativa. Puede retirarse en cualquier momento ante cualquier reclamación de TMB.\n\nCódigo (GPLv3): github.com/Jabato01/bus-metro-barcelona",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
