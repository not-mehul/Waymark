package com.waymark

import android.app.Application
import com.waymark.alerts.DepartureWatch
import com.waymark.alerts.Notifications
import com.waymark.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WaymarkApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        DepartureWatch.schedule(this)
        scope.launch {
            // The directory first: a traveler who opens the app to add a flight
            // should find the field already able to resolve their airport.
            container.loadAirportDirectory()
            // Nothing is written on first run: the worked example is offered
            // on the empty shelf rather than installed on somebody's behalf.
            // This only sweeps reminders whose booking has already gone.
            container.tripRepository.pruneOrphans()
        }
    }
}
