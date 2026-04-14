package dev.dhanfinix.roomguard.drive.token

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStoreDriveTokenStoreTest {

    private val testContext: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())
    
    private lateinit var tokenStore: DataStoreDriveTokenStore
    private lateinit var testFile: File

    @Before
    fun setup() {
        testFile = File(testContext.cacheDir, "test_datastore.preferences_pb")
        if (testFile.exists()) testFile.delete()
        
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        
        tokenStore = DataStoreDriveTokenStore(testContext, dataStore)
    }

    @After
    fun tearDown() {
        if (testFile.exists()) testFile.delete()
    }

    @Test
    fun `saveToken persists the token`() = runTest(testDispatcher) {
        // Arrange
        val token = "test-token"

        // Action
        tokenStore.saveToken(token)

        // Assert
        assertEquals(token, tokenStore.getToken())
    }

    @Test
    fun `isAuthorized returns correct state`() = runTest(testDispatcher) {
        // Action
        tokenStore.setAuthorized(true)

        // Assert
        assertTrue(tokenStore.isAuthorized())
    }

    @Test
    fun `clearToken removes the token`() = runTest(testDispatcher) {
        // Arrange
        tokenStore.saveToken("to-be-cleared")

        // Action
        tokenStore.clearToken()

        // Assert
        assertEquals(null, tokenStore.getToken())
    }
}
