package dev.dhanfinix.roomguard.core

import java.io.File

/**
 * A container for logical backup data.
 * 
 * @param csvContent The serialized metadata in CSV format.
 * @param blobFiles  A map of relative file paths (as recorded in the CSV) to local [File] handles.
 */
data class BackupBundle(
    val csvContent: String,
    val blobFiles: Map<String, File> = emptyMap()
)
