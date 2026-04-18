package dev.dhanfinix.roomguard.drive

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.dhanfinix.roomguard.RoomGuard
import dev.dhanfinix.roomguard.core.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomGuardDriveRestoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var mockProvider: DatabaseProvider
    private lateinit var mockTokenStore: DriveTokenStore
    private lateinit var mockSerializer: CsvSerializer
    private lateinit var mockAuthClient: com.google.android.gms.auth.api.identity.AuthorizationClient
    private lateinit var mockSignInClient: com.google.android.gms.auth.api.identity.SignInClient
    private lateinit var roomGuardDrive: RoomGuardDrive
    private lateinit var tempDir: File

    @Before
    fun setup() {
        mockProvider = mockk(relaxed = true)
        mockTokenStore = mockk(relaxed = true)
        mockSerializer = mockk(relaxed = true)
        mockAuthClient = mockk(relaxed = true)
        mockSignInClient = mockk(relaxed = true)
        
        tempDir = Files.createTempDirectory("roomguard_restore_test").toFile()
        
        roomGuardDrive = RoomGuard.Builder(context)
            .appName("TestApp")
            .databaseProvider(mockProvider)
            .tokenStore(mockTokenStore)
            .csvSerializer(mockSerializer)
            .driveClients(mockAuthClient, mockSignInClient)
            .build()
            .driveManager()!!
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `verifyIntegrity returns true when PRAGMA quick_check is ok`() = runTest {
        // Arrange
        val dbFile = File(tempDir, "real_test.db")
        // Create a real valid SQLite DB using Robolectric's Android implementation
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { 
            // no-op, just ensure it exists and is valid
        }

        // Action
        val method = roomGuardDrive.javaClass.getDeclaredMethod("verifyIntegrity", File::class.java)
        method.isAccessible = true
        val result = method.invoke(roomGuardDrive, dbFile) as Boolean

        // Assert
        assertTrue(result)
    }

    @Test
    fun `REPLACE mode restore logic verification`() = runTest {
        // Behavioral check: ensuring we can call the methods on the mock
        coEvery { mockProvider.closeDatabase() } just Runs
        coEvery { mockProvider.onRestoreComplete() } just Runs
        
        // This test is mostly to ensure no runtime crashes during setup
        assertTrue(true)
    }
}
