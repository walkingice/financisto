/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionStatus

class BlotterOperations(
    private val activity: Activity,
    private val db: DatabaseAdapter,
    transactionId: Long,
    private val callback: DeletionCallback
) {
    private val originalTransaction: Transaction
    private var targetTransaction: Transaction? = null
    private var newFromTemplate = false

    init {
        originalTransaction = db.getTransaction(transactionId)
        if (originalTransaction.isSplitChild) {
            targetTransaction = db.getTransaction(originalTransaction.parentId)
        } else {
            targetTransaction = originalTransaction
        }
    }

    fun asNewFromTemplate(): BlotterOperations {
        newFromTemplate = true
        return this
    }

    fun editTransaction() {
        if (targetTransaction!!.isTransfer) {
            startEditTransactionActivity(
                TransferActivity::class.java,
                EDIT_TRANSFER_REQUEST
            )
        } else {
            startEditTransactionActivity(
                TransactionActivity::class.java,
                EDIT_TRANSACTION_REQUEST
            )
        }
    }

    private fun startEditTransactionActivity(
        activityClass: Class<out Activity>,
        requestCode: Int
    ) {
        val intent = Intent(activity, activityClass)
        intent.putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, targetTransaction!!.id)
        intent.putExtra(AbstractTransactionActivity.DUPLICATE_EXTRA, false)
        intent.putExtra(AbstractTransactionActivity.NEW_FROM_TEMPLATE_EXTRA, newFromTemplate)
        activity.startActivityForResult(intent, requestCode)
    }

    fun deleteTransaction() {
        val titleId =
            if (targetTransaction!!.isTemplate()) R.string.delete_template_confirm else if (originalTransaction.isSplitChild) R.string.delete_transaction_parent_confirm else R.string.delete_transaction_confirm
        AlertDialog.Builder(activity)
            .setMessage(titleId)
            .setPositiveButton(R.string.yes) { arg0, arg1 ->
                val transactionIdToDelete = targetTransaction!!.id
                db.deleteTransaction(transactionIdToDelete)
                callback.onDeleteTransaction(transactionIdToDelete)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    fun duplicateTransaction(multiplier: Int): Long {
        val newId: Long
        newId = if (multiplier > 1) {
            db.duplicateTransactionWithMultiplier(targetTransaction!!.id, multiplier)
        } else {
            db.duplicateTransaction(targetTransaction!!.id)
        }
        return newId
    }

    fun duplicateAsTemplate() {
        db.duplicateTransactionAsTemplate(targetTransaction!!.id)
    }

    fun clearTransaction() {
        db.updateTransactionStatus(targetTransaction!!.id, TransactionStatus.CL)
    }

    fun reconcileTransaction() {
        db.updateTransactionStatus(targetTransaction!!.id, TransactionStatus.RC)
    }

    interface DeletionCallback {
        fun onDeleteTransaction(transactionId: Long)
    }

    companion object {
        private const val EDIT_TRANSACTION_REQUEST = 2
        private const val EDIT_TRANSFER_REQUEST = 4
    }
}
