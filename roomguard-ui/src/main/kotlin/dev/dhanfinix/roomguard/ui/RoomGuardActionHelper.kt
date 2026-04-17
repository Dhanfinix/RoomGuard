package dev.dhanfinix.roomguard.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Helper to handle platform-specific actions like Sharing and File Provider logic.
 *
 * This abstracts the boilerplate away from the UI components.
 */
internal object RoomGuardActionHelper {

    /**
     * Default authority for RoomGuard's internal FileProvider.
     * Matches the one defined in roomguard-ui/AndroidManifest.xml.
     */
    private fun getAuthority(context: Context): String = "${context.packageName}.roomguard.fileprovider"

    /**
     * Creates and starts a Chooser intent to share a local file.
     */
    fun shareFile(context: Context, filePath: String, mimeType: String, title: String = "Share Backup") {
        try {
            val file = File(filePath)
            val uri = FileProvider.getUriForFile(context, getAuthority(context), file)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            // In a real app, you might want to log this or show a toast
            e.printStackTrace()
        }
    }
}
