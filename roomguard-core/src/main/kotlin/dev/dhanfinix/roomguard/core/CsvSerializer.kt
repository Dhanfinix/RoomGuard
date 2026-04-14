package dev.dhanfinix.roomguard.core

/**
 * The host app implements this interface to define how its data is serialized to/from CSV.
 *
 * RoomGuard has no knowledge of the host app's entity classes.
 * The host is fully responsible for:
 * - Generating the CSV string (schema, sections, encoding)
 * - Parsing CSV lines back into entities
 * - Duplicate detection during import
 * - Returning an [ImportSummary] with counts
 *
 * See sample app's `NoteCsvSerializer` for a reference implementation.
 */
interface CsvSerializer {

    /**
     * Convert all app data to a single CSV string.
     *
     * This is a suspend function so DB reads can be done inside a coroutine.
     * The format is entirely defined by the host app.
     *
     * Recommended structure:
     * ```
     * [SECTION_NAME]
     * column1,column2,...
     * value1,value2,...
     *
     * [ANOTHER_SECTION]
     * ...
     * ```
     *
     * @return A UTF-8 CSV string
     */
    suspend fun toCsv(): String

    /**
     * Parse the given CSV string and insert/merge data into the local database.
     *
     * This is a suspend function so DB writes can be done inside a coroutine.
     * The host is responsible for:
     * - Parsing section headers
     * - Parsing column names
     * - Type conversion
     * - Conflict resolution (skip duplicates, replace, or merge)
     *
     * @param content  Full CSV string (from [exportToCsv] or user-provided file)
     * @param strategy Choice between overwriting existing data or merging.
     * @return [ImportSummary] with counts of what was imported vs. skipped
     */
    suspend fun fromCsv(content: String, strategy: RestoreStrategy): ImportSummary
}
