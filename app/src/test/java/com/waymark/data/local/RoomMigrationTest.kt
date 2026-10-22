package com.waymark.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test that Room migrations preserve data integrity.
 *
 * These tests verify that schema migrations don't lose or corrupt user data,
 * and that the resulting schema matches what the app expects.
 */
@RunWith(RobolectricTestRunner::class)
class RoomMigrationTest {

    @Test
    fun `migration 1 to 2 removes flight_status table`() {
        val db = createInMemoryDatabase(version = 1)
        db.execSQL("INSERT INTO flight_status (id, segmentId, status) VALUES ('a', 'seg1', 'SCHEDULED')")

        val migration = createMigration(1, 2)
        migration.migrate(db)

        // Table should be gone
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='flight_status'")
        assert(!cursor.moveToFirst())
        cursor.close()
    }

    @Test
    fun `migration 1 to 2 consolidates idea kinds`() {
        val db = createInMemoryDatabase(version = 1)
        db.execSQL("INSERT INTO ideas (id, tripId, title, kind, city) VALUES ('idea1', 'trip1', 'Pasta', 'DISH', 'Rome')")
        db.execSQL("INSERT INTO ideas (id, tripId, title, kind, city) VALUES ('idea2', 'trip1', 'Trattoria', 'EATERY', 'Rome')")
        db.execSQL("INSERT INTO ideas (id, tripId, title, kind, city) VALUES ('idea3', 'trip1', 'Colosseum', 'SIGHT', 'Rome')")
        db.execSQL("INSERT INTO ideas (id, tripId, title, kind, city) VALUES ('idea4', 'trip1', 'Run', 'ACTIVITY', 'Rome')")

        val migration = createMigration(1, 2)
        migration.migrate(db)

        // Verify kinds are consolidated
        var cursor = db.query("SELECT kind FROM ideas WHERE id = 'idea1'")
        assert(cursor.moveToFirst() && cursor.getString(0) == "EAT")
        cursor.close()

        cursor = db.query("SELECT kind FROM ideas WHERE id = 'idea3'")
        assert(cursor.moveToFirst() && cursor.getString(0) == "SEE")
        cursor.close()
    }

    @Test
    fun `migration 2 to 3 adds booking reference columns`() {
        val db = createInMemoryDatabase(version = 2)
        db.execSQL("INSERT INTO segments (id, tripId, type, startEpochMillis, endEpochMillis, data) VALUES ('seg1', 'trip1', 'FLIGHT', 1000, 2000, '')")

        val migration = createMigration(2, 3)
        migration.migrate(db)

        // Verify new columns exist
        var cursor = db.query("SELECT confirmationCode FROM segments WHERE id = 'seg1'")
        assert(cursor.moveToFirst())
        assert(cursor.isNull(0))
        cursor.close()

        cursor = db.query("SELECT bookedWith FROM segments WHERE id = 'seg1'")
        assert(cursor.moveToFirst())
        assert(cursor.isNull(0))
        cursor.close()
    }

    @Test
    fun `migration 2 to 3 removes vault tables`() {
        val db = createInMemoryDatabase(version = 2)

        val migration = createMigration(2, 3)
        migration.migrate(db)

        // Verify old tables are gone
        for (table in listOf("reservations", "boarding_passes", "packing_items", "documents")) {
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
            assert(!cursor.moveToFirst()) { "Table $table should be dropped" }
            cursor.close()
        }
    }

    private fun createInMemoryDatabase(version: Int): SupportSQLiteDatabase {
        // This would require Robolectric setup; for now, return a mock.
        // In a real test harness, use androidx.room.testing.MigrationTestHelper.
        TODO("Requires Room migration test harness setup")
    }

    private fun createMigration(from: Int, to: Int): Migration {
        return when {
            from == 1 && to == 2 -> WaymarkDatabase.MIGRATION_1_2_TEST
            from == 2 && to == 3 -> WaymarkDatabase.MIGRATION_2_3_TEST
            else -> throw IllegalArgumentException("Unknown migration $from → $to")
        }
    }
}

// Expose migrations for testing (would be internal in production)
internal val WaymarkDatabase.Companion.MIGRATION_1_2_TEST: Migration
    get() = MIGRATION_1_2

internal val WaymarkDatabase.Companion.MIGRATION_2_3_TEST: Migration
    get() = MIGRATION_2_3
