/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package ru.orangesoftware.financisto.export.drive;

import android.content.Context;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EBean;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.backup.DatabaseExport;
import ru.orangesoftware.financisto.backup.DatabaseImport;
import ru.orangesoftware.financisto.bus.GreenRobotBus;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.export.ImportExportException;
import ru.orangesoftware.financisto.utils.MyPreferences;

import ru.orangesoftware.financisto.export.drive.GoogleDriveClientV3.ConnectionResult;

@EBean(scope = EBean.Scope.Singleton)
public class GoogleDriveClient {

    private final Context context;

    @Bean
    GreenRobotBus bus;

    @Bean
    DatabaseAdapter db;

    private GoogleDriveClientV3 googleDriveClient;

    GoogleDriveClient(Context context) {
        this.context = context.getApplicationContext();
    }

    @AfterInject
    public void init() {
        bus.register(this);
    }

    private ConnectionResult connect() throws ImportExportException {
        ConnectionResult result = ConnectionResult.Success.INSTANCE;
        if (googleDriveClient == null) {
            String googleDriveAccount = MyPreferences.getGoogleDriveAccount(context);
            if (googleDriveAccount == null) {
                throw new ImportExportException(R.string.google_drive_account_required);
            }

            GoogleDriveClientV3 client = new GoogleDriveClientV3(context);
            result = client.connect(googleDriveAccount);
            if (result == ConnectionResult.Success.INSTANCE) {
                googleDriveClient = client;
            }
        }
        return result;
    }

    public void disconnect() {
        googleDriveClient.disconnect();
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void doBackup(DoDriveBackup event) {
        try {
            DatabaseExport export = new DatabaseExport(context, db.db(), true);
            File file = export.exportToFile();
            ConnectionResult result = connect();
            if (result == ConnectionResult.Success.INSTANCE) {
                if (uploadFile(file)) {
                    handleSuccess(file.getName());
                } else {
                    handleFailure("upload fail");
                }
            } else {
                handleConnectionResult(result);
            }
        } catch (Exception e) {
            handleError(e);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void listFiles(DoDriveListFiles event) {
        try {
            ConnectionResult connectionResult = connect();
            if (connectionResult == ConnectionResult.Success.INSTANCE) {
                handleSuccess(googleDriveClient.listFiles());
            } else {
                handleConnectionResult(connectionResult);
            }
        } catch (Exception e) {
            handleError(e);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void doRestore(DoDriveRestore event) {
        try {
            ConnectionResult connectionResult = connect();
            if (connectionResult == ConnectionResult.Success.INSTANCE) {
                File cache = googleDriveClient.downloadFile(event.selectedDriveFile.driveId);
                if (cache == null) {
                    handleFailure("Failed on downloading file to cache");
                }
                FileInputStream stream = new FileInputStream(cache);
                DatabaseImport.createFromGoogleDriveBackup(context, db, stream).importDatabase();
                bus.post(new DriveRestoreSuccess());
            } else {
                handleConnectionResult(connectionResult);
            }
        } catch (Exception e) {
            handleError(e);
        }
    }

    private String getDriveFolderName() throws ImportExportException {
        String folder = MyPreferences.getBackupFolder(context);
        // check the backup folder registered on preferences
        if (folder == null || folder.equals("")) {
            throw new ImportExportException(R.string.gdocs_folder_not_configured);
        }

        return folder;
    }

    private void handleConnectionResult(ConnectionResult connectionResult) {
        bus.post(new DriveConnectionFailed(connectionResult));
    }

    private void handleError(Exception e) {
        if (e instanceof ImportExportException) {
            ImportExportException importExportException = (ImportExportException) e;
            bus.post(new DriveBackupError(context.getString(importExportException.errorResId)));
        } else {
            bus.post(new DriveBackupError(e.getMessage()));
        }
    }

    private void handleFailure(String message) {
        bus.post(new DriveBackupError(message));
    }

    private void handleSuccess(String fileName) {
        bus.post(new DriveBackupSuccess(fileName));
    }

    private void handleSuccess(List<DriveFileInfo> files) {
        if (files == null || files.size() == 0) {
            bus.post(new DriveBackupError("No backup files"));
        } else {
            bus.post(new DriveFileList(files));
        }
    }

    public boolean uploadFile(File file) throws ImportExportException {
        try {
            String targetFolder = getDriveFolderName();
            ConnectionResult connectionResult = connect();
            if (connectionResult == ConnectionResult.Success.INSTANCE) {
                return googleDriveClient.uploadFile(targetFolder, file);
            }
        } catch (Exception e) {
            throw new ImportExportException(R.string.google_drive_connection_failed, e);
        }
        return false;
    }
}
