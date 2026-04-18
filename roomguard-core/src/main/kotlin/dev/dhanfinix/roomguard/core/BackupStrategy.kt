package dev.dhanfinix.roomguard.core

/**
 * Strategy for database backup.
 */
enum class BackupStrategy {
    /**
     * Physical backup of the raw SQLite database file.
     * Fast and represents an exact bit-for-bit snapshot.
     */
    PHYSICAL,

    /**
     * Logical backup of data via CSV metadata and separate BLOB files.
     * Enables incremental/differential uploads, smaller sync payloads,
     * and human-readable metadata.
     */
    INCREMENTAL
}
