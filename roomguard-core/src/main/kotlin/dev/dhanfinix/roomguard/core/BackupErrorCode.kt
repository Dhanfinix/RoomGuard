package dev.dhanfinix.roomguard.core

object BackupErrorCode {
    const val AUTH_EXPIRED     = "AUTH_EXPIRED"       // Drive token invalid or revoked
    const val BACKUP_FAILED    = "BACKUP_FAILED"      // Upload to Drive failed
    const val RESTORE_FAILED   = "RESTORE_FAILED"     // Restore from Drive failed
    const val NO_BACKUP_FOUND  = "NO_BACKUP_FOUND"    // No file in appDataFolder
    const val DB_NOT_FOUND     = "DB_NOT_FOUND"       // Local DB file missing before backup
    const val INTEGRITY_FAILED = "INTEGRITY_FAILED"   // PRAGMA quick_check returned non-ok
    const val EXPORT_FAILED    = "EXPORT_FAILED"      // CSV generation or write failed
    const val IMPORT_FAILED    = "IMPORT_FAILED"      // CSV read or parse failed
    const val NOT_AUTHORIZED   = "NOT_AUTHORIZED"     // Drive API returned not-authorized
}
