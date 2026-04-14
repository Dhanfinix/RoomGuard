package dev.dhanfinix.roomguard.sample.data

import dev.dhanfinix.roomguard.core.CsvSerializer
import dev.dhanfinix.roomguard.core.ImportSummary
import kotlinx.coroutines.flow.first

class NoteCsvSerializer(private val dao: NoteDao) : CsvSerializer {

    override suspend fun toCsv(): String {
        val notes = dao.getAllNotes().first()
        val sb = StringBuilder()
        sb.appendLine("[NOTES]")
        sb.appendLine("id,title,body,createdAt")
        notes.forEach { note ->
            sb.appendLine("${note.id},\"${note.title}\",\"${note.body}\",${note.createdAt}")
        }
        return sb.toString()
    }

    override suspend fun fromCsv(content: String, strategy: dev.dhanfinix.roomguard.core.RestoreStrategy): ImportSummary {
        if (strategy == dev.dhanfinix.roomguard.core.RestoreStrategy.OVERWRITE) {
            dao.clearAllNotes()
        }
        val existingIds = if (strategy == dev.dhanfinix.roomguard.core.RestoreStrategy.OVERWRITE) {
            emptySet()
        } else {
            dao.getAllNotes().first().map { it.createdAt }.toSet()
        }
        var imported = 0
        var skipped = 0
        var inSection = false

        content.lines().forEach { line ->
            when {
                line.trim() == "[NOTES]" -> { inSection = true; return@forEach }
                line.startsWith("id,") -> return@forEach   // header
                inSection && line.isNotBlank() -> {
                    val tokens = line.split(",")
                    val createdAt = tokens.getOrNull(3)?.toLongOrNull() ?: return@forEach
                    if (createdAt in existingIds) { skipped++; return@forEach }
                    dao.insert(NoteEntity(
                        title = tokens.getOrNull(1)?.trim('"') ?: "",
                        body = tokens.getOrNull(2)?.trim('"') ?: "",
                        createdAt = createdAt
                    ))
                    imported++
                }
            }
        }
        return ImportSummary(imported, skipped, "Imported $imported notes, skipped $skipped.")
    }
}
