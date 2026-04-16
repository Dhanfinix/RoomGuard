package dev.dhanfinix.roomguard.core

/**
 * Contract for local backup/restore operations.
 *
 * Implement via `RoomGuardLocal` (roomguard-local module).
 */
interface LocalBackupManager {

    /**
     * Exports all app data using the requested local backup format,
     * then writes it to the app's cache directory.
     *
     * @param format Local backup format to generate.
     * @return [BackupResult.Success] containing the generated file path as a String,
     *         or [BackupResult.Error] on failure.
     *
     * Caller decides what to do with the File (share via Intent, copy to external, etc.)
     */
    suspend fun exportLocalBackup(format: LocalBackupFormat = LocalBackupFormat.COMPRESSED): BackupResult<String>

    /**
     * Reads a local backup file from the given URI string and delegates parsing to [CsvSerializer.fromCsv].
     *
     * @param uri Content URI string resolved by the host Activity (file picker result).
     *            Use [android.net.Uri.toString()] to convert from Uri.
     * @param strategy Whether to replace all data or merge.
     * @return [BackupResult.Success] with [ImportSummary], or [BackupResult.Error]
     */
    suspend fun importFromLocal(uri: String, strategy: RestoreStrategy): BackupResult<ImportSummary>

    /**
     * Backward-compatible CSV export wrapper.
     *
     * Prefer [exportLocalBackup] so the caller can explicitly choose the format.
     */
    @Deprecated(
        message = "Use exportLocalBackup(format) instead.",
        replaceWith = ReplaceWith("exportLocalBackup(LocalBackupFormat.CSV)")
    )
    suspend fun exportToCsv(): BackupResult<String> = exportLocalBackup(LocalBackupFormat.CSV)

    /**
     * Backward-compatible CSV import wrapper.
     *
     * Prefer [importFromLocal] so the caller can accept all supported local backup formats.
     */
    @Deprecated(
        message = "Use importFromLocal(uri, strategy) instead.",
        replaceWith = ReplaceWith("importFromLocal(uri, strategy)")
    )
    suspend fun importFromCsv(uri: String, strategy: RestoreStrategy): BackupResult<ImportSummary> =
        importFromLocal(uri, strategy)
}
