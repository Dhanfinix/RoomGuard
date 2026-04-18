package dev.dhanfinix.roomguard.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.dhanfinix.roomguard.core.CsvSerializer
import dev.dhanfinix.roomguard.core.ImportSummary
import dev.dhanfinix.roomguard.core.RestoreStrategy
import dev.dhanfinix.roomguard.core.BackupBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.StringBuilder

/**
 * A generic and robust implementation of [CsvSerializer] that provides "Zero-Config" data
 * portability for any Android Room database.
 *
 * Instead of requiring the developer to implement serialization logic for every entity,
 * this class uses SQLite's `sqlite_master` metadata to automatically:
 * 1. Discover all user-defined tables.
 * 2. Export their contents into a section-based CSV format.
 * 3. Re-import them by dynamically building "INSERT OR REPLACE" statements.
 *
 * ### CSV Format Architecture
 * The exported CSV uses a multi-section format to support multiple tables in a single file:
 * ```
 * [table_name_1]
 * column1,column2,column3
 * value1,value2,value3
 *
 * [table_name_2]
 * ...
 * ```
 *
 * ### Security & Integrity
 * - Automatically filters out Android/Room internal tables (e.g., `android_metadata`, `room_master_table`).
 * - Handles CSV escaping (quotes, commas, newlines) to prevent data corruption.
 * - Executes imports within a single database transaction for atomicity.
 *
 * @param database      The RoomDatabase instance to inspect and serialize.
 * @param excludeTables Optional list of table names to ignore during discovery.
 */
class AutomaticRoomCsvSerializer(
    private val database: RoomDatabase,
    private val excludeTables: List<String> = emptyList()
) : CsvSerializer {

    private val internalTables = listOf(
        "android_metadata",
        "sqlite_sequence",
        "room_master_table",
        "room_table_modification_log"
    )

    override suspend fun toCsv(): String = toBackupBundle(null).csvContent

    override suspend fun toBackupBundle(since: Long?): BackupBundle = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val tableNames = getTableNames(db)
        val sb = StringBuilder()

        for (tableName in tableNames) {
            sb.appendLine("[$tableName]")

            // Check if table has the tracking column
            var hasTrackingColumn = false
            db.query("PRAGMA table_info($tableName)").use { pragma ->
                while (pragma.moveToNext()) {
                    if (pragma.getString(1) == "last_update") {
                        hasTrackingColumn = true
                        break
                    }
                }
            }

            val query = if (since != null && hasTrackingColumn) {
                "SELECT * FROM $tableName WHERE last_update > $since"
            } else {
                "SELECT * FROM $tableName"
            }

            db.query(query).use { cursor ->
                val columnNames = cursor.columnNames
                sb.appendLine(columnNames.joinToString(","))

                while (cursor.moveToNext()) {
                    val row = (0 until cursor.columnCount).map { i ->
                        formatValue(cursor, i)
                    }
                    sb.appendLine(row.joinToString(","))
                }
            }
            sb.appendLine() // Gap between sections
        }

        BackupBundle(sb.toString())
    }

    override suspend fun fromCsv(content: String, strategy: RestoreStrategy): ImportSummary = withContext(Dispatchers.IO) {
        val lines = content.lineSequence().filter { it.isNotBlank() }.toList()
        var totalImported = 0
        var currentTable: String? = null
        var headers: List<String>? = null
        val dataPerTable = mutableMapOf<String, MutableList<List<String>>>()

        // Phase 1: Group data by table
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentTable = trimmed.removeSurrounding("[", "]")
                headers = null
            } else if (currentTable != null) {
                val parts = splitCsv(line)
                if (headers == null) {
                    headers = parts
                } else {
                    dataPerTable.getOrPut(currentTable) { mutableListOf() }.add(parts)
                    // We also need the headers for this table
                }
            }
        }

        // Phase 2: Execute imports in a transaction
        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            for ((tableName, rows) in dataPerTable) {
                // Re-calculate headers (we could store them in Phase 1, but this is safer)
                val tableLines = lines.dropWhile { it.trim() != "[$tableName]" }.drop(1)
                val tableHeaders = splitCsv(tableLines.firstOrNull() ?: "")
                
                if (tableHeaders.isEmpty()) continue

                if (strategy == RestoreStrategy.OVERWRITE) {
                    db.execSQL("DELETE FROM $tableName")
                }

                for (row in rows) {
                    val sql = buildInsertSql(tableName, tableHeaders, row)
                    db.execSQL(sql)
                    totalImported++
                }
            }
        }

        ImportSummary(
            itemsImported = totalImported,
            itemsSkipped = 0,
            message = "Successfully restored $totalImported records from ${dataPerTable.size} tables."
        )
    }

    // ── Internal Helpers ───────────────────────────────────────────────────────

    private fun getTableNames(db: SupportSQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (name !in internalTables && name !in excludeTables) {
                    tables.add(name)
                }
            }
        }
        return tables
    }

    private fun formatValue(cursor: android.database.Cursor, index: Int): String {
        return when (cursor.getType(index)) {
            android.database.Cursor.FIELD_TYPE_NULL -> ""
            android.database.Cursor.FIELD_TYPE_STRING -> escapeCsv(cursor.getString(index))
            else -> cursor.getString(index)
        }
    }

    private fun escapeCsv(value: String): String {
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n")) {
            return value
        }
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun splitCsv(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    current.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun buildInsertSql(tableName: String, headers: List<String>, values: List<String>): String {
        val cols = headers.joinToString(",")
        val vals = values.joinToString(",") { valStr ->
            if (valStr.isEmpty()) "NULL" else "'${valStr.replace("'", "''")}'"
        }
        // Using REPLACE for conflict resolution
        return "INSERT OR REPLACE INTO $tableName ($cols) VALUES ($vals)"
    }
}
