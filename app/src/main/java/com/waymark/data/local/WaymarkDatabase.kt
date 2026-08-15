package com.waymark.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TripEntity::class,
        TravelerEntity::class,
        TripMemberEntity::class,
        SegmentEntity::class,
        ReservationEntity::class,
        BoardingPassEntity::class,
        FlightStatusEntity::class,
        RaisedAlertEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class WaymarkDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun travelerDao(): TravelerDao
    abstract fun segmentDao(): SegmentDao
    abstract fun reservationDao(): ReservationDao
    abstract fun boardingPassDao(): BoardingPassDao
    abstract fun flightStatusDao(): FlightStatusDao
    abstract fun alertDao(): AlertDao

    companion object {
        private const val NAME = "waymark.db"

        @Volatile
        private var instance: WaymarkDatabase? = null

        fun get(context: Context): WaymarkDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): WaymarkDatabase =
            Room.databaseBuilder(context, WaymarkDatabase::class.java, NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
