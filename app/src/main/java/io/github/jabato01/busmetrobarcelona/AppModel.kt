package io.github.jabato01.busmetrobarcelona

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jabato01.busmetrobarcelona.data.Api
import io.github.jabato01.busmetrobarcelona.data.ApiException
import io.github.jabato01.busmetrobarcelona.data.BusFavorite
import io.github.jabato01.busmetrobarcelona.data.Credentials
import io.github.jabato01.busmetrobarcelona.data.Favorite
import io.github.jabato01.busmetrobarcelona.data.Itinerary
import io.github.jabato01.busmetrobarcelona.data.MetroFavorite
import io.github.jabato01.busmetrobarcelona.data.Place
import io.github.jabato01.busmetrobarcelona.data.ServiceAlert
import io.github.jabato01.busmetrobarcelona.data.Store
import kotlinx.coroutines.launch

/** The app's screens; the top of the stack is the one shown. */
sealed interface Screen {
    data object Favorites : Screen
    data class Bus(val stop: String = "", val line: String = "") : Screen
    data class Metro(val line: String = "", val station: String = "") : Screen
    data object Nearby : Screen
    data object Map : Screen
    data object Manage : Screen
    data object AddBus : Screen
    data object AddMetro : Screen
    data class Alerts(val favorite: Favorite? = null) : Screen
    data object Planner : Screen
    data class Trip(val itinerary: Itinerary, val updated: Long, val origin: String, val destination: String) : Screen
    data class RouteMap(val itinerary: Itinerary, val updated: Long) : Screen
    data object Settings : Screen
}

/** Origin or destination of a planned trip. */
sealed interface PlannerPlace {
    data object MyLocation : PlannerPlace
    data class Fixed(val place: Place) : PlannerPlace
}

class AppModel(application: Application) : AndroidViewModel(application) {
    private val store = Store(application)

    val stack = mutableStateListOf<Screen>(Screen.Favorites)
    fun open(screen: Screen) { stack.add(screen) }
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    var credentials by mutableStateOf(store.credentials)
        private set
    var geocoder by mutableStateOf(store.geocoder)
        private set

    fun saveSettings(creds: Credentials, provider: String) {
        val changed = creds != credentials
        store.credentials = creds
        store.geocoder = provider
        credentials = creds
        geocoder = provider
        if (changed) loadAlerts()
    }

    // --- Favourites ---
    val favorites = mutableStateListOf<Favorite>().apply { addAll(store.favorites) }

    private fun saveFavorites() { store.favorites = favorites.toList() }

    fun isFavorite(f: Favorite) = favorites.any { it.key == f.key }

    fun addFavorite(f: Favorite) {
        if (!isFavorite(f)) {
            favorites.add(f)
            saveFavorites()
        }
    }

    fun removeFavorite(f: Favorite) {
        favorites.removeAll { it.key == f.key }
        saveFavorites()
    }

    fun moveFavorite(from: Int, to: Int) {
        favorites.add(to, favorites.removeAt(from))
        saveFavorites()
    }

    fun openFavorite(f: Favorite) = when (f) {
        is BusFavorite -> open(Screen.Bus(f.stop, f.line))
        is MetroFavorite -> open(Screen.Metro(f.line, f.station))
    }

    // --- Recent places ---
    var recentPlaces by mutableStateOf(store.recentPlaces)
        private set

    fun addRecentPlace(p: Place) {
        recentPlaces = (listOf(p) + recentPlaces.filter { it.lat != p.lat || it.lon != p.lon }).take(8)
        store.recentPlaces = recentPlaces
    }

    // --- Planner state, kept here so it survives choosing a place or opening a trip ---
    var plannerOrigin by mutableStateOf<PlannerPlace>(PlannerPlace.MyLocation)
    var plannerDestination by mutableStateOf<PlannerPlace?>(null)
    var plannerItineraries by mutableStateOf<List<Itinerary>>(emptyList())
    var plannerNext by mutableStateOf("")
    var plannerUpdated by mutableStateOf(0L)

    // --- Service alerts ---
    var alerts by mutableStateOf<List<ServiceAlert>>(emptyList())
        private set
    var alertsUpdated by mutableStateOf<Long?>(null)
        private set
    var alertsError by mutableStateOf("")
        private set
    private var alertsLoading = false

    fun loadAlerts() {
        if (!credentials.isComplete || alertsLoading) return
        alertsLoading = true
        viewModelScope.launch {
            try {
                alerts = Api.alerts(credentials)
                alertsUpdated = System.currentTimeMillis()
                alertsError = ""
            } catch (e: ApiException) {
                alertsError = e.message ?: ""
            }
            alertsLoading = false
        }
    }

    /** Alerts affecting a favourite: its metro station (or whole line), or its bus stop or line. */
    fun alertsFor(f: Favorite): List<ServiceAlert> = alerts.filter { a ->
        when (f) {
            is MetroFavorite -> a.isMetro && f.lineName in a.lines && (a.stations.isEmpty() || a.stations.any { it.code == f.station })
            is BusFavorite -> !a.isMetro && (f.stop in a.stops || (f.line.isNotEmpty() && f.line in a.lines))
        }
    }

    init {
        loadAlerts()
    }
}
