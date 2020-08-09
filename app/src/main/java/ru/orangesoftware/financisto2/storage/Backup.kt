package ru.orangesoftware.financisto2.storage

import java.text.SimpleDateFormat
import java.util.Date

object Backup {

    var sdf = SimpleDateFormat("yyyyMMdd_HHmmss")

    fun generateFilename(extension: String): String {
        return sdf.format(Date()) + extension
    }

}
