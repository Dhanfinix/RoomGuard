package dev.dhanfinix.roomguard.local

import android.database.Cursor
import android.util.Base64
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.lang.StringBuilder

import dev.dhanfinix.roomguard.core.BlobStrategy
import dev.dhanfinix.roomguard.core.CsvSerializer
import dev.dhanfinix.roomguard.core.BlobCapableSerializer
import dev.dhanfinix.roomguard.core.ImportSummary
import dev.dhanfinix.roomguard.core.RestoreStrategy
import dev.dhanfinix.roomguard.core.BackupBundle
import dev.dhanfinix.roomguard.core.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * A specialized [CsvSerializer] that handles BLOB (binary) data using the specified [BlobStrategy].
 * 
 * This serializer is built for "Heavy" databases and follows the logic originally seen in the 
 * Stampin project, but adapted for RoomGuard's zero-config requirements.
 * 
 * @param database      The RoomDatabase instance.
 * @param blobStrategy  The strategy for handling BLOBs (NONE, BASE64, FILE_POINTER).
 * @param blobDir       The directory to store sidecar files if using [BlobStrategy.FILE_POINTER].
 * @param excludeTables Tables to ignore during export.
 */
class BlobRoomCsvSerializer(
    private val database: RoomDatabase,
    private val blobStrategy: BlobStrategy = BlobStrategy.NONE,
    override var blobDir: File? = null,
    private val excludeTables: List<String> = emptyList()
) : BlobCapableSerializer {

    private val internalTables = listOf(
        "android_metadata",
        "sqlite_sequence",
        "room_master_table",
        "room_table_modification_log"
    )

    private val BLOB_PREFIX = "[BLOB]:"
    private val FILE_PREFIX = "[FILE]:"
    private val NULL_MARKER = "[NULL]"

    override suspend fun toCsv(): String = toBackupBundle(null).csvContent

    override suspend fun toBackupBundle(since: Long?): BackupBundle = withContext(Dispatchers.IO) {
        val db = database.openHelper.readableDatabase
        val tableNames = getTableNames(db)
        val sb = StringBuilder()
        val blobFiles = mutableMapOf<String, File>()

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
                        val value = formatValue(cursor, i, tableName)
                        
                        // If it's a file pointer, track it in the bundle
                        if (value.startsWith(FILE_PREFIX)) {
                            val relativePath = value.removePrefix(FILE_PREFIX)
                            val file = File(blobDir, relativePath)
                            if (file.exists()) {
                                blobFiles[relativePath] = file
                            }
                        }
                        value
                    }
                    sb.appendLine(row.joinToString(","))
                }
            }
            sb.appendLine() // Gap between sections
        }

        BackupBundle(sb.toString(), blobFiles)
    }

    override suspend fun fromCsv(content: String, strategy: RestoreStrategy): ImportSummary = withContext(Dispatchers.IO) {
        val lines = content.lineSequence().filter { it.isNotBlank() }.toList()
        var totalImported = 0
        var totalSkipped = 0
        
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
                }
            }
        }

        // Phase 2: Execute imports in a transaction
        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            for ((tableName, rows) in dataPerTable) {
                // Re-calculate headers for safety
                val tableLines = lines.dropWhile { it.trim() != "[$tableName]" }.drop(1)
                val tableHeaders = splitCsv(tableLines.firstOrNull() ?: "")
                
                if (tableHeaders.isEmpty()) continue

                if (strategy == RestoreStrategy.OVERWRITE) {
                    db.execSQL("DELETE FROM $tableName")
                }

                for (row in rows) {
                    try {
                        val cv = android.content.ContentValues()
                        tableHeaders.forEachIndexed { index, col ->
                            val rawValue = if (index < row.size) row[index] else ""
                            
                            when {
                                rawValue == NULL_MARKER -> cv.putNull(col)
                                rawValue.startsWith(BLOB_PREFIX) -> {
                                    val base64 = rawValue.removePrefix(BLOB_PREFIX)
                                    cv.put(col, Base64.decode(base64, Base64.NO_WRAP))
                                }
                                rawValue.startsWith(FILE_PREFIX) -> {
                                    val relativePath = rawValue.removePrefix(FILE_PREFIX)
                                    val blobFile = File(blobDir, relativePath)
                                    if (blobFile.exists()) {
                                        cv.put(col, blobFile.readBytes())
                                    } else {
                                        cv.putNull(col)
                                    }
                                }
                                else -> cv.put(col, unescapeCsv(rawValue))
                            }
                        }
                        db.insert(tableName, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
                        totalImported++
                    } catch (e: Exception) {
                        totalSkipped++
                    }
                }
            }
        }

        ImportSummary(
            itemsImported = totalImported,
            itemsSkipped = totalSkipped,
            message = "Restored $totalImported records across ${dataPerTable.size} tables."
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

    private fun formatValue(cursor: android.database.Cursor, index: Int, tableName: String): String {
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> NULL_MARKER
            Cursor.FIELD_TYPE_STRING -> escapeCsv(cursor.getString(index))
            Cursor.FIELD_TYPE_BLOB -> {
                when (blobStrategy) {
                    BlobStrategy.NONE -> ""
                    BlobStrategy.BASE64 -> {
                        val blob = cursor.getBlob(index)
                        BLOB_PREFIX + Base64.encodeToString(blob, Base64.NO_WRAP)
                    }
                    BlobStrategy.FILE_POINTER -> {
                        if (blobDir == null) return ""
                        val blob = cursor.getBlob(index)
                        // Content-based hashing for deduplication
                        val hash = HashUtils.sha256(blob)
                        val fileName = "blobs/$hash.bin"
                        val file = File(blobDir, fileName)
                        if (!file.exists()) {
                            file.parentFile?.mkdirs()
                            file.writeBytes(blob)
                        }
                        FILE_PREFIX + fileName
                    }
                }
            }
            else -> cursor.getString(index)
        }
    }

    private fun escapeCsv(value: String): String {
        if (!value.contains(",") && !value.contains("\"") && !value.contains("\n")) {
            return value
        }
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun unescapeCsv(value: String): String {
        return if (value.startsWith("\"") && value.endsWith("\"")) {
            value.substring(1, value.length - 1).replace("\"\"", "\"")
        } else {
            value
        }
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
}
