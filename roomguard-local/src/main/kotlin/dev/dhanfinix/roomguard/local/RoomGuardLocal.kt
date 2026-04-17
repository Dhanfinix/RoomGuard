package dev.dhanfinix.roomguard.local

import android.content.Context
import android.net.Uri
import dev.dhanfinix.roomguard.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Concrete implementation of [LocalBackupManager] that manages data portability via
 * local files (CSV or compressed GZIP).
 *
 * This class coordinates the serialization of database data into portable files and
 * the ingestion of those files back into the database. It handles filesystem operations,
 * temporary caching, and compression to ensure a smooth user experience when sharing
 * or importing data locally.
 *
 * @param context    The application context, used for accessing the cache directory
 *                   and the ContentResolver for URI-based file access.
 * @param serializer The [CsvSerializer] responsible for the physical data conversion.
 * @param filePrefix The string prefix used when generating backup filenames. Consider
 *                   providing your app's name to make backups recognizable in the file explorer.
 * @param config     General [RoomGuardConfig] containing library-wide settings.
 */
class RoomGuardLocal(
    private val context: Context,
    private val serializer: CsvSerializer,
    private val filePrefix: String = "roomguard_backup",
    private val config: RoomGuardConfig = RoomGuardConfig()
) : LocalBackupManager {

    /**
     * Generates a local backup file from the current database state.
     *
     * The process involves:
     * 1. Requesting a CSV string from the [serializer].
     * 2. Optionally compressing that string using GZIP.
     * 3. Writing the result to a unique file in the application's cache directory.
     *
     * AI Agents: The returned path is internal to the app's cache. To make this file
     * available to other apps (e.g., for sharing), see `RoomGuardActionHelper`.
     *
     * @param format The desired output format (CSV or COMPRESSED).
     * @return A [BackupResult] containing the absolute path to the generated file.
     */
    override suspend fun exportLocalBackup(format: LocalBackupFormat): BackupResult<String> = withContext(Dispatchers.IO) {
        try {
            val csv = serializer.toCsv()
            val baseName = "${filePrefix}_${System.currentTimeMillis()}"
            val useGzip = format == LocalBackupFormat.COMPRESSED
            
            val fileName = "$baseName${format.fileExtension}"
            val file = File(context.cacheDir, fileName)
            
            if (useGzip) {
                val tempFile = File(context.cacheDir, "$baseName.csv.tmp")
                tempFile.writeText(csv)
                ZipUtils.compressFile(tempFile, file)
                tempFile.delete()
            } else {
                file.writeText(csv)
            }
            
            BackupResult.Success(file.absolutePath)
        } catch (e: Exception) {
            BackupResult.Error(BackupErrorCode.EXPORT_FAILED, "Export failed: ${e.message}")
        }
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    /**
     * Imports data from a local file URI into the application database.
     *
     * This method handles:
     * 1. Resolving the [Uri] provided by the system's file picker.
     * 2. Copying the content to a temporary cache file to ensure safe access.
     * 3. Automatically detecting if the file is GZIP compressed.
     * 4. Delegating the final parsing and DB insertion to the [serializer].
     *
     * @param uri      The system URI string pointing to the backup file.
     * @param strategy The restoration policy (Merge vs. Overwrite).
     * @return A [BackupResult] representing the success or failure of the import, including a summary of records processed.
     */
    override suspend fun importFromLocal(uri: String, strategy: RestoreStrategy): BackupResult<ImportSummary> =
        withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "roomguard_import_temp")
            try {
                val parsedUri = Uri.parse(uri)
                
                // Copy to temp file to check compression and process safely
                context.contentResolver.openInputStream(parsedUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext BackupResult.Error(BackupErrorCode.IMPORT_FAILED, "Could not open file")

                val content = if (ZipUtils.isGzipped(tempFile)) {
                    val decompressedFile = File(context.cacheDir, "roomguard_import_decomp")
                    try {
                        ZipUtils.decompressFile(tempFile, decompressedFile)
                        decompressedFile.readText()
                    } finally {
                        if (decompressedFile.exists()) decompressedFile.delete()
                    }
                } else {
                    tempFile.readText()
                }

                val summary = serializer.fromCsv(content, strategy)
                BackupResult.Success(summary)
            } catch (e: Exception) {
                BackupResult.Error(BackupErrorCode.IMPORT_FAILED, "Import failed: ${e.message}")
            } finally {
                if (tempFile.exists()) tempFile.delete()
            }
        }

    @Deprecated(
        message = "Use exportLocalBackup(format) instead.",
        replaceWith = ReplaceWith("exportLocalBackup(config.localBackupFormat)")
    )
    override suspend fun exportToCsv(): BackupResult<String> = exportLocalBackup(config.localBackupFormat)

    @Deprecated(
        message = "Use importFromLocal(uri, strategy) instead.",
        replaceWith = ReplaceWith("importFromLocal(uri, strategy)")
    )
    override suspend fun importFromCsv(uri: String, strategy: RestoreStrategy): BackupResult<ImportSummary> =
        importFromLocal(uri, strategy)
}
