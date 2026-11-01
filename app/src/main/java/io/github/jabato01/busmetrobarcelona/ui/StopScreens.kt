package io.github.jabato01.busmetrobarcelona.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.jabato01.busmetrobarcelona.AppModel
import io.github.jabato01.busmetrobarcelona.Screen
import io.github.jabato01.busmetrobarcelona.data.Api
import io.github.jabato01.busmetrobarcelona.data.ApiException
import io.github.jabato01.busmetrobarcelona.data.BusArrival
import io.github.jabato01.busmetrobarcelona.data.BusFavorite
import io.github.jabato01.busmetrobarcelona.data.Favorite
import io.github.jabato01.busmetrobarcelona.data.MetroDirection
import io.github.jabato01.busmetrobarcelona.data.MetroFavorite
import io.github.jabato01.busmetrobarcelona.data.MetroLine
import io.github.jabato01.busmetrobarcelona.data.MetroStation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REFRESH_MILLIS = 30000L

private data class FavRow(val label: String, val color: String, val text: String)
private data class FavState(val rows: List<FavRow> = emptyList(), val error: String = "", val loaded: Boolean = false)

private fun minutesList(minutes: List<Int>) = minutes.take(3).joinToString(" · ") { minutesText(it) }

private suspend fun loadFavorite(f: Favorite, model: AppModel): List<FavRow> = when (f) {
    is BusFavorite -> Api.busForecast(f.stop, f.line, model.credentials).groupBy { it.line }.map { (line, items) ->
        FavRow(line, "", minutesList(items.mapNotNull { it.minutes }))
    }
    is MetroFavorite -> Api.metroForecast(f.station, model.credentials).map {
        FavRow(it.line, it.color, "→ ${it.destination}  " + if (it.minutes.isEmpty()) "Sin trenes" else minutesList(it.minutes))
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(model: AppModel) {
    val states = remember { mutableStateMapOf<String, FavState>() }
    var updated by remember { mutableStateOf<Long?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(tick, model.favorites.size, model.credentials) {
        if (!model.credentials.isComplete) return@LaunchedEffect
        model.favorites.toList().forEach { f ->
            launch {
                try {
                    states[f.key] = FavState(loadFavorite(f, model), loaded = true)
                    updated = System.currentTimeMillis()
                } catch (e: ApiException) {
                    states[f.key] = FavState(states[f.key]?.rows ?: emptyList(), e.message ?: "", true)
                }
            }
        }
        refreshing = false
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_MILLIS)
            tick++
        }
    }

    Scaffold(topBar = {
        Bar("Favoritos", actions = {
            IconButton(onClick = { tick++ }) { Icon(Icons.Default.Refresh, "Actualizar") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Menú") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(
                    "Bus" to Screen.Bus(), "Metro" to Screen.Metro(), "Cerca de mí" to Screen.Nearby,
                    "Mapa" to Screen.Map, "Cómo llegar" to Screen.Planner, "Avisos de servicio" to Screen.Alerts(),
                    "Gestión de favoritos" to Screen.Manage, "Ajustes" to Screen.Settings,
                ).forEach { (label, screen) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; model.open(screen) })
                }
            }
        })
    }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; tick++ },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (model.favorites.isEmpty()) {
                    item {
                        if (model.credentials.isComplete) {
                            Empty("Sin favoritos", "Abre el menú ⋮ y elige «Gestión de favoritos», o consulta una parada y añádela.")
                        } else {
                            Empty("Faltan las credenciales de TMB", "Abre el menú ⋮ y elige «Ajustes».")
                        }
                    }
                }
                itemsIndexed(model.favorites, key = { _, f -> f.key }) { _, f ->
                    FavoriteRow(f, states[f.key], model)
                    HorizontalDivider()
                }
                if (model.favorites.isNotEmpty()) item { DataSource(updated) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteRow(f: Favorite, state: FavState?, model: AppModel) {
    var menu by remember { mutableStateOf(false) }
    val alerts = model.alertsFor(f)
    Box {
        Column(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = { model.openFavorite(f) }, onLongClick = { menu = true })
                .padding(16.dp, 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (f) {
                    is MetroFavorite -> LineBadge(f.lineName, parseColor(f.color))
                    is BusFavorite -> LineBadge(if (f.line.isEmpty()) "BUS" else "BUS ${f.line}", BusRed)
                }
                Spacer(Modifier.width(8.dp))
                Text(f.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(4.dp))
            state?.rows?.forEach { row ->
                Row {
                    Text(
                        row.label, Modifier.width(44.dp), fontWeight = FontWeight.Bold,
                        color = if (row.color.isEmpty()) BusRed else parseColor(row.color),
                    )
                    Text(row.text, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (alerts.isNotEmpty()) {
                Text(
                    "⚠ " + (if (alerts.size == 1) "1 aviso de servicio" else "${alerts.size} avisos de servicio") + " · mantén pulsado",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                )
            }
            val status = when {
                !model.credentials.isComplete -> ""
                state == null -> "Cargando…"
                state.error.isNotEmpty() -> state.error
                state.loaded && state.rows.isEmpty() -> "Sin servicio previsto"
                else -> ""
            }
            if (status.isNotEmpty()) {
                Text(
                    status, style = MaterialTheme.typography.bodySmall,
                    color = if (state?.error?.isNotEmpty() == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (alerts.isNotEmpty()) {
                DropdownMenuItem(text = { Text("Ver avisos de servicio") }, onClick = { menu = false; model.open(Screen.Alerts(f)) })
            }
            DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; model.removeFavorite(f) })
        }
    }
}

@Composable
fun BusScreen(model: AppModel, initial: Screen.Bus) {
    var stop by remember { mutableStateOf(initial.stop) }
    var line by remember { mutableStateOf(initial.line) }
    var name by remember { mutableStateOf("") }
    var forecast by remember { mutableStateOf<List<BusArrival>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var queried by remember { mutableStateOf<Pair<String, String>?>(null) }
    var updated by remember { mutableStateOf<Long?>(null) }
    var request by remember { mutableIntStateOf(if (initial.stop.isNotEmpty()) 1 else 0) }
    var favorite by remember { mutableStateOf<BusFavorite?>(null) }

    LaunchedEffect(request) {
        if (request == 0 || stop.isBlank() || !model.credentials.isComplete) return@LaunchedEffect
        val s = stop.trim()
        val l = line.trim().uppercase()
        while (true) {
            loading = true
            try {
                forecast = Api.busForecast(s, l, model.credentials)
                error = ""
                updated = System.currentTimeMillis()
                if (queried?.first != s) name = try { Api.stopName(s, model.credentials) ?: "" } catch (e: ApiException) { "" }
                queried = s to l
                favorite = BusFavorite(s, l, name.ifEmpty { "Parada $s" })
            } catch (e: ApiException) {
                error = e.message ?: ""
                forecast = emptyList()
                queried = s to l
            }
            loading = false
            delay(REFRESH_MILLIS)
        }
    }

    Scaffold(topBar = {
        Bar(name.ifEmpty { "Bus" }, onBack = { model.back() }, actions = {
            val fav = favorite
            IconButton(enabled = fav != null && !model.isFavorite(fav), onClick = { fav?.let(model::addFavorite) }) {
                Icon(Icons.Default.Star, "Añadir a favoritos")
            }
            IconButton(onClick = { request++ }) { Icon(Icons.Default.Refresh, "Actualizar") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    stop, { stop = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Parada") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    line, { line = it }, Modifier.weight(1f), label = { Text("Línea (opcional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { request++ }),
                )
            }
            Button(
                onClick = { request++ }, enabled = stop.isNotBlank() && model.credentials.isComplete,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Consultar") }
            Hint(if (model.credentials.isComplete) "" else "Faltan las credenciales de TMB: ábrelas en Ajustes.")
            Hint(error, error = true)
            if (queried != null && !loading && error.isEmpty() && forecast.isEmpty()) Hint("No hay buses previstos en esta parada")
            if (loading && forecast.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
            forecast.forEach { a ->
                ListItem(
                    leadingContent = { LineBadge(a.line, BusRed) },
                    headlineContent = { Text(a.destination, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        Text(
                            a.minutes?.let(::minutesText) ?: a.text, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                HorizontalDivider()
            }
            if (error.isEmpty() && forecast.isNotEmpty()) DataSource(updated)
        }
    }
}

/** A row that shows a value and opens a list to choose it. */
@Composable
private fun PickerRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
    HorizontalDivider()
}

@Composable
private fun <T> PickerDialog(title: String, items: List<T>, text: @Composable (T) -> Unit, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            LazyColumn {
                itemsIndexed(items) { _, item ->
                    Box(Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 12.dp)) { text(item) }
                }
            }
        },
    )
}

/** Choosing a metro line and then a station, shared by the Metro and Add metro screens. */
@Composable
private fun LineStationPicker(
    model: AppModel,
    initialLine: String,
    initialStation: String,
    onError: (String) -> Unit,
    onChange: (MetroLine?, MetroStation?) -> Unit,
) {
    var lines by remember { mutableStateOf<List<MetroLine>>(emptyList()) }
    var stations by remember { mutableStateOf<List<MetroStation>>(emptyList()) }
    var line by remember { mutableStateOf<MetroLine?>(null) }
    var station by remember { mutableStateOf<MetroStation?>(null) }
    var pickLine by remember { mutableStateOf(false) }
    var pickStation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun selectLine(l: MetroLine, keepStation: String) {
        line = l
        station = null
        stations = emptyList()
        onChange(l, null)
        scope.launch {
            try {
                stations = Api.metroStations(l.code, model.credentials)
                stations.firstOrNull { it.code == keepStation }?.let {
                    station = it
                    onChange(l, it)
                }
            } catch (e: ApiException) {
                onError(e.message ?: "")
            }
        }
    }

    LaunchedEffect(model.credentials) {
        if (!model.credentials.isComplete) return@LaunchedEffect
        try {
            lines = Api.metroLines(model.credentials)
            lines.firstOrNull { it.code == initialLine }?.let { selectLine(it, initialStation) }
        } catch (e: ApiException) {
            onError(e.message ?: "")
        }
    }

    PickerRow(
        "Línea",
        line?.let { "${it.name} · ${it.description}" } ?: if (lines.isEmpty()) "Cargando…" else "Elige línea",
        lines.isNotEmpty(),
    ) { pickLine = true }
    PickerRow(
        "Estación",
        station?.name ?: when {
            line == null -> "Elige antes la línea"
            stations.isEmpty() -> "Cargando…"
            else -> "Elige estación"
        },
        stations.isNotEmpty(),
    ) { pickStation = true }

    if (pickLine) {
        PickerDialog("Línea", lines, { l ->
            Column {
                Text(l.name, color = parseColor(l.color), fontWeight = FontWeight.Bold)
                Text(l.description, style = MaterialTheme.typography.bodySmall)
            }
        }, { pickLine = false; selectLine(it, "") }, { pickLine = false })
    }
    if (pickStation) {
        PickerDialog("Estación", stations, { Text(it.name) }, {
            pickStation = false
            station = it
            onChange(line, it)
        }, { pickStation = false })
    }
}

@Composable
fun MetroScreen(model: AppModel, initial: Screen.Metro) {
    var line by remember { mutableStateOf<MetroLine?>(null) }
    var station by remember { mutableStateOf<MetroStation?>(null) }
    var forecast by remember { mutableStateOf<List<MetroDirection>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var queried by remember { mutableStateOf(false) }
    var updated by remember { mutableStateOf<Long?>(null) }
    var request by remember { mutableIntStateOf(0) }

    LaunchedEffect(station, request) {
        val s = station ?: return@LaunchedEffect
        forecast = forecast.takeIf { queried && request > 0 } ?: emptyList()
        while (true) {
            loading = true
            try {
                forecast = Api.metroForecast(s.code, model.credentials)
                error = ""
                updated = System.currentTimeMillis()
            } catch (e: ApiException) {
                error = e.message ?: ""
                forecast = emptyList()
            }
            queried = true
            loading = false
            delay(REFRESH_MILLIS)
        }
    }
    val l = line
    val s = station
    val favorite = if (l != null && s != null) MetroFavorite(l.code, l.name, l.color, s.code, s.name) else null

    Scaffold(topBar = {
        Bar(s?.name ?: "Metro", onBack = { model.back() }, actions = {
            IconButton(enabled = favorite != null && !model.isFavorite(favorite), onClick = { favorite?.let(model::addFavorite) }) {
                Icon(Icons.Default.Star, "Añadir a favoritos")
            }
            IconButton(onClick = { request++ }) { Icon(Icons.Default.Refresh, "Actualizar") }
        })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            LineStationPicker(model, initial.line, initial.station, { error = it }) { newLine, newStation ->
                line = newLine
                station = newStation
                queried = false
                if (newStation == null) forecast = emptyList()
            }
            Hint(if (model.credentials.isComplete) "" else "Faltan las credenciales de TMB: ábrelas en Ajustes.")
            Hint(error, error = true)
            if (queried && !loading && error.isEmpty() && forecast.isEmpty()) Hint("No hay trenes previstos")
            if (loading && forecast.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
            forecast.forEach { d ->
                ListItem(
                    leadingContent = { LineBadge(d.line, parseColor(d.color)) },
                    overlineContent = { Text("Dirección ${d.destination}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    headlineContent = {
                        Text(
                            if (d.minutes.isEmpty()) "Sin trenes" else minutesList(d.minutes),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                HorizontalDivider()
            }
            if (error.isEmpty() && forecast.isNotEmpty()) DataSource(updated)
        }
    }
}

@Composable
fun ManageScreen(model: AppModel) {
    var confirm by remember { mutableStateOf<Favorite?>(null) }
    Scaffold(topBar = { Bar("Gestión de favoritos", onBack = { model.back() }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                ) {
                    Button(onClick = { model.open(Screen.AddBus) }) {
                        Icon(Icons.Default.Add, null)
                        Text("Bus")
                    }
                    Button(onClick = { model.open(Screen.AddMetro) }) {
                        Icon(Icons.Default.Add, null)
                        Text("Metro")
                    }
                }
                if (model.favorites.isEmpty()) Hint("Sin favoritos. Añade una parada de bus o una estación de metro.")
            }
            itemsIndexed(model.favorites, key = { _, f -> f.key }) { index, f ->
                ListItem(
                    headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            when (f) {
                                is MetroFavorite -> "Metro ${f.lineName}"
                                is BusFavorite -> "Bus · parada ${f.stop}" + if (f.line.isEmpty()) " · todas las líneas" else " · línea ${f.line}"
                            },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(enabled = index > 0, onClick = { model.moveFavorite(index, index - 1) }) {
                                Icon(Icons.Default.KeyboardArrowUp, "Subir")
                            }
                            IconButton(enabled = index < model.favorites.lastIndex, onClick = { model.moveFavorite(index, index + 1) }) {
                                Icon(Icons.Default.KeyboardArrowDown, "Bajar")
                            }
                            IconButton(onClick = { confirm = f }) {
                                Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
    confirm?.let { f ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("¿Eliminar favorito?") },
            text = { Text(f.name) },
            confirmButton = { TextButton(onClick = { model.removeFavorite(f); confirm = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun AddBusScreen(model: AppModel) {
    var stop by remember { mutableStateOf("") }
    var line by remember { mutableStateOf("") }
    var name by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf("") }

    // Check the stop exists once typing pauses.
    LaunchedEffect(stop) {
        name = null
        checked = ""
        if (stop.isBlank()) return@LaunchedEffect
        delay(600)
        name = try { Api.stopName(stop.trim(), model.credentials) } catch (e: ApiException) { null }
        checked = stop.trim()
    }
    val favorite = name?.let { BusFavorite(stop.trim(), line.trim().uppercase(), it) }
    val can = favorite != null && checked == stop.trim() && !model.isFavorite(favorite)

    Scaffold(topBar = { Bar("Añadir bus", onBack = { model.back() }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                stop, { stop = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Código de parada") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                line, { line = it }, Modifier.fillMaxWidth(), label = { Text("Línea (vacío = todas)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            val status = when {
                stop.isBlank() -> ""
                checked != stop.trim() -> "Buscando la parada…"
                name == null -> "No existe ninguna parada con ese código"
                favorite != null && model.isFavorite(favorite) -> "$name (ya está en favoritos)"
                else -> name ?: ""
            }
            if (status.isNotEmpty()) {
                Text(status, color = if (name == null && checked.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            Button(onClick = { favorite?.let(model::addFavorite); model.back() }, enabled = can, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Añadir a favoritos")
            }
        }
    }
}

@Composable
fun AddMetroScreen(model: AppModel) {
    var line by remember { mutableStateOf<MetroLine?>(null) }
    var station by remember { mutableStateOf<MetroStation?>(null) }
    var error by remember { mutableStateOf("") }
    val l = line
    val s = station
    val favorite = if (l != null && s != null) MetroFavorite(l.code, l.name, l.color, s.code, s.name) else null

    Scaffold(topBar = { Bar("Añadir metro", onBack = { model.back() }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxWidth()) {
            LineStationPicker(model, "", "", { error = it }) { newLine, newStation ->
                line = newLine
                station = newStation
            }
            Hint(error, error = true)
            if (favorite != null && model.isFavorite(favorite)) Hint("Ya está en favoritos")
            Button(
                onClick = { favorite?.let(model::addFavorite); model.back() },
                enabled = favorite != null && !model.isFavorite(favorite),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
            ) { Text("Añadir a favoritos") }
        }
    }
}
