package com.waymark.di

import android.content.Context
import com.waymark.data.local.ReminderStore
import com.waymark.data.repo.AlertRepository
import com.waymark.data.repo.TripRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The graph, for what Android constructs and Hilt does not: the reminder
 * worker, and the widget, which Glance builds for itself.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface Graph {
    fun tripRepository(): TripRepository
    fun alertRepository(): AlertRepository
    fun reminderStore(): ReminderStore
}

val Context.graph: Graph
    get() = EntryPointAccessors.fromApplication(applicationContext, Graph::class.java)
