package dev.dhanfinix.roomguard

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.SignInClient
import dev.dhanfinix.roomguard.core.CsvSerializer
import dev.dhanfinix.roomguard.core.DatabaseProvider
import dev.dhanfinix.roomguard.drive.DriveTokenStore
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

class RoomGuardBuilderTest {

    private lateinit var context: Context
    private lateinit var databaseProvider: DatabaseProvider
    private lateinit var tokenStore: DriveTokenStore
    private lateinit var csvSerializer: CsvSerializer
    private lateinit var authClient: AuthorizationClient
    private lateinit var signInClient: SignInClient

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        databaseProvider = mockk(relaxed = true)
        tokenStore = mockk(relaxed = true)
        csvSerializer = mockk(relaxed = true)
        authClient = mockk(relaxed = true)
        signInClient = mockk(relaxed = true)
    }

    @Test
    fun `builder reuses shared app name for drive and local defaults`() {
        val roomGuard = RoomGuard.Builder(context)
            .appName("My App")
            .databaseProvider(databaseProvider)
            .tokenStore(tokenStore)
            .csvSerializer(csvSerializer)
            .driveClients(authClient, signInClient)
            .build()

        assertEquals("My App", readPrivateString(roomGuard.driveManager, "appName"))
        assertEquals("My App_backup", readPrivateString(roomGuard.localManager, "filePrefix"))
    }

    @Test
    fun `builder allows local prefix override`() {
        val roomGuard = RoomGuard.Builder(context)
            .appName("My App")
            .databaseProvider(databaseProvider)
            .tokenStore(tokenStore)
            .csvSerializer(csvSerializer)
            .localFilePrefix("custom_backup")
            .driveClients(authClient, signInClient)
            .build()

        assertEquals("custom_backup", readPrivateString(roomGuard.localManager, "filePrefix"))
    }

    @Test
    fun `builder validates required values`() {
        assertThrows(IllegalArgumentException::class.java) {
            RoomGuard.Builder(context)
                .databaseProvider(databaseProvider)
                .tokenStore(tokenStore)
                .csvSerializer(csvSerializer)
                .driveClients(authClient, signInClient)
                .build()
        }

        assertThrows(IllegalArgumentException::class.java) {
            RoomGuard.Builder(context)
                .appName("My App")
                .databaseProvider(databaseProvider)
                .tokenStore(tokenStore)
                .driveClients(authClient, signInClient)
                .build()
        }
    }

    private fun readPrivateString(target: Any, fieldName: String): String {
        val field: Field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target) as String
    }
}
