/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
package ru.orangesoftware.financisto2.blotter

import android.app.AlertDialog
import android.content.Intent
import androidx.fragment.app.Fragment
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity
import ru.orangesoftware.financisto.activity.TransferActivity
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionStatus

class BlotterOperations(
    private val fragment: Fragment,
    private val db: DatabaseAdapter,
    transactionId: Long,
    private val callback: DeletionCallback
) {
    private var newFromTemplate = false
    private val originalTransaction: Transaction = db.getTransaction(transactionId)
    private var targetTransaction: Transaction

    init {
        targetTransaction = if (originalTransaction.isSplitChild) {
            db.getTransaction(originalTransaction.parentId)
        } else {
            originalTransaction
        }
    }

    fun asNewFromTemplate(): BlotterOperations {
        newFromTemplate = true
        return this
    }

    fun editTransaction() {
        val intent = getEditTransactionActivityIntent()
        val requestCode = if (targetTransaction.isTransfer) {
            EDIT_TRANSFER_REQUEST
        } else {
            EDIT_TRANSACTION_REQUEST
        }
        fragment.startActivityForResult(intent, requestCode)
    }

    fun getEditTransactionActivityIntent(): Intent {
        val intent = Intent(fragment.requireActivity(), TransferActivity::class.java)
        intent.putExtra(AbstractTransactionActivity.TRAN_ID_EXTRA, targetTransaction.id)
        intent.putExtra(AbstractTransactionActivity.DUPLICATE_EXTRA, false)
        intent.putExtra(AbstractTransactionActivity.NEW_FROM_TEMPLATE_EXTRA, newFromTemplate)
        return intent
    }

    fun deleteTransaction() {
        val titleId = when {
            targetTransaction.isTemplate() -> R.string.delete_template_confirm
            originalTransaction.isSplitChild -> R.string.delete_transaction_parent_confirm
            else -> R.string.delete_transaction_confirm
        }
        AlertDialog.Builder(fragment.requireContext())
            .setMessage(titleId)
            .setPositiveButton(R.string.yes) { arg0, arg1 ->
                val transactionIdToDelete = targetTransaction.id
                db.deleteTransaction(transactionIdToDelete)
                callback.onDeleteTransactionCompleted(transactionIdToDelete)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    fun duplicateTransaction(multiplier: Int): Long {
        val newId: Long
        newId = if (multiplier > 1) {
            db.duplicateTransactionWithMultiplier(targetTransaction.id, multiplier)
        } else {
            db.duplicateTransaction(targetTransaction.id)
        }
        return newId
    }

    fun duplicateAsTemplate() {
        db.duplicateTransactionAsTemplate(targetTransaction.id)
    }

    fun clearTransaction() {
        db.updateTransactionStatus(targetTransaction.id, TransactionStatus.CL)
    }

    fun reconcileTransaction() {
        db.updateTransactionStatus(targetTransaction.id, TransactionStatus.RC)
    }

    interface DeletionCallback {
        fun onDeleteTransactionCompleted(transactionId: Long)
    }

    companion object {
        private const val EDIT_TRANSACTION_REQUEST = 2
        private const val EDIT_TRANSFER_REQUEST = 4
    }
}
