package dev.dhanfinix.roomguard.local

import android.content.Context
import android.net.Uri
import dev.dhanfinix.roomguard.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Local file implementation of [LocalBackupManager].
 *
 * @param context    Application context (used for cache dir and content resolver)
 * @param serializer Host-provided CSV serializer — owns the data format entirely
 * @param filePrefix Prefix for the generated CSV filename. Defaults to "roomguard_backup".
 *                   Consider using your app's name, e.g. "myapp_backup".
 */
class RoomGuardLocal(
    private val context: Context,
    private val serializer: CsvSerializer,
    private val filePrefix: String = "roomguard_backup",
    private val config: RoomGuardConfig = RoomGuardConfig()
) : LocalBackupManager {

    // ── Export ─────────────────────────────────────────────────────────────────

    /**
     * Generates a local backup file in the requested format and writes it to the app's cache directory.
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
     * Reads the file at the given URI string and delegates to [CsvSerializer.fromCsv].
     *
     * The host's [CsvSerializer.fromCsv] handles all parsing, deduplication,
     * and insertion into the DB.
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
