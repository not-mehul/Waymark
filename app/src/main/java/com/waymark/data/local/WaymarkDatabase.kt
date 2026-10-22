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
        IdeaEntity::class,
        RaisedAlertEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class WaymarkDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun travelerDao(): TravelerDao
    abstract fun segmentDao(): SegmentDao
    abstract fun ideaDao(): IdeaDao
    abstract fun alertDao(): AlertDao

    companion object {
        const val NAME = "waymark.db"

        @Volatile
        private var instance: WaymarkDatabase? = null

        fun get(context: Context): WaymarkDatabase = instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(context: Context): WaymarkDatabase =
            Room.databaseBuilder(context, WaymarkDatabase::class.java, NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(*MIGRATIONS)
                .build()

        /**
         * Every schema this app has ever written, in order. A traveler's
         * itinerary must survive any update, so nothing here is ever
         * `fallbackToDestructiveMigration`: a migration that cannot run is a
         * crash to fix, not a database to quietly empty.
         */
        val MIGRATIONS: Array<Migration> by lazy {
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        }

        /**
         * Version 2 drops the flight-status table and folds the six idea kinds
         * into four. Both are destructive of data the app no longer models, so
         * they are done in SQL rather than by rebuilding the database.
         *
         * The very first builds wrote version 1 before the ideas board existed,
         * so the table is created here when it is missing rather than assumed.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS flight_status")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ideas` (`id` TEXT NOT NULL, " +
                        "`tripId` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`city` TEXT NOT NULL, `place` TEXT, `note` TEXT, `priceBand` TEXT, " +
                        "`typicalMinutes` INTEGER, `bestTime` TEXT, `plannedDateEpochDay` INTEGER, " +
                        "`interestedTravelerIds` TEXT, `status` TEXT NOT NULL, " +
                        "`scheduledSegmentId` TEXT, `source` TEXT NOT NULL, " +
                        "`addedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ideas_tripId` ON `ideas` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ideas_status` ON `ideas` (`status`)")
                db.execSQL("UPDATE ideas SET kind = 'EAT' WHERE kind IN ('DISH', 'EATERY')")
                db.execSQL("UPDATE ideas SET kind = 'SEE' WHERE kind = 'SIGHT'")
                db.execSQL("UPDATE ideas SET kind = 'DO' WHERE kind IN ('ACTIVITY', 'WALK')")
            }
        }

        /**
         * Version 3 removes the vault, the packing list and travel documents.
         *
         * Booking references stop being rows in a separate encrypted table and
         * become columns on the booking they belong to, which is where a person
         * looks for them. The trip itself — its days, its bookings, its party,
         * its ideas — comes through untouched.
         *
         * **What does not come through are the encrypted values themselves.**
         * Confirmation codes, ticket numbers and boarding-pass payloads were
         * sealed with an AES key held in the Android Keystore, and the code that
         * could open them is what this version deletes. Carrying them over would
         * mean keeping the whole cipher alive to run once inside a migration —
         * Keystore work in the one place in the app that must not fail — to
         * rescue values that can be typed again in a few seconds. The columns
         * are created empty and the sealed tables are dropped.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE segments ADD COLUMN confirmationCode TEXT")
                db.execSQL("ALTER TABLE segments ADD COLUMN bookedWith TEXT")
                db.execSQL("ALTER TABLE segments ADD COLUMN ticketNumbers TEXT")

                // The vendor is the one thing that was never sealed, so it is
                // the one thing worth moving across.
                db.execSQL(
                    """
                    UPDATE segments SET bookedWith = (
                        SELECT vendor FROM reservations
                        WHERE reservations.segmentId = segments.id LIMIT 1
                    )
                    WHERE EXISTS (
                        SELECT 1 FROM reservations WHERE reservations.segmentId = segments.id
                    )
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE IF EXISTS reservations")
                db.execSQL("DROP TABLE IF EXISTS boarding_passes")
                db.execSQL("DROP TABLE IF EXISTS packing_items")
                db.execSQL("DROP TABLE IF EXISTS documents")
            }
        }

        /**
         * Version 4 tidies what version 3 left behind.
         *
         * - Builds of version 3 written before travel documents were removed
         *   still carry a `documents` table, and Room refused to open them: the
         *   schema had changed without the version moving. It is dropped here,
         *   which repairs those installs as well.
         * - Three columns nothing ever wrote to from the app are removed:
         *   `trips.coverPlace`, `travelers.documentRef` and `ideas.source`.
         * - Reminders learn when their booking starts, so the trip screen can
         *   count down to it, and whether Android actually showed them, so one
         *   raised while notifications were blocked is posted once they are not.
         *
         * SQLite on this app's oldest supported Android cannot drop a column,
         * so each changed table is rebuilt: create, copy, drop, rename. Foreign
         * keys are not enforced while a migration runs, so replacing `trips`
         * and `travelers` does not cascade into the tables that point at them.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS documents")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trips_v4` (`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `destinationSummary` TEXT NOT NULL, " +
                        "`startEpochMillis` INTEGER NOT NULL, `endEpochMillis` INTEGER NOT NULL, " +
                        "`homeZoneId` TEXT NOT NULL, `archived` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO trips_v4 (id, name, destinationSummary, startEpochMillis, " +
                        "endEpochMillis, homeZoneId, archived) SELECT id, name, destinationSummary, " +
                        "startEpochMillis, endEpochMillis, homeZoneId, archived FROM trips"
                )
                db.execSQL("DROP TABLE trips")
                db.execSQL("ALTER TABLE trips_v4 RENAME TO trips")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `travelers_v4` (`id` TEXT NOT NULL, " +
                        "`fullName` TEXT NOT NULL, `nickname` TEXT, `seatPreference` TEXT NOT NULL, " +
                        "`mealPreference` TEXT, `loyaltyRefs` TEXT, `contactPhone` TEXT, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO travelers_v4 (id, fullName, nickname, seatPreference, " +
                        "mealPreference, loyaltyRefs, contactPhone) SELECT id, fullName, nickname, " +
                        "seatPreference, mealPreference, loyaltyRefs, contactPhone FROM travelers"
                )
                db.execSQL("DROP TABLE travelers")
                db.execSQL("ALTER TABLE travelers_v4 RENAME TO travelers")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ideas_v4` (`id` TEXT NOT NULL, " +
                        "`tripId` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                        "`city` TEXT NOT NULL, `place` TEXT, `note` TEXT, `priceBand` TEXT, " +
                        "`typicalMinutes` INTEGER, `bestTime` TEXT, `plannedDateEpochDay` INTEGER, " +
                        "`interestedTravelerIds` TEXT, `status` TEXT NOT NULL, " +
                        "`scheduledSegmentId` TEXT, `addedAtMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "INSERT INTO ideas_v4 (id, tripId, title, kind, city, place, note, priceBand, " +
                        "typicalMinutes, bestTime, plannedDateEpochDay, interestedTravelerIds, " +
                        "status, scheduledSegmentId, addedAtMillis) SELECT id, tripId, title, kind, " +
                        "city, place, note, priceBand, typicalMinutes, bestTime, plannedDateEpochDay, " +
                        "interestedTravelerIds, status, scheduledSegmentId, addedAtMillis FROM ideas"
                )
                db.execSQL("DROP TABLE ideas")
                db.execSQL("ALTER TABLE ideas_v4 RENAME TO ideas")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ideas_tripId` ON `ideas` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ideas_status` ON `ideas` (`status`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `raised_alerts_v4` (`signature` TEXT NOT NULL, " +
                        "`segmentId` TEXT NOT NULL, `label` TEXT NOT NULL, `headline` TEXT NOT NULL, " +
                        "`detail` TEXT NOT NULL, `startEpochMillis` INTEGER NOT NULL, " +
                        "`raisedAtMillis` INTEGER NOT NULL, `posted` INTEGER NOT NULL, " +
                        "`acknowledged` INTEGER NOT NULL, PRIMARY KEY(`signature`))"
                )
                // Reminders raised before this version were posted when raised,
                // as far as anyone can now tell; their start is the booking's.
                db.execSQL(
                    "INSERT INTO raised_alerts_v4 (signature, segmentId, label, headline, detail, " +
                        "startEpochMillis, raisedAtMillis, posted, acknowledged) " +
                        "SELECT signature, segmentId, designator, headline, detail, " +
                        "COALESCE((SELECT startEpochMillis FROM segments " +
                        "WHERE segments.id = raised_alerts.segmentId), raisedAtMillis), " +
                        "raisedAtMillis, 1, acknowledged FROM raised_alerts"
                )
                db.execSQL("DROP TABLE raised_alerts")
                db.execSQL("ALTER TABLE raised_alerts_v4 RENAME TO raised_alerts")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_raised_alerts_segmentId` " +
                        "ON `raised_alerts` (`segmentId`)"
                )
            }
        }
    }
}
