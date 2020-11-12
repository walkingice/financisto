package ru.orangesoftware.financisto.export.drive;

import java.util.Date;

public class DriveFileInfo implements Comparable<DriveFileInfo> {

    public final String driveId;
    public final String title;
    public final Date createdDate;

    public DriveFileInfo(String driveId, String title, Date createdDate) {
        this.driveId = driveId;
        this.title = title;
        this.createdDate = createdDate;
    }

    @Override
    public int compareTo(DriveFileInfo another) {
        return another.createdDate.compareTo(this.createdDate);
    }

    @Override
    public String toString() {
        return title;
    }

}
