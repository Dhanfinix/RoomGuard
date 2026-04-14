package dev.dhanfinix.roomguard.core

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * A fake [DatabaseProvider] that uses an in-memory SQLite database.
 * Useful for unit testing SQL-based logic without hitting the disk.
 */
class FakeDatabaseProvider(private val db: SQLiteDatabase) : DatabaseProvider {
    
    override fun getDatabase(): SQLiteDatabase = db

    override fun getDatabaseFile(): File = File("fake.db")

    override fun closeDatabase() {
        // usually don't close in-memory DB during tests to keep data
    }

    override fun getWritableDatabase(): SQLiteDatabase = db

    override fun onRestoreComplete() {
        // no-op
    }
}

/**
 * A simple [CsvSerializer] for testing coordination logic.
 */
class FakeCsvSerializer(
    private val csvContent: String = "id,name\n1,test",
    private val summary: ImportSummary = ImportSummary(1, 0, "Success")
) : CsvSerializer {
    
    override suspend fun toCsv(): String = csvContent

    override suspend fun fromCsv(csv: String, strategy: RestoreStrategy): ImportSummary = summary
}
