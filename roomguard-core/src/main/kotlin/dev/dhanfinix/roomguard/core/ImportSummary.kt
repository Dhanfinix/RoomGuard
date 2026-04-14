package dev.dhanfinix.roomguard.core

/**
 * Summary returned after a CSV import via [LocalBackupManager.importFromCsv].
 * The host's [CsvSerializer.fromCsv] is responsible for populating this.
 */
data class ImportSummary(
    val itemsImported: Int,
    val itemsSkipped: Int,
    val message: String
)
