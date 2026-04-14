package dev.dhanfinix.roomguard.core

sealed class BackupResult<out T> {
    data class Success<T>(val data: T) : BackupResult<T>()
    data class Error(
        val code: String,
        val message: String
    ) : BackupResult<Nothing>()
}

// Extension helpers
fun <T> BackupResult<T>.isSuccess(): Boolean = this is BackupResult.Success
fun <T> BackupResult<T>.isError(): Boolean = this is BackupResult.Error
fun <T> BackupResult<T>.getOrNull(): T? = (this as? BackupResult.Success)?.data
fun <T> BackupResult<T>.errorCode(): String? = (this as? BackupResult.Error)?.code
