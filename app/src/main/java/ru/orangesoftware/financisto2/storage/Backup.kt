package ru.orangesoftware.financisto2.storage

import android.content.Context
import android.os.Environment
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.utils.MyPreferences
import java.io.File
import java.io.FilenameFilter
import java.text.SimpleDateFormat
import java.util.Date

object Backup {
    @kotlin.jvm.JvmField
    val BACKUP_TABLES = arrayOf(
        DatabaseHelper.ACCOUNT_TABLE,
        DatabaseHelper.ATTRIBUTES_TABLE,
        DatabaseHelper.CATEGORY_ATTRIBUTE_TABLE,
        DatabaseHelper.TRANSACTION_ATTRIBUTE_TABLE,
        DatabaseHelper.BUDGET_TABLE,
        DatabaseHelper.CATEGORY_TABLE,
        DatabaseHelper.CURRENCY_TABLE,
        DatabaseHelper.LOCATIONS_TABLE,
        DatabaseHelper.PROJECT_TABLE,
        DatabaseHelper.TRANSACTION_TABLE,
        DatabaseHelper.PAYEE_TABLE,
        DatabaseHelper.CCARD_CLOSING_DATE_TABLE,
        DatabaseHelper.SMS_TEMPLATES_TABLE,
        "split",  /* todo: seems not used, found only in old 20110422_0051_create_split_table.sql, should be removed then */
        DatabaseHelper.EXCHANGE_RATES_TABLE
    )

    @kotlin.jvm.JvmField
    val BACKUP_TABLES_WITH_SYSTEM_IDS = arrayOf(
        DatabaseHelper.ATTRIBUTES_TABLE,
        DatabaseHelper.CATEGORY_TABLE,
        DatabaseHelper.PROJECT_TABLE,
        DatabaseHelper.LOCATIONS_TABLE
    )

    @kotlin.jvm.JvmField
    val BACKUP_TABLES_WITH_SORT_ORDER = arrayOf(
        DatabaseHelper.ACCOUNT_TABLE,
        DatabaseHelper.SMS_TEMPLATES_TABLE,
        DatabaseHelper.PROJECT_TABLE,
        DatabaseHelper.PAYEE_TABLE,
        DatabaseHelper.BUDGET_TABLE,
        DatabaseHelper.CURRENCY_TABLE,
        DatabaseHelper.LOCATIONS_TABLE,
        DatabaseHelper.ATTRIBUTES_TABLE
    )

    @kotlin.jvm.JvmField
    val RESTORE_SCRIPTS = arrayOf(
        "20100114_1158_alter_accounts_types.sql",
        "20110903_0129_alter_template_splits.sql",
        "20171230_1852_alter_electronic_account_type.sql"
    )

    @kotlin.jvm.JvmField
    val DEFAULT_EXPORT_PATH: File = Environment.getExternalStoragePublicDirectory("financisto")

    private val fileNameDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
    private val fileDirDateFormat = SimpleDateFormat("yyyyMM")

    private val fileNameFilter = FilenameFilter { _, fileName -> fileName.endsWith(".backup") }

    fun generateFilename(extension: String): String {
        return fileNameDateFormat.format(Date()) + extension
    }

    fun generateDirName(): String = fileDirDateFormat.format(Date())

    fun getBackupFolder(context: Context?, dirByMonth: Boolean = false): File? {
        val rootFolder = getBackupRootFolder(context)
        return if (dirByMonth) {
            val subFolder = File(rootFolder, generateDirName())
            subFolder.mkdirs()
            subFolder
        } else {
            rootFolder
        }
    }

    fun getBackupRootFolder(context: Context?): File? {
        val path = MyPreferences.getDatabaseBackupFolder(context)
        var file = File(path)
        file.mkdirs()
        if (file.isDirectory && file.canWrite()) {
            return file
        }
        file = DEFAULT_EXPORT_PATH
        file.mkdirs()
        return file
    }

    fun createBackupTargetByExtension(
        context: Context?,
        extension: String,
        dirByMonth: Boolean = false
    ): File? {
        val fileName = generateFilename(extension)
        val dir = getBackupFolder(context, dirByMonth)
        return File(dir, fileName)
    }

    fun findBackupFileByName(context: Context?, backupFileName: String?): File? {
        val files = getFlatBackupFilesList(context)
        return files.find { it.name == backupFileName }
    }

    fun listBackups(context: Context): List<File> {
        return getFlatBackupFilesList(context)
    }

    private fun getFlatBackupFilesList(context: Context?): List<File> {
        context ?: return emptyList()
        val rootDir = getBackupRootFolder(context) ?: return emptyList()
        val files = mutableListOf<File>()
        // files in level 0 dir
        files.addAll(rootDir.listFiles(fileNameFilter))
        // files in level 1 dir
        val dirs = rootDir.listFiles().filter { it.isDirectory && it.canRead() }
        dirs.forEach { subDir ->
            files.addAll(subDir.listFiles(fileNameFilter))
        }
        return files.sortedByDescending { it.name }
    }

    fun tableHasSystemIds(tableName: String?): Boolean {
        for (table in BACKUP_TABLES_WITH_SYSTEM_IDS) {
            if (table.equals(tableName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun tableHasOrder(tableName: String?): Boolean {
        for (table in BACKUP_TABLES_WITH_SORT_ORDER) {
            if (table.equals(tableName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

}
