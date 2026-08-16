package com.waymark.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TripEntity::class,
        TravelerEntity::class,
        TripMemberEntity::class,
        SegmentEntity::class,
        ReservationEntity::class,
        BoardingPassEntity::class,
        IdeaEntity::class,
        DocumentEntity::class,
        PackingItemEntity::class,
        RaisedAlertEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class WaymarkDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun travelerDao(): TravelerDao
    abstract fun segmentDao(): SegmentDao
    abstract fun reservationDao(): ReservationDao
    abstract fun boardingPassDao(): BoardingPassDao
    abstract fun ideaDao(): IdeaDao
    abstract fun documentDao(): DocumentDao
    abstract fun packingDao(): PackingDao
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
                .addMigrations(MIGRATION_1_2)
                .build()

        /**
         * Version 2 drops the flight-status table and folds the six idea kinds
         * into four. Both are destructive of data the app no longer models, so
         * they are done in SQL rather than by rebuilding the database: a
         * traveler's itinerary must survive an app update.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS flight_status")
                db.execSQL("UPDATE ideas SET kind = 'EAT' WHERE kind IN ('DISH', 'EATERY')")
                db.execSQL("UPDATE ideas SET kind = 'SEE' WHERE kind = 'SIGHT'")
                db.execSQL("UPDATE ideas SET kind = 'DO' WHERE kind IN ('ACTIVITY', 'WALK')")
            }
        }
    }
}
