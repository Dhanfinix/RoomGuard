package dev.dhanfinix.roomguard.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
     * Zips a directory into a ZIP archive.
     */
    fun zipDirectory(sourceDir: File, targetZip: File) {
        ZipOutputStream(FileOutputStream(targetZip)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = file.relativeTo(sourceDir).path
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { input ->
                        input.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    /**
     * Unzips a ZIP archive into a target directory.
     */
    fun unzipFile(sourceZip: File, targetDir: File) {
        ZipInputStream(FileInputStream(sourceZip)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
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

    /**
     * Checks if a file is a ZIP archive by inspecting the magic bytes (PK\x03\x04).
     */
    fun isZip(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return FileInputStream(file).use { input ->
            input.read() == 'P'.code && input.read() == 'K'.code &&
                    input.read() == 0x03 && input.read() == 0x04
        }
    }
}
