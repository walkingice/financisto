package ru.orangesoftware.financisto.export.drive;

public class DriveConnectionFailed {

    public final GoogleDriveClientV3.ConnectionResult connectionResult;

    public DriveConnectionFailed(GoogleDriveClientV3.ConnectionResult connectionResult) {
        this.connectionResult = connectionResult;
    }
}
