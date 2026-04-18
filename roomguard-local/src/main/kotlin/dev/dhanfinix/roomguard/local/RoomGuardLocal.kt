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
    /**
     * Generates a local backup file from the current database state.
     */
    override suspend fun exportLocalBackup(format: LocalBackupFormat): BackupResult<String> = withContext(Dispatchers.IO) {
        try {
            val baseName = "${filePrefix}_${System.currentTimeMillis()}"
            val useZipBundle = config.blobStrategy == BlobStrategy.FILE_POINTER
            
            val fileName = if (useZipBundle) "$baseName.zip" else "$baseName${format.fileExtension}"
            val file = File(context.cacheDir, fileName)
            
            // Choose the correct serializer based on config
            val activeSerializer = if (config.blobStrategy != BlobStrategy.NONE && serializer is AutomaticRoomCsvSerializer) {
                // If it's a Room database, upgrade to the blob-aware version
                val db = (serializer as? AutomaticRoomCsvSerializer)?.let { 
                    // This is a bit hacky, but AutomaticRoomCsvSerializer has the database
                    // For now, assume the injected serializer is already configured correctly if provided manually
                    null 
                }
                serializer
            } else {
                serializer
            }

            if (useZipBundle) {
                // FILE_POINTER strategy needs a bundle
                val bundleDir = File(context.cacheDir, baseName)
                bundleDir.mkdirs()
                
                val csvFile = File(bundleDir, "data.csv")
                
                // Inject the bundle directory if the serializer is blob-aware
                if (activeSerializer is BlobRoomCsvSerializer) {
                    activeSerializer.blobDir = bundleDir
                }
                
                val csvContent = activeSerializer.toCsv()
                csvFile.writeText(csvContent)
                
                ZipUtils.zipDirectory(bundleDir, file)
                bundleDir.deleteRecursively()
            } else {
                val csv = activeSerializer.toCsv()
                val useGzip = format == LocalBackupFormat.COMPRESSED
                
                if (useGzip) {
                    val tempFile = File(context.cacheDir, "$baseName.csv.tmp")
                    tempFile.writeText(csv)
                    ZipUtils.compressFile(tempFile, file)
                    tempFile.delete()
                } else {
                    file.writeText(csv)
                }
            }
            
            BackupResult.Success(file.absolutePath)
        } catch (e: Exception) {
            BackupResult.Error(BackupErrorCode.EXPORT_FAILED, "Export failed: ${e.message}")
        }
    }

    // ── Import ─────────────────────────────────────────────────────────────────

    override suspend fun importFromLocal(uri: String, strategy: RestoreStrategy): BackupResult<ImportSummary> =
        withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "roomguard_import_temp")
            try {
                val parsedUri = Uri.parse(uri)
                
                context.contentResolver.openInputStream(parsedUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext BackupResult.Error(BackupErrorCode.IMPORT_FAILED, "Could not open file")

                val content: String
                val blobDir: File?

                when {
                    ZipUtils.isZip(tempFile) -> {
                        val unzipDir = File(context.cacheDir, "roomguard_unzipped_${System.currentTimeMillis()}")
                        unzipDir.mkdirs()
                        ZipUtils.unzipFile(tempFile, unzipDir)
                        
                        val csvFile = File(unzipDir, "data.csv")
                        content = if (csvFile.exists()) csvFile.readText() else throw Exception("Invalid ZIP backup: data.csv not found")
                        blobDir = unzipDir
                    }
                    ZipUtils.isGzipped(tempFile) -> {
                        val decompressedFile = File(context.cacheDir, "roomguard_import_decomp")
                        ZipUtils.decompressFile(tempFile, decompressedFile)
                        content = decompressedFile.readText()
                        decompressedFile.delete()
                        blobDir = null
                    }
                    else -> {
                        content = tempFile.readText()
                        blobDir = null
                    }
                }

                // If the serializer is BlobRoomCsvSerializer, we need to provide the blobDir
                if (serializer is BlobRoomCsvSerializer) {
                    serializer.blobDir = blobDir
                }

                val summary = serializer.fromCsv(content, strategy)
                
                // Cleanup unzip dir if exists
                if (ZipUtils.isZip(tempFile)) {
                    blobDir?.deleteRecursively()
                }
                
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
