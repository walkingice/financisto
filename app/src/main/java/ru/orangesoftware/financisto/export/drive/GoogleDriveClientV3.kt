package ru.orangesoftware.financisto.export.drive

import android.content.Context
import android.content.Intent
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import ru.orangesoftware.financisto.export.Export
import ru.orangesoftware.financisto2.storage.Backup
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import com.google.api.services.drive.model.File as GFile

private const val MIME_DIR = "application/vnd.google-apps.folder"

class GoogleDriveClientV3(val context: Context) {

    private val scopeAppData = DriveScopes.DRIVE_FILE
    private val scopes = arrayListOf(scopeAppData)

    private var driveService: Drive? = null

    fun connect(accountName: String): ConnectionResult {
        try {
            val service = createDriveService(accountName)
            // dummy api call to detect permission
            driveService = service
            listFiles()
        } catch (e: UserRecoverableAuthIOException) {
            driveService = null
            val intent = e.intent
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            return ConnectionResult.NeedPermission(intent)
        } catch (e: Exception) {
            driveService = null
            e.printStackTrace()
            return ConnectionResult.Error
        }

        return ConnectionResult.Success
    }

    fun disconnect() {
        driveService = null
    }

    fun uploadFile(folderName: String, file: File): Boolean {
        val driveService = this.driveService ?: return false
        val topFolder = queryOrCreateTopFolder(driveService, folderName) ?: return false
        val subFolder = queryOrCreateSubFolder(driveService, topFolder) ?: return false
        val metadata = GFile()
        metadata.name = file.name
        metadata.parents = listOf(subFolder.id)
        val content = FileContent(Export.BACKUP_MIME_TYPE, file)
        driveService.files()
            .create(metadata, content)
            .setFields("id")
            .execute()
        return true
    }

    fun downloadFile(driveFileId: String): File? {
        val driveService = this.driveService ?: return null
        val cacheName = "google-drive-cache.backup"
        val cacheFile = File(context.cacheDir, cacheName)
        val outputStream = FileOutputStream(cacheFile)
        driveService.files().get(driveFileId).executeMediaAndDownloadTo(outputStream)
        return cacheFile
    }

    fun listFiles(): List<DriveFileInfo>? {
        val driveService = this.driveService ?: return null
        val query = """
            mimeType='${Export.BACKUP_MIME_TYPE}'
            and name contains 'backup'
            and trashed=false
        """.trimIndent().replace('\n', ' ')
        // https://developers.google.com/resources/api-libraries/documentation/drive/v3/java/latest/com/google/api/services/drive/Drive.Files.List.html#setOrderBy-java.lang.String-
        // only return last 10 backup files
        val limit = 10
        val files = driveService.files().list()
            .setQ(query)
            .setFields("*")
            .setOrderBy("createdTime desc")
            .setPageSize(limit)
            .execute()
        if (files.files.size == 0) {
            return null
        }

        return files.files.map {
            val name = it.name
            val time = Date(it.createdTime.value)
            DriveFileInfo(it.id, name, time)
        }
    }

    private fun createDriveService(accountName: String): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(context, scopes)
            .setBackOff(ExponentialBackOff())
        credential.selectedAccountName = accountName
        val transport = NetHttpTransport()
        val jsonFactory = JacksonFactory.getDefaultInstance()
        // https://developers.google.com/resources/api-libraries/documentation/drive/v3/java/latest/index.html?com/google/api/services/drive/Drive.html
        return Drive.Builder(transport, jsonFactory, credential)
            .setApplicationName("Financisto")
            .build()
    }

    private fun queryOrCreateTopFolder(driveService: Drive, folderName: String): GFile? {
        val topFolder = queryTopFolder(driveService, folderName)
        return if (topFolder != null) {
            topFolder
        } else {
            // create then query again
            createFolder(driveService, folderName)
            queryTopFolder(driveService, folderName)
        }
    }

    private fun queryTopFolder(driveService: Drive, folderName: String): GFile? {
        // https://developers.google.com/drive/api/v3/search-files
        val query = """
            mimeType='$MIME_DIR'
            and name contains '$folderName'
            and 'root' in parents
            and trashed=false
        """.trimIndent().replace('\n', ' ')
        val files = driveService.files().list()
            .setQ(query)
            .setFields("*")
            .setOrderBy("createdTime desc")
            .execute()
        return if (files.files.size > 0) files.files[0] else null
    }

    private fun queryOrCreateSubFolder(
        driveService: Drive,
        topFolder: GFile
    ): GFile? {
        val subFolderName = Backup.generateDirName()
        val subFolder = querySubFolder(driveService, topFolder)
        return if (subFolder != null) {
            subFolder
        } else {
            // create then query again
            createFolder(driveService, subFolderName, topFolder)
            querySubFolder(driveService, topFolder)
        }
    }

    private fun querySubFolder(
        driveService: Drive,
        topFolder: GFile
    ): GFile? {
        val subFolderName = Backup.generateDirName()
        val query = """
            mimeType='$MIME_DIR'
            and name contains '$subFolderName'
            and '${topFolder.id}' in parents
            and trashed=false
        """.trimIndent().replace('\n', ' ')
        val files = driveService.files().list()
            .setQ(query)
            .setFields("*")
            .setOrderBy("createdTime desc")
            .execute()
        return if (files.files.size > 0) files.files[0] else null
    }

    private fun createFolder(
        driveService: Drive,
        folderName: String,
        parentFolder: GFile? = null
    ): GFile {
        val dir = GFile()
        dir.name = folderName
        dir.mimeType = MIME_DIR
        parentFolder?.let {
            dir.parents = listOf(it.id)
        }
        driveService.files().create(dir).setFields("id").execute()
        return dir
    }

    sealed class ConnectionResult {
        object Success : ConnectionResult()
        object Error : ConnectionResult()
        class NeedPermission(val intent: Intent) : ConnectionResult()
    }
}
