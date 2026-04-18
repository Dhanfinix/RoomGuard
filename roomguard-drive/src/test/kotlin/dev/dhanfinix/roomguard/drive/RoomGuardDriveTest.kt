package dev.dhanfinix.roomguard.drive

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpResponseException
import dev.dhanfinix.roomguard.RoomGuard
import dev.dhanfinix.roomguard.core.DatabaseProvider
import dev.dhanfinix.roomguard.core.CsvSerializer
import dev.dhanfinix.roomguard.drive.token.DataStoreDriveTokenStore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

class RoomGuardDriveTest {

    private lateinit var drive: RoomGuardDrive
    private lateinit var mockContext: Context
    private lateinit var mockProvider: DatabaseProvider
    private lateinit var mockTokenStore: DriveTokenStore
    private lateinit var mockSerializer: CsvSerializer
    private lateinit var mockAuthClient: AuthorizationClient
    private lateinit var mockSignInClient: SignInClient

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockProvider = mockk(relaxed = true)
        mockTokenStore = mockk(relaxed = true)
        mockSerializer = mockk(relaxed = true)
        mockAuthClient = mockk(relaxed = true)
        mockSignInClient = mockk(relaxed = true)

        drive = RoomGuard.Builder(mockContext)
            .appName("TestApp")
            .databaseProvider(mockProvider)
            .tokenStore(mockTokenStore)
            .csvSerializer(mockSerializer)
            .driveClients(mockAuthClient, mockSignInClient)
            .build()
            .driveManager()!!
    }

    @Test
    fun `isAuthError detects 401 GoogleJsonResponseException`() {
        val httpException = HttpResponseException.Builder(401, "Unauthorized", HttpHeaders())
        val exception = GoogleJsonResponseException(httpException, null)
        
        val result = invokeIsAuthError(exception)
        assertTrue("Expected 401 to be recognized as auth error", result)
    }

    @Test
    fun `isAuthError detects IllegalStateException regarding refreshing tokens`() {
        val exception = IllegalStateException("OAuth2Credentials instance does not support refreshing the access token.")
        
        val result = invokeIsAuthError(exception)
        assertTrue("Expected refresh exception to be recognized as auth error", result)
    }

    @Test
    fun `isAuthError detects deeply nested auth errors in cause chain`() {
        val rootCause = IllegalStateException("OAuth2Credentials instance does not support refreshing")
        val wrapper1 = RuntimeException("Google API failed", rootCause)
        val wrapper2 = Exception("General error", wrapper1)
        
        val result = invokeIsAuthError(wrapper2)
        assertTrue("Expected nested auth error to be recognized", result)
    }

    @Test
    fun `isAuthError ignores unrelated exceptions`() {
        val exception = IllegalArgumentException("File not found")
        val result = invokeIsAuthError(exception)
        assertFalse("Expected unrelated error to be ignored", result)
    }

    // Helper to invoke private method for testing crucial error detection logic
    private fun invokeIsAuthError(exception: Throwable): Boolean {
        val function = RoomGuardDrive::class.declaredMemberFunctions.find { it.name == "isAuthError" }
        requireNotNull(function) { "isAuthError function not found" }
        function.isAccessible = true
        return function.call(drive, exception) as Boolean
    }
}
