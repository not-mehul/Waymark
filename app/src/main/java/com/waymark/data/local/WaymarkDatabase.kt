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
        DocumentEntity::class,
        RaisedAlertEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class WaymarkDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun travelerDao(): TravelerDao
    abstract fun segmentDao(): SegmentDao
    abstract fun ideaDao(): IdeaDao
    abstract fun documentDao(): DocumentDao
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        /**
         * Version 3 removes the vault and the packing list.
         *
         * Booking references stop being rows in a separate encrypted table and
         * become columns on the booking they belong to, which is where a person
         * looks for them. The trip itself — its days, its bookings, its party,
         * its ideas, its documents — comes through untouched.
         *
         * **What does not come through are the encrypted values themselves.**
         * Confirmation codes, ticket numbers, document numbers and boarding-pass
         * payloads were sealed with an AES key held in the Android Keystore, and
         * the code that could open them is what this version deletes. Carrying
         * them over would mean keeping the whole cipher alive to run once inside
         * a migration — Keystore work in the one place in the app that must not
         * fail — to rescue values that can be typed again in a few seconds. The
         * columns are created empty and the sealed tables are dropped.
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

                // `documents.numberSealed` becomes `documents.number`. SQLite
                // could not rename a column until 3.25, which is Android 11 —
                // above this app's floor — so the table is rebuilt instead.
                // Sealed numbers cannot be read here, so the column starts
                // empty and the rest of the document survives.
                db.execSQL(
                    """
                    CREATE TABLE documents_v3 (
                        id TEXT NOT NULL PRIMARY KEY,
                        travelerId TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        label TEXT NOT NULL,
                        number TEXT NOT NULL,
                        issuer TEXT,
                        issuedOnEpochDay INTEGER,
                        expiresOnEpochDay INTEGER,
                        note TEXT,
                        fileUri TEXT,
                        updatedAtMillis INTEGER NOT NULL,
                        FOREIGN KEY(travelerId) REFERENCES travelers(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO documents_v3
                        (id, travelerId, kind, label, number, issuer,
                         issuedOnEpochDay, expiresOnEpochDay, note, fileUri, updatedAtMillis)
                    SELECT id, travelerId, kind, label, '', issuer,
                           issuedOnEpochDay, expiresOnEpochDay, note, fileUri, updatedAtMillis
                    FROM documents
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE documents")
                db.execSQL("ALTER TABLE documents_v3 RENAME TO documents")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_documents_travelerId " +
                        "ON documents(travelerId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_documents_expiresOnEpochDay " +
                        "ON documents(expiresOnEpochDay)"
                )
            }
        }
    }
}
