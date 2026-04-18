package dev.dhanfinix.roomguard.core

/**
 * Strategy for handling BLOB (binary) data during CSV serialization.
 */
enum class BlobStrategy {
    /**
     * Ignore BLOB columns entirely. Values will be empty in CSV.
     */
    NONE,

    /**
     * Encode BLOB data as a Base64 string directly inside the CSV.
     * Simple and self-contained, but can lead to very large CSV files.
     */
    BASE64,

    /**
     * Save each BLOB to a separate file in a sidecar directory and record the relative
     * path in the CSV. Recommended for large binary data (like images or PDF).
     */
    FILE_POINTER
}
