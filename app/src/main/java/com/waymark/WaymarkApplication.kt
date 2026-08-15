package com.waymark

import android.app.Application
import com.waymark.alerts.DelayWatch
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
        DelayWatch.schedule(this)
        scope.launch { container.seeder.seedIfEmpty() }
    }
}
