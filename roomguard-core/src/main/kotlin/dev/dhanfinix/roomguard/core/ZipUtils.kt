package dev.dhanfinix.roomguard.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object ZipUtils {

    /**
     * Compresses a file using GZIP.
     */
    fun compressFile(source: File, target: File) {
        FileInputStream(source).use { input ->
            GZIPOutputStream(FileOutputStream(target)).use { output ->
                input.copyTo(output)
                output.finish() // Ensure all data is written
            }
        }
    }

    /**
     * Decompresses a GZIP file.
     */
    fun decompressFile(source: File, target: File) {
        GZIPInputStream(FileInputStream(source)).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Checks if a file is GZIP compressed by inspecting the magic bytes (0x1f8b).
     */
    fun isGzipped(file: File): Boolean {
        if (!file.exists() || file.length() < 2) return false
        return FileInputStream(file).use { input ->
            input.read() == 0x1f && input.read() == 0x8b
        }
    }
}
