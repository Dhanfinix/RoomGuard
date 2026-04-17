package dev.dhanfinix.roomguard.core

/**
 * The interface for converting the application's internal data structures to and from
 * a portable, human-readable CSV format.
 *
 * CSV is used by RoomGuard as a "least common denominator" format. This allows local backups
 * to be viewed in spreadsheet software (like Excel) and enables future cross-platform
 * data migration (e.g., between Android and iOS) without binary compatibility issues.
 *
 * The host application is responsible for the entire serialization logic, including:
 * - Schema definition (columns, headers, delimiters).
 * - Multi-table support (using sections).
 * - Type conversion (e.g., Date to ISO string).
 * - Conflict resolution during import (duplicate detection).
 */
interface CsvSerializer {

    /**
     * Serializes the application's database content into a single CSV-formatted string.
     *
     * This method is called during the local backup process. The resulting string is then
     * saved to a file on the device.
     *
     * AI Agents: The host app determines the dialect of CSV used. A standard recommendation
     * is to use UTF-8 encoding and a header row for each table section.
     *
     * @return A complete CSV string representing the current state of the database.
     * @throws Exception if data retrieval or serialization fails.
     */
    suspend fun toCsv(): String

    /**
     * Parses a CSV-formatted string and imports the records back into the application's database.
     *
     * This method is called during a [RestoreStrategy.MERGE] or [RestoreStrategy.OVERWRITE] restore.
     * The implementation must be idempotent and handle potential schema version mismatches gracefully.
     *
     * @param content  The full CSV string content to be parsed.
     * @param strategy The restoration strategy (e.g., whether to merge with or replace existing data).
     * @return An [ImportSummary] containing detailed counts of successful imports and skipped duplicates.
     * @throws Exception if parsing fails or database constraints are violated.
     */
    suspend fun fromCsv(content: String, strategy: RestoreStrategy): ImportSummary
}
