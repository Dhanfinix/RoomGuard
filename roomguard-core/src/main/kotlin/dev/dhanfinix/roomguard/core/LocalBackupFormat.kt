package dev.dhanfinix.roomguard.core

/**
 * Local backup file format.
 */
enum class LocalBackupFormat(
    val fileExtension: String,
    val mimeType: String,
    val title: String,
    val subtitle: String
) {
    CSV(
        fileExtension = ".csv",
        mimeType = "text/csv",
        title = "Human-readable CSV",
        subtitle = "Easy to inspect, edit, and share."
    ),
    COMPRESSED(
        fileExtension = ".csv.gz",
        mimeType = "application/gzip",
        title = "Compressed backup",
        subtitle = "Smaller file size for archiving and transport."
    )
}
