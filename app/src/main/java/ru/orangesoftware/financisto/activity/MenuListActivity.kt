/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 *
 * Contributors:
 * Denis Solonenko - initial API and implementation
 * Abdsandryk Souza - implementing 2D chart reports
 */
package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.app.AlertDialog.Builder
import android.app.ListActivity
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.view.View
import android.widget.ListView
import android.widget.Toast
import com.google.android.gms.common.GooglePlayServicesUtil
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.Bean
import org.androidannotations.annotations.EActivity
import org.androidannotations.annotations.OnActivityResult
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import ru.orangesoftware.financisto.R.layout
import ru.orangesoftware.financisto.R.string
import ru.orangesoftware.financisto.adapter.SummaryEntityListAdapter
import ru.orangesoftware.financisto.bus.GreenRobotBus
import ru.orangesoftware.financisto.export.csv.CsvExportOptions
import ru.orangesoftware.financisto.export.csv.CsvImportOptions
import ru.orangesoftware.financisto.export.drive.DoDriveBackup
import ru.orangesoftware.financisto.export.drive.DoDriveListFiles
import ru.orangesoftware.financisto.export.drive.DoDriveRestore
import ru.orangesoftware.financisto.export.drive.DriveBackupError
import ru.orangesoftware.financisto.export.drive.DriveBackupFailure
import ru.orangesoftware.financisto.export.drive.DriveBackupSuccess
import ru.orangesoftware.financisto.export.drive.DriveConnectionFailed
import ru.orangesoftware.financisto.export.drive.DriveFileInfo
import ru.orangesoftware.financisto.export.drive.DriveFileList
import ru.orangesoftware.financisto.export.drive.DriveRestoreSuccess
import ru.orangesoftware.financisto.export.drive.DropboxFileList
import ru.orangesoftware.financisto.export.dropbox.DropboxBackupTask
import ru.orangesoftware.financisto.export.dropbox.DropboxListFilesTask
import ru.orangesoftware.financisto.export.dropbox.DropboxRestoreTask
import ru.orangesoftware.financisto.export.qif.QifExportOptions
import ru.orangesoftware.financisto.export.qif.QifImportOptions
import ru.orangesoftware.financisto.service.DailyAutoBackupScheduler
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection

@EActivity(layout.activity_menu_list)
open class MenuListActivity : ListActivity() {
    @JvmField
    @Bean
    var bus: GreenRobotBus? = null
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    @AfterViews
    protected fun init() {
        listAdapter = SummaryEntityListAdapter(this, MenuListItem.values())
    }

    override fun onListItemClick(
        l: ListView,
        v: View,
        position: Int,
        id: Long
    ) {
        MenuListItem.values()[position].call(this)
    }

    @OnActivityResult(MenuListItem.ACTIVITY_CSV_EXPORT)
    fun onCsvExportResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val options = CsvExportOptions.fromIntent(data)
            MenuListItem.doCsvExport(this, options)
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_QIF_EXPORT)
    fun onQifExportResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val options = QifExportOptions.fromIntent(data)
            MenuListItem.doQifExport(this, options)
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_CSV_IMPORT)
    fun onCsvImportResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val options = CsvImportOptions.fromIntent(data)
            MenuListItem.doCsvImport(this, options)
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_QIF_IMPORT)
    fun onQifImportResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val options = QifImportOptions.fromIntent(data)
            MenuListItem.doQifImport(this, options)
        }
    }

    @OnActivityResult(MenuListItem.ACTIVITY_CHANGE_PREFERENCES)
    fun onChangePreferences() {
        DailyAutoBackupScheduler.scheduleNextAutoBackup(this)
    }

    override fun onPause() {
        super.onPause()
        PinProtection.lock(this)
        bus!!.unregister(this)
    }

    override fun onResume() {
        super.onResume()
        PinProtection.unlock(this)
        bus!!.register(this)
    }

    var progressDialog: ProgressDialog? = null
    private fun dismissProgressDialog() {
        if (progressDialog != null) {
            progressDialog!!.dismiss()
            progressDialog = null
        }
    }

    // google drive
    @Subscribe(threadMode = MAIN)
    fun doGoogleDriveBackup(e: StartDriveBackup?) {
        progressDialog = ProgressDialog.show(
            this,
            null,
            getString(string.backup_database_gdocs_inprogress),
            true
        )
        bus!!.post(DoDriveBackup())
    }

    @Subscribe(threadMode = MAIN)
    fun doGoogleDriveRestore(e: StartDriveRestore?) {
        progressDialog =
            ProgressDialog.show(this, null, getString(string.google_drive_loading_files), true)
        bus!!.post(DoDriveListFiles())
    }

    @Subscribe(threadMode = MAIN)
    fun onDriveList(event: DriveFileList) {
        dismissProgressDialog()
        val files = event.files
        val fileNames = getFileNames(files)
        val context = this
        val selectedDriveFile = arrayOfNulls<DriveFileInfo>(1)
        Builder(context)
            .setTitle(string.restore_database_online_google_drive)
            .setPositiveButton(
                string.restore
            ) { dialog: DialogInterface?, which: Int ->
                if (selectedDriveFile[0] != null) {
                    progressDialog = ProgressDialog.show(
                        context,
                        null,
                        getString(string.google_drive_restore_in_progress),
                        true
                    )
                    bus!!.post(DoDriveRestore(selectedDriveFile[0]))
                }
            }
            .setSingleChoiceItems(
                fileNames,
                -1
            ) { dialog: DialogInterface?, which: Int ->
                if (which >= 0 && which < files.size) {
                    selectedDriveFile[0] = files[which]
                }
            }
            .show()
    }

    private fun getFileNames(files: List<DriveFileInfo>): Array<String?> {
        val names = arrayOfNulls<String>(files.size)
        for (i in files.indices) {
            names[i] = files[i].title
        }
        return names
    }

    @Subscribe(threadMode = MAIN)
    fun onDriveConnectionFailed(event: DriveConnectionFailed) {
        dismissProgressDialog()
        val connectionResult = event.connectionResult
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(
                    this,
                    RESOLVE_CONNECTION_REQUEST_CODE
                )
            } catch (e: SendIntentException) {
                // Unable to resolve, message user appropriately
                onDriveBackupError(DriveBackupError(e.message))
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(connectionResult.errorCode, this, 0).show()
        }
    }

    @Subscribe(threadMode = MAIN)
    fun onDriveBackupFailed(event: DriveBackupFailure) {
        dismissProgressDialog()
        val status = event.status
        if (status.hasResolution()) {
            try {
                status.startResolutionForResult(
                    this,
                    RESOLVE_CONNECTION_REQUEST_CODE
                )
            } catch (e: SendIntentException) {
                // Unable to resolve, message user appropriately
                onDriveBackupError(DriveBackupError(e.message))
            }
        } else {
            GooglePlayServicesUtil.getErrorDialog(status.statusCode, this, 0).show()
        }
    }

    @Subscribe(threadMode = MAIN)
    fun onDriveBackupSuccess(event: DriveBackupSuccess) {
        dismissProgressDialog()
        Toast.makeText(
            this,
            getString(string.google_drive_backup_success, event.fileName),
            Toast.LENGTH_LONG
        ).show()
    }

    @Subscribe(threadMode = MAIN)
    fun onDriveRestoreSuccess(event: DriveRestoreSuccess?) {
        dismissProgressDialog()
        Toast.makeText(this, string.restore_database_success, Toast.LENGTH_LONG).show()
    }

    @Subscribe(threadMode = MAIN)
    fun onDriveBackupError(event: DriveBackupError) {
        dismissProgressDialog()
        Toast.makeText(
            this,
            getString(string.google_drive_connection_failed, event.message),
            Toast.LENGTH_LONG
        ).show()
    }

    @OnActivityResult(RESOLVE_CONNECTION_REQUEST_CODE)
    fun onConnectionRequest(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, string.google_drive_connection_resolved, Toast.LENGTH_LONG)
                .show()
        }
    }

    // dropbox
    @Subscribe(threadMode = MAIN)
    fun doImportFromDropbox(event: DropboxFileList) {
        val backupFiles = event.files
        if (backupFiles != null) {
            val selectedDropboxFile = arrayOfNulls<String>(1)
            Builder(this)
                .setTitle(string.restore_database_online_dropbox)
                .setPositiveButton(
                    string.restore
                ) { dialog: DialogInterface?, which: Int ->
                    if (selectedDropboxFile[0] != null) {
                        val d = ProgressDialog.show(
                            this@MenuListActivity,
                            null,
                            getString(string.restore_database_inprogress_dropbox),
                            true
                        )
                        DropboxRestoreTask(
                            this@MenuListActivity,
                            d,
                            selectedDropboxFile[0]
                        ).execute()
                    }
                }
                .setSingleChoiceItems(
                    backupFiles,
                    -1
                ) { dialog: DialogInterface?, which: Int ->
                    if (which >= 0 && which < backupFiles.size) {
                        selectedDropboxFile[0] = backupFiles[which]
                    }
                }
                .show()
        }
    }

    @Subscribe(threadMode = MAIN)
    fun doDropboxBackup(e: StartDropboxBackup?) {
        val d = ProgressDialog.show(
            this,
            null,
            this.getString(string.backup_database_dropbox_inprogress),
            true
        )
        DropboxBackupTask(this, d).execute()
    }

    @Subscribe(threadMode = MAIN)
    fun doDropboxRestore(e: StartDropboxRestore?) {
        val d =
            ProgressDialog.show(this, null, this.getString(string.dropbox_loading_files), true)
        DropboxListFilesTask(this, d).execute()
    }

    class StartDropboxBackup
    class StartDropboxRestore
    class StartDriveBackup
    class StartDriveRestore
    companion object {
        private const val RESOLVE_CONNECTION_REQUEST_CODE = 1
    }
}
