package io.github.jabato01.busmetrobarcelona.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ApiException(message: String) : Exception(message)

data class Credentials(val appId: String, val appKey: String) {
    val isComplete get() = appId.isNotEmpty() && appKey.isNotEmpty()
}

data class BusArrival(val line: String, val destination: String, val minutes: Int?, val text: String)
data class MetroLine(val code: String, val name: String, val color: String, val order: Int, val description: String)
data class MetroStation(val code: String, val name: String, val order: Int)
data class MetroDirection(val line: String, val color: String, val destination: String, val minutes: List<Int>)
data class StopLine(val name: String, val color: String, val order: Int)

sealed interface NearbyStop {
    val name: String
    val lat: Double
    val lon: Double
    val distance: Double
    val dataDate: String
}

data class NearbyBus(
    val stop: String, override val name: String, override val lat: Double, override val lon: Double,
    override val distance: Double, override val dataDate: String, val lines: List<StopLine>,
) : NearbyStop

data class NearbyMetro(
    val station: String, override val name: String, override val lat: Double, override val lon: Double,
    override val distance: Double, override val dataDate: String,
    val line: String, val lineName: String, val color: String,
) : NearbyStop

data class AlertStation(val line: String, val code: String, val name: String)
data class ServiceAlert(
    val isMetro: Boolean, val lines: List<String>, val stations: List<AlertStation>, val stops: List<String>,
    val title: String, val text: String, val period: String,
)

data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

data class PlanEnd(val name: String, val stopCode: String)
data class Leg(
    val mode: String, val modeName: String, val line: String, val color: String, val textColor: String,
    val headsign: String, val agency: String, val from: PlanEnd, val to: PlanEnd,
    val start: Long, val end: Long, val duration: Int, val distance: Double, val stops: Int, val points: String,
) {
    val isWalk get() = mode == "WALK"
}
data class Itinerary(val start: Long, val end: Long, val duration: Int, val walkTime: Int, val transfers: Int, val legs: List<Leg>)
data class PlanResult(val itineraries: List<Itinerary>, val next: String)
data class PlanTime(val arriveBy: Boolean, val date: Date)

/** Network access: TMB's API plus the ICGC and Photon address search. Keys only ever go to api.tmb.cat. */
object Api {
    private const val BASE_URL = "https://api.tmb.cat/v1"
    private const val USER_AGENT = "BusMetroBarcelona (Android; github.com/Jabato01/bus-metro-barcelona)"

    // Area the place search is limited to: Barcelona and its metropolitan area.
    private const val WEST = 1.95
    private const val SOUTH = 41.27
    private const val EAST = 2.32
    private const val NORTH = 41.50

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private suspend fun fetch(url: String, service: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", USER_AGENT)
            try {
                val code = c.responseCode
                val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                code to body
            } finally {
                c.disconnect()
            }
        } catch (e: IOException) {
            throw ApiException("Sin conexión con $service")
        }
    }

    private suspend fun tmb(path: String, creds: Credentials, query: String = ""): JSONObject {
        val url = "$BASE_URL/$path?app_id=${enc(creds.appId)}&app_key=${enc(creds.appKey)}" + if (query.isEmpty()) "" else "&$query"
        val (code, body) = fetch(url, "TMB")
        when {
            code == 200 -> Unit
            code == 401 || code == 403 -> throw ApiException("Credenciales rechazadas. Revisa APP_ID y APP_KEY en Ajustes.")
            else -> throw ApiException("Error $code de TMB")
        }
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw ApiException("Respuesta no válida de TMB")
        }
    }

    private suspend fun geo(url: String): JSONObject {
        val (code, body) = fetch(url, "el buscador")
        if (code != 200) throw ApiException("Error $code del buscador")
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw ApiException("Respuesta no válida del buscador")
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

    private fun JSONObject.features() = optJSONArray("features").objects()
    private fun JSONObject.props() = optJSONObject("properties") ?: JSONObject()
    private fun JSONObject.lonLat(): Pair<Double, Double>? {
        val c = optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        return c.optDouble(0) to c.optDouble(1)
    }

    /** Next buses at a stop; with no line, those of every line through it. */
    suspend fun busForecast(stop: String, line: String, creds: Credentials): List<BusArrival> {
        val path = if (line.isEmpty()) "ibus/stops/${enc(stop)}" else "ibus/lines/${enc(line)}/stops/${enc(stop)}"
        return tmb(path, creds).optJSONObject("data")?.optJSONArray("ibus").objects().map {
            BusArrival(
                line = it.str("line").ifEmpty { line },
                destination = it.str("destination"),
                minutes = if (it.has("t-in-min") && !it.isNull("t-in-min")) it.optInt("t-in-min") else null,
                text = it.str("text-ca"),
            )
        }
    }

    suspend fun metroLines(creds: Credentials): List<MetroLine> =
        tmb("transit/linies/metro", creds).features().map {
            val p = it.props()
            MetroLine(p.str("CODI_LINIA"), p.str("NOM_LINIA"), "#" + p.str("COLOR_LINIA"), p.optInt("ORDRE_LINIA"),
                p.str("ORIGEN_LINIA") + " – " + p.str("DESTI_LINIA"))
        }.sortedBy { it.order }

    suspend fun metroStations(lineCode: String, creds: Credentials): List<MetroStation> =
        tmb("transit/linies/metro/${enc(lineCode)}/estacions", creds).features().map {
            val p = it.props()
            MetroStation(p.str("CODI_ESTACIO"), p.str("NOM_ESTACIO"), p.optInt("ORDRE_ESTACIO"))
        }.sortedBy { it.order }

    /** Next trains at a station, one entry per direction of each line. */
    suspend fun metroForecast(station: String, creds: Credentials): List<MetroDirection> {
        val json = tmb("itransit/metro/estacions", creds, "estacions=${enc(station)}")
        val now = json.optLong("timestamp")
        val result = mutableListOf<MetroDirection>()
        json.optJSONArray("linies").objects().forEach { linia ->
            linia.optJSONArray("estacions").objects().forEach { platform ->
                platform.optJSONArray("linies_trajectes").objects().forEach { route ->
                    result += MetroDirection(
                        route.str("nom_linia"), "#" + route.str("color_linia"), route.str("desti_trajecte"),
                        route.optJSONArray("propers_trens").objects().map {
                            max(0.0, floor((it.optLong("temps_arribada") - now) / 60000.0)).toInt()
                        },
                    )
                }
            }
        }
        return result
    }

    suspend fun stopName(stop: String, creds: Credentials): String? =
        tmb("transit/parades/${enc(stop)}", creds).features().firstOrNull()?.props()?.str("NOM_PARADA")?.ifEmpty { null }

    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rad = Math.PI / 180
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2) * sin(dLon / 2)
        return 6371000 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Bus stops and metro stations within `radius` metres, closest first. */
    suspend fun nearby(lat: Double, lon: Double, radius: Double, creds: Credentials): List<NearbyStop> = coroutineScope {
        val dLat = radius / 111320
        val dLon = radius / (111320 * cos(lat * Math.PI / 180))
        val bbox = "bbox=${lon - dLon},${lat - dLat},${lon + dLon},${lat + dLat},EPSG:4326"
        val stopsCall = async { tmb("transit/parades", creds, "$bbox&propertyName=CODI_PARADA,NOM_PARADA,DATA,GEOMETRY").features() }
        val linesCall = async { tmb("transit/linies/bus/parades", creds, "$bbox&propertyName=CODI_PARADA,NOM_LINIA,COLOR_REC,ORDRE_LINIA").features() }
        val stationsCall = async {
            tmb("transit/linies/metro/estacions", creds, "$bbox&propertyName=CODI_ESTACIO,NOM_ESTACIO,CODI_LINIA,NOM_LINIA,COLOR_LINIA,DATA,GEOMETRY").features()
        }
        val stops = stopsCall.await()
        val stations = stationsCall.await()
        val linesByStop = linesCall.await().map { it.props() }.groupBy({ it.str("CODI_PARADA") }) {
            StopLine(it.str("NOM_LINIA"), "#" + it.str("COLOR_REC"), it.optInt("ORDRE_LINIA"))
        }.mapValues { (_, lines) -> lines.distinctBy { it.name }.sortedBy { it.order } }
        val dataDate = (stops + stations).map { it.props().str("DATA") }.maxOrNull() ?: ""

        val result = mutableListOf<NearbyStop>()
        stops.forEach { f ->
            val p = f.props()
            val (x, y) = f.lonLat() ?: return@forEach
            val code = p.str("CODI_PARADA")
            result += NearbyBus(code, p.str("NOM_PARADA"), y, x, distance(lat, lon, y, x), dataDate, linesByStop[code] ?: emptyList())
        }
        stations.forEach { f ->
            val p = f.props()
            val (x, y) = f.lonLat() ?: return@forEach
            result += NearbyMetro(p.str("CODI_ESTACIO"), p.str("NOM_ESTACIO"), y, x, distance(lat, lon, y, x), dataDate,
                p.str("CODI_LINIA"), p.str("NOM_LINIA"), "#" + p.str("COLOR_LINIA"))
        }
        result.filter { it.distance <= radius }.sortedBy { it.distance }
    }

    private fun plainText(html: String) = html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun activeNow(begin: Long, end: Long, now: Long) = (begin == 0L || begin <= now) && (end == 0L || now <= end)

    /** Current metro and bus service alerts. */
    suspend fun alerts(creds: Credentials): List<ServiceAlert> = coroutineScope {
        val now = System.currentTimeMillis()
        val metroCall = async { tmb("alerts/metro/channels/WEB", creds).optJSONObject("data")?.optJSONArray("alerts").objects() }
        val busCall = async { tmb("alerts/bus/channels/WEB", creds).optJSONObject("data")?.optJSONArray("alerts").objects() }
        val result = mutableListOf<ServiceAlert>()
        metroCall.await().forEach { a ->
            val active = a.optJSONArray("disruption_dates").objects().any { activeNow(it.optLong("begin_date"), it.optLong("end_date"), now) }
            val pub = a.optJSONArray("publications").objects().firstOrNull()
            if (!active || pub == null) return@forEach
            val entities = a.optJSONArray("entities").objects()
            val stations = entities.filter { it.str("station_code") != "ALL" }
                .map { AlertStation(it.str("line_name"), it.str("station_code"), it.str("station_name")) }
            result += ServiceAlert(true, entities.map { it.str("line_name") }.distinct(), stations, emptyList(),
                stations.firstOrNull()?.name ?: "Toda la línea",
                plainText(pub.str("textEs").ifEmpty { pub.str("textCa") }),
                plainText(pub.str("headerEs").ifEmpty { pub.str("headerCa") }))
        }
        busCall.await().forEach { a ->
            val info = a.optJSONObject("channelInfoTO") ?: JSONObject()
            if (!activeNow(a.optLong("begin"), a.optLong("end"), now) || !info.optBoolean("publish", true)) return@forEach
            val affected = a.optJSONArray("linesAffected").objects()
            val stops = affected.flatMap { l ->
                l.optJSONArray("ways").objects().flatMap { w -> w.optJSONArray("stops").objects().map { it.str("stopId") } }
            }.distinct()
            result += ServiceAlert(false, affected.map { it.str("commercialLineId") }.distinct(), emptyList(), stops,
                a.str("causeName").ifEmpty { "Aviso" },
                plainText(info.str("textEs").ifEmpty { info.str("textCa") }),
                if (a.optLong("begin") > 0) "Desde el " + dateText(a.optLong("begin")) else "")
        }
        result
    }

    /** OpenStreetMap data via Photon: good for named places, rarely knows house numbers. */
    private suspend fun geocodeOsm(text: String): List<Place> =
        geo("https://photon.komoot.io/api/?limit=15&bbox=$WEST,$SOUTH,$EAST,$NORTH&lat=41.3888&lon=2.1590&q=${enc(text)}").features().mapNotNull { f ->
            val p = f.props()
            val (x, y) = f.lonLat() ?: return@mapNotNull null
            val street = p.str("street").let { if (it.isEmpty()) "" else it + p.str("housenumber").let { n -> if (n.isEmpty()) "" else " $n" } }
            val name = p.str("name").ifEmpty { street.ifEmpty { p.str("city").ifEmpty { "Sin nombre" } } }
            val detail = listOf(if (name == street) "" else street, p.str("district").ifEmpty { p.str("locality") }, p.str("city"))
                .filter { it.isNotEmpty() }.joinToString(", ")
            Place(name, detail, y, x)
        }

    /** Institut Cartogràfic i Geològic de Catalunya: official street addresses with numbers. */
    private suspend fun geocodeIcgc(text: String): List<Place> =
        geo("https://eines.icgc.cat/geocodificador/cerca?size=20&text=${enc(text)}").features().mapNotNull { f ->
            val p = f.props()
            val (x, y) = f.lonLat() ?: return@mapNotNull null
            if (y !in SOUTH..NORTH || x !in WEST..EAST) return@mapNotNull null
            val label = p.str("etiqueta").ifEmpty { p.str("nom") }.replace(Regex(" - \\[[^\\]]*\\]$"), "")
            val town = p.str("municipi")
            Place(label.removeSuffix(", $town"), town, y, x)
        }

    /** Place search. provider: "auto" (both, ICGC first when the text has a number), "icgc" or "osm". */
    suspend fun geocode(input: String, provider: String): List<Place> = coroutineScope {
        val text = input.replace(Regex("[´`’‘]"), "'")
        var error: ApiException? = null
        val icgcCall = async {
            if (provider == "osm") emptyList() else try { geocodeIcgc(text) } catch (e: ApiException) { error = e; emptyList() }
        }
        val osmCall = async {
            if (provider == "icgc") emptyList() else try { geocodeOsm(text) } catch (e: ApiException) { error = e; emptyList() }
        }
        val icgc = icgcCall.await()
        val osm = osmCall.await()
        if (icgc.isEmpty() && osm.isEmpty()) error?.let { throw it }
        val ordered = if (text.any { it.isDigit() }) icgc + osm else osm + icgc
        ordered.distinctBy { (it.name + "|" + it.detail).lowercase(Locale.ROOT) }.take(15)
    }

    private val MODES = mapOf(
        "WALK" to "A pie", "BUS" to "Bus", "SUBWAY" to "Metro", "RAIL" to "Tren", "TRAM" to "Tranvía",
        "FUNICULAR" to "Funicular", "GONDOLA" to "Teleférico", "CABLE_CAR" to "Teleférico", "FERRY" to "Barco",
    )

    /** Journey planner. With no [time], leave now; [cursor] pages to more results. */
    suspend fun plan(from: Pair<Double, Double>, to: Pair<Double, Double>, time: PlanTime?, cursor: String, creds: Credentials): PlanResult {
        var query = "mode=TRANSIT,WALK&numItineraries=5&showIntermediateStops=true" +
            "&fromPlace=${from.first},${from.second}&toPlace=${to.first},${to.second}"
        if (cursor.isNotEmpty()) {
            query += "&pageCursor=${enc(cursor)}"
        } else if (time != null) {
            val date = SimpleDateFormat("MM-dd-yyyy", Locale.ROOT).format(time.date)
            val clock = SimpleDateFormat("h:mmaa", Locale.ENGLISH).format(time.date).lowercase(Locale.ROOT)
            query += "&date=$date&time=$clock&arriveBy=${time.arriveBy}"
        }
        val json = tmb("planner/plan", creds, query)
        json.optJSONObject("error")?.let {
            throw ApiException(if (it.str("msg") == "PATH_NOT_FOUND" || it.optInt("id") == 404) "No se ha encontrado ninguna ruta"
            else it.str("message").ifEmpty { "El planificador no ha podido calcular la ruta" })
        }
        val raw = json.optJSONObject("plan")?.optJSONArray("itineraries").objects()
        if (raw.isEmpty() && cursor.isEmpty()) throw ApiException("No se ha encontrado ninguna ruta")
        fun end(o: JSONObject?, fallback: Pair<String, String>): PlanEnd {
            val name = o?.str("name") ?: ""
            return PlanEnd(if (name == fallback.first) fallback.second else name, o?.str("stopCode") ?: "")
        }
        return PlanResult(raw.map { it2 ->
            Itinerary(it2.optLong("startTime"), it2.optLong("endTime"), it2.optInt("duration"), it2.optInt("walkTime"), it2.optInt("transfers"),
                it2.optJSONArray("legs").objects().map { l ->
                    val mode = l.str("mode")
                    Leg(mode, MODES[mode] ?: mode, l.str("routeShortName"),
                        l.str("routeColor").let { c -> if (c.isEmpty()) "" else "#$c" },
                        l.str("routeTextColor").let { c -> if (c.isEmpty()) "#FFFFFF" else "#$c" },
                        l.str("headsign"), l.str("agencyName"),
                        end(l.optJSONObject("from"), "Origin" to "Origen"), end(l.optJSONObject("to"), "Destination" to "Destino"),
                        l.optLong("startTime"), l.optLong("endTime"), l.optInt("duration"), l.optDouble("distance", 0.0),
                        (l.optJSONArray("intermediateStops")?.length() ?: 0) + 1,
                        l.optJSONObject("legGeometry")?.str("points") ?: "")
                })
        }, json.str("nextPageCursor"))
    }

    /** Google encoded polyline -> list of (lat, lon). */
    fun decodePolyline(str: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < str.length) {
            val values = IntArray(2)
            for (k in 0..1) {
                var result = 0
                var shift = 0
                var b: Int
                do {
                    b = str[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                values[k] = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            }
            lat += values[0]
            lon += values[1]
            points += lat / 1e5 to lon / 1e5
        }
        return points
    }

    fun clockText(ms: Long): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(ms))
    fun dateText(ms: Long): String = SimpleDateFormat("d/M/yyyy", Locale.ROOT).format(Date(ms))

    // TMB's licence requires citing the source and the date of the last update wherever its data is shown.
    /** Real-time data: when it was received. */
    fun sourceText(updated: Long): String {
        val a = Calendar.getInstance().apply { timeInMillis = updated }
        val b = Calendar.getInstance()
        val today = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return "Datos: TMB · actualizado " + (if (today) "" else "el ${dateText(updated)} ") + "a las ${clockText(updated)}"
    }

    /** Stops and stations: TMB's own date for the data, e.g. "2026-10-30Z". */
    fun staticSourceText(dataDate: String): String {
        val p = dataDate.take(10).split("-")
        return if (p.size == 3) "Datos: TMB · actualizados el ${p[2].toInt()}/${p[1].toInt()}/${p[0]}" else "Datos: TMB"
    }
}
