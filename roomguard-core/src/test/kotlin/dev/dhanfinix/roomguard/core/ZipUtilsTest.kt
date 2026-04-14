package dev.dhanfinix.roomguard.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ZipUtilsTest {

    private lateinit var workspaceDir: File
    private lateinit var sourceFile: File
    private lateinit var compressedFile: File
    private lateinit var decompressedFile: File

    @Before
    fun setup() {
        workspaceDir = Files.createTempDirectory("roomguard_test").toFile()
        sourceFile = File(workspaceDir, "source.txt")
        compressedFile = File(workspaceDir, "compressed.gz")
        decompressedFile = File(workspaceDir, "decompressed.txt")
        
        sourceFile.writeText("Hello, RoomGuard! This is a test string to check compression.")
    }

    @After
    fun teardown() {
        workspaceDir.deleteRecursively()
    }

    @Test
    fun `test file compression and decompression is lossless`() {
        // 1. Compress
        ZipUtils.compressFile(sourceFile, compressedFile)
        assertTrue(compressedFile.exists())
        assertTrue(compressedFile.length() > 0)
        
        // 2. Magic byte verification
        assertTrue(ZipUtils.isGzipped(compressedFile))
        assertFalse(ZipUtils.isGzipped(sourceFile))
        
        // 3. Decompress
        ZipUtils.decompressFile(compressedFile, decompressedFile)
        assertTrue(decompressedFile.exists())
        
        // 4. Verify original content matches
        val originalContent = sourceFile.readText()
        val restoredContent = decompressedFile.readText()
        assertEquals(originalContent, restoredContent)
    }
    
    @Test
    fun `test magic byte detection on missing or empty files`() {
        val missingFile = File(workspaceDir, "doesnotexist.txt")
        assertFalse(ZipUtils.isGzipped(missingFile))
        
        val emptyFile = File(workspaceDir, "empty.txt")
        emptyFile.createNewFile()
        assertFalse(ZipUtils.isGzipped(emptyFile))
    }

    @Test
    fun `isGzipped returns false for corrupted headers`() {
        val corruptedFile = File(workspaceDir, "corrupted.gz")
        // Gzip header starts with 0x1f 0x8b. Let's write dummy data.
        corruptedFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02))
        assertFalse(ZipUtils.isGzipped(corruptedFile))
    }

    @Test(expected = Exception::class)
    fun `decompressFile throws on non-gzipped input`() {
        val nonGzipFile = File(workspaceDir, "plain.txt")
        nonGzipFile.writeText("Not a gzip file")
        
        ZipUtils.decompressFile(nonGzipFile, decompressedFile)
    }
}
