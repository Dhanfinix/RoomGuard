package dev.dhanfinix.roomguard.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dev.dhanfinix.roomguard.core.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class RoomGuardLocalTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var mockSerializer: CsvSerializer
    private lateinit var roomGuardLocal: RoomGuardLocal
    private lateinit var tempDir: File

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        mockSerializer = mockk(relaxed = true)
        
        // Create a real temp directory for file operations
        tempDir = Files.createTempDirectory("roomguard_test").toFile()
        every { context.cacheDir } returns tempDir
        every { context.contentResolver } returns contentResolver
        
        // Mock Uri.parse to return a mock Uri
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)

        roomGuardLocal = RoomGuardLocal(
            context = context,
            serializer = mockSerializer,
            filePrefix = "test_backup",
            config = RoomGuardConfig(useCompression = false)
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkStatic(Uri::class)
    }

    @Test
    fun `exportToCsv creates a valid file and returns path`() = runTest {
        // Arrange
        val csvContent = "id,name\n1,test"
        coEvery { mockSerializer.toCsv() } returns csvContent
        
        // Action
        val result = roomGuardLocal.exportToCsv()
        
        // Assert
        assertTrue(result is BackupResult.Success)
        val path = (result as BackupResult.Success).data
        val file = File(path)
        assertTrue(file.exists())
        assertEquals(csvContent, file.readText())
        assertTrue(file.name.startsWith("test_backup_"))
        assertTrue(file.name.endsWith(".csv"))
    }

    @Test
    fun `exportToCsv with compression creates gzipped file`() = runTest {
        // Arrange
        val config = RoomGuardConfig(useCompression = true)
        roomGuardLocal = RoomGuardLocal(context, mockSerializer, config = config)
        coEvery { mockSerializer.toCsv() } returns "some data"
        
        // Action
        val result = roomGuardLocal.exportToCsv()
        
        // Assert
        assertTrue(result is BackupResult.Success)
        val path = (result as BackupResult.Success).data
        assertTrue(path.endsWith(".csv.gz"))
        assertTrue(ZipUtils.isGzipped(File(path)))
    }

    @Test
    fun `importFromCsv reads content and delegates to serializer`() = runTest {
        // Arrange
        val csvContent = "imported,data"
        val mockInputStream = ByteArrayInputStream(csvContent.toByteArray())
        every { contentResolver.openInputStream(any()) } returns mockInputStream
        
        val summary = ImportSummary(1, 0, "Imported 1 row")
        coEvery { mockSerializer.fromCsv(csvContent, any()) } returns summary
        
        // Action
        val result = roomGuardLocal.importFromCsv("content://test", RestoreStrategy.MERGE)
        
        // Assert
        assertTrue(result is BackupResult.Success)
        assertEquals(summary, (result as BackupResult.Success).data)
        coVerify { mockSerializer.fromCsv(csvContent, RestoreStrategy.MERGE) }
    }

    @Test
    fun `importFromCsv handles gzipped input`() = runTest {
        // Arrange
        val csvContent = "compressed,data"
        val tempFile = File(tempDir, "temp.gz")
        Files.write(tempFile.toPath(), csvContent.toByteArray())
        
        // Create an actual gzipped stream
        val gzippedFile = File(tempDir, "actual.gz")
        ZipUtils.compressFile(tempFile, gzippedFile)
        
        val mockInputStream = gzippedFile.inputStream()
        every { contentResolver.openInputStream(any()) } returns mockInputStream
        
        coEvery { mockSerializer.fromCsv(csvContent, any()) } returns mockk()
        
        // Ensure the config doesn't interfere, though import detects gzippedness automatically
        roomGuardLocal = RoomGuardLocal(context, mockSerializer)
        
        // Action
        roomGuardLocal.importFromCsv("content://test.gz", RestoreStrategy.OVERWRITE)
        
        // Assert
        coVerify { mockSerializer.fromCsv(csvContent, RestoreStrategy.OVERWRITE) }
    }
}
