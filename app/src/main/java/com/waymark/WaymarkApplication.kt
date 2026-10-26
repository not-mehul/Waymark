package com.waymark

import android.app.Application
import com.waymark.alerts.DepartureWatch
import com.waymark.alerts.Notifications
import com.waymark.alerts.ReminderCheck
import com.waymark.di.AppContainer
import com.waymark.widget.NextUpWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class WaymarkApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
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
            // This only tidies what earlier builds left behind.
            container.tripRepository.repair()
            container.loadCityDirectory()
        }
        scope.launch {
            // Any change to a booking, a trip or a reminder setting moves the
            // next reminder; look again, once the burst of writes has settled.
            combine(
                container.tripRepository.observeItineraryChanges(),
                container.reminderStore.observe(),
            ) { _, _ -> }
                .debounce(SETTLE_MILLIS)
                .collect {
                    ReminderCheck.run(this@WaymarkApplication, container)
                    NextUpWidget.refresh(this@WaymarkApplication)
                }
        }
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
    }
}
