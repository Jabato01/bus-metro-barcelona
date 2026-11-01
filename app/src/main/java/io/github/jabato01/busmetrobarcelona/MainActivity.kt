package io.github.jabato01.busmetrobarcelona

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.key
import io.github.jabato01.busmetrobarcelona.ui.AddBusScreen
import io.github.jabato01.busmetrobarcelona.ui.AddMetroScreen
import io.github.jabato01.busmetrobarcelona.ui.AlertsScreen
import io.github.jabato01.busmetrobarcelona.ui.BusScreen
import io.github.jabato01.busmetrobarcelona.ui.FavoritesScreen
import io.github.jabato01.busmetrobarcelona.ui.ManageScreen
import io.github.jabato01.busmetrobarcelona.ui.MapScreen
import io.github.jabato01.busmetrobarcelona.ui.MetroScreen
import io.github.jabato01.busmetrobarcelona.ui.NearbyScreen
import io.github.jabato01.busmetrobarcelona.ui.PlannerScreen
import io.github.jabato01.busmetrobarcelona.ui.RouteMapScreen
import io.github.jabato01.busmetrobarcelona.ui.SettingsScreen
import io.github.jabato01.busmetrobarcelona.ui.TripScreen

class MainActivity : ComponentActivity() {
    private val model: AppModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface {
                    BackHandler(enabled = model.stack.size > 1) { model.back() }
                    // One composition per stack level, so a screen starts fresh when opened.
                    key(model.stack.size, model.stack.last()) {
                        when (val screen = model.stack.last()) {
                            Screen.Favorites -> FavoritesScreen(model)
                            is Screen.Bus -> BusScreen(model, screen)
                            is Screen.Metro -> MetroScreen(model, screen)
                            Screen.Nearby -> NearbyScreen(model)
                            Screen.Map -> MapScreen(model)
                            Screen.Manage -> ManageScreen(model)
                            Screen.AddBus -> AddBusScreen(model)
                            Screen.AddMetro -> AddMetroScreen(model)
                            is Screen.Alerts -> AlertsScreen(model, screen.favorite)
                            Screen.Planner -> PlannerScreen(model)
                            is Screen.Trip -> TripScreen(model, screen)
                            is Screen.RouteMap -> RouteMapScreen(model, screen.itinerary, screen.updated)
                            Screen.Settings -> SettingsScreen(model)
                        }
                    }
                }
            }
        }
    }
}
