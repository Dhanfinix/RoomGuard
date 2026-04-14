package dev.dhanfinix.roomguard.core

/**
 * Contract for local CSV-based backup/restore operations.
 *
 * Implement via `RoomGuardLocal` (roomguard-local module).
 */
interface LocalBackupManager {

    /**
     * Exports all app data to a CSV string via [CsvSerializer.toCsv],
     * then writes it to the app's cache directory.
     *
     * @return [BackupResult.Success] containing the generated file path as a String,
     *         or [BackupResult.Error] on failure.
     *
     * Caller decides what to do with the File (share via Intent, copy to external, etc.)
     */
    suspend fun exportToCsv(): BackupResult<String>

    /**
     * Reads a CSV file from the given URI string and delegates parsing to [CsvSerializer.fromCsv].
     *
     * @param uri Content URI string resolved by the host Activity (file picker result).
     *            Use [android.net.Uri.toString()] to convert from Uri.
     * @param strategy Whether to replace all data or merge.
     * @return [BackupResult.Success] with [ImportSummary], or [BackupResult.Error]
     */
    suspend fun importFromCsv(uri: String, strategy: RestoreStrategy): BackupResult<ImportSummary>
}
