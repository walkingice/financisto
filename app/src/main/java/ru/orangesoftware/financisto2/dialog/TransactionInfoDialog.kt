/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 * Denis Solonenko - initial API and implementation
 */
package ru.orangesoftware.financisto2.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.AccountType
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.model.TransactionInfo
import ru.orangesoftware.financisto.recur.Recurrence
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.Utils
import ru.orangesoftware.financisto.view.NodeInflater
import ru.orangesoftware.financisto2.blotter.BlotterOperations

class TransactionInfoDialog(
    private val context: Context,
    private val db: DatabaseAdapter,
    private val inflater: NodeInflater
) {
    private val layoutInflater: LayoutInflater
    private val splitPadding: Int
    private val u: Utils

    fun show(
        fragment: Fragment,
        transactionId: Long,
        callback: BlotterOperations.DeletionCallback
    ) {
        var ti = db.getTransactionInfo(transactionId)
        if (ti == null) {
            val context = fragment.requireContext()
            Toast.makeText(context, R.string.no_transaction_found, Toast.LENGTH_LONG).show()
            return
        }
        if (ti.parentId > 0) {
            ti = db.getTransactionInfo(ti.parentId)
        }
        val v = layoutInflater.inflate(R.layout.info_dialog, null)
        val layout = v.findViewById<LinearLayout>(R.id.list)
        val titleView = createTitleView(ti, layout)
        createMainInfoNodes(ti, layout)
        createAdditionalInfoNodes(ti, layout)
        showDialog(fragment, transactionId, v, titleView, callback)
    }

    private fun createMainInfoNodes(ti: TransactionInfo?, layout: LinearLayout) {
        if (ti!!.toAccount == null) {
            createLayoutForTransaction(ti, layout)
        } else {
            createLayoutForTransfer(ti, layout)
        }
    }

    private fun createLayoutForTransaction(
        ti: TransactionInfo?,
        layout: LinearLayout
    ) {
        val fromAccount = ti!!.fromAccount
        val formAccountType =
            AccountType.valueOf(ti.fromAccount.type)
        add(layout, R.string.account, ti.fromAccount.title, formAccountType)
        if (ti.payee != null) {
            add(layout, R.string.payee, ti.payee.title)
        }
        add(layout, R.string.category, ti.category.title)
        if (ti.originalCurrency != null) {
            val amount = add(layout, R.string.original_amount, "")
            u.setAmountText(amount, ti.originalCurrency, ti.originalFromAmount, true)
        }
        val amount = add(layout, R.string.amount, "")
        u.setAmountText(amount, ti.fromAccount.currency, ti.fromAmount, true)
        if (ti.category.isSplit) {
            val splits =
                db.getSplitsForTransaction(ti.id)
            for (split in splits) {
                addSplitInfo(layout, fromAccount, split)
            }
        }
    }

    private fun addSplitInfo(
        layout: LinearLayout,
        fromAccount: Account,
        split: Transaction
    ) {
        if (split.isTransfer) {
            val toAccount =
                db.getAccount(split.toAccountId)
            val title = u.getTransferTitleText(fromAccount, toAccount)
            val topLayout = add(layout, title, "")
            val amountView = topLayout.findViewById<TextView>(R.id.data)
            u.setTransferAmountText(
                amountView,
                fromAccount.currency,
                split.fromAmount,
                toAccount.currency,
                split.toAmount
            )
            topLayout.setPadding(splitPadding, 0, 0, 0)
        } else {
            val c =
                db.getCategoryWithParent(split.categoryId)
            val sb = StringBuilder()
            if (c != null && c.id > 0) {
                sb.append(c.title)
            }
            if (Utils.isNotEmpty(split.note)) {
                sb.append(" (").append(split.note).append(")")
            }
            val topLayout = add(layout, sb.toString(), "")
            val amountView = topLayout.findViewById<TextView>(R.id.data)
            u.setAmountText(amountView, fromAccount.currency, split.fromAmount, true)
            topLayout.setPadding(splitPadding, 0, 0, 0)
        }
    }

    private fun createLayoutForTransfer(ti: TransactionInfo?, layout: LinearLayout) {
        val fromAccountType =
            AccountType.valueOf(ti!!.fromAccount.type)
        add(layout, R.string.account_from, ti.fromAccount.title, fromAccountType)
        var amountView = add(layout, R.string.amount_from, "")
        u.setAmountText(amountView, ti.fromAccount.currency, ti.fromAmount, true)
        val toAccountType =
            AccountType.valueOf(ti.toAccount.type)
        add(layout, R.string.account_to, ti.toAccount.title, toAccountType)
        amountView = add(layout, R.string.amount_to, "")
        u.setAmountText(amountView, ti.toAccount.currency, ti.toAmount, true)
        if (MyPreferences.isShowPayeeInTransfers(context)) {
            add(layout, R.string.payee, if (ti.payee != null) ti.payee.title else "")
        }
        if (MyPreferences.isShowCategoryInTransferScreen(context)) {
            add(layout, R.string.category, if (ti.category != null) ti.category.title else "")
        }
    }

    private fun createAdditionalInfoNodes(ti: TransactionInfo?, layout: LinearLayout) {
        val attributes =
            db.getAttributesForTransaction(ti!!.id)
        for (tai in attributes) {
            val value = tai.getValue(context)
            if (Utils.isNotEmpty(value)) {
                add(layout, tai.name, value)
            }
        }
        val project = ti.project
        if (project != null && project.id > 0) {
            add(layout, R.string.project, project.title)
        }
        if (!Utils.isEmpty(ti.note)) {
            add(layout, R.string.note, ti.note)
        }
        val location = ti.location
        val locationName: String
        if (location != null && location.id > 0) {
            locationName =
                location.title + if (location.resolvedAddress != null) " (" + location.resolvedAddress + ")" else ""
            add(layout, R.string.location, locationName)
        }
    }

    private fun createTitleView(ti: TransactionInfo?, layout: LinearLayout): View {
        val titleView = layoutInflater.inflate(R.layout.info_dialog_title, null)
        val titleLabel = titleView.findViewById<TextView>(R.id.label)
        val titleData = titleView.findViewById<TextView>(R.id.data)
        val titleIcon =
            titleView.findViewById<ImageView>(R.id.icon)
        if (ti!!.isTemplate()) {
            titleLabel.text = ti.templateName
        } else {
            if (ti.isScheduled && ti.recurrence != null) {
                val r = Recurrence.parse(ti.recurrence)
                titleLabel.text = r.toInfoString(context)
            } else {
                val titleId =
                    if (ti.isSplitParent) R.string.split else if (ti.toAccount == null) R.string.transaction else R.string.transfer
                titleLabel.setText(titleId)
                add(
                    layout, R.string.date, DateUtils.formatDateTime(
                        context, ti.dateTime,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR
                    ),
                    ti.attachedPicture
                )
            }
        }
        val status = ti.status
        titleData.text = context.getString(status.titleId)
        titleIcon.setImageResource(status.iconId)
        return titleView
    }

    private fun showDialog(
        blotterFragment: Fragment,
        transactionId: Long,
        v: View,
        titleView: View,
        callback: BlotterOperations.DeletionCallback
    ) {
        val d: Dialog = AlertDialog.Builder(blotterFragment.requireContext())
            .setCustomTitle(titleView)
            .setView(v)
            .create()
        d.setCanceledOnTouchOutside(true)
        val bEdit = v.findViewById<Button>(R.id.bEdit)
        bEdit.setOnClickListener { arg0: View? ->
            d.dismiss()
            BlotterOperations(
                blotterFragment,
                db,
                transactionId,
                callback
            ).editTransaction()
        }
        val bClose = v.findViewById<Button>(R.id.bClose)
        bClose.setOnClickListener { arg0: View? -> d.dismiss() }
        d.show()
    }

    private fun add(
        layout: LinearLayout,
        labelId: Int,
        data: String,
        accountType: AccountType
    ) {
        inflater.Builder(layout, R.layout.select_entry_simple_icon)
            .withIcon(accountType.iconId).withLabel(labelId).withData(data).create()
    }

    private fun add(layout: LinearLayout, labelId: Int, data: String): TextView {
        val v =
            inflater.Builder(layout, R.layout.select_entry_simple).withLabel(labelId)
                .withData(data).create()
        return v.findViewById<View>(R.id.data) as TextView
    }

    private fun add(
        layout: LinearLayout,
        labelId: Int,
        data: String,
        pictureFileName: String?
    ) {
        val v = inflater.PictureBuilder(layout)
            .withPicture(context, pictureFileName)
            .withLabel(labelId)
            .withData(data)
            .create()
        v.isClickable = false
        v.isFocusable = false
        v.isFocusableInTouchMode = false
        val pictureView =
            v.findViewById<ImageView>(R.id.picture)
        pictureView.tag = pictureFileName
    }

    private fun add(layout: LinearLayout, label: String, data: String): LinearLayout {
        return inflater.Builder(layout, R.layout.select_entry_simple).withLabel(label)
            .withData(data).create() as LinearLayout
    }

    init {
        layoutInflater =
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        splitPadding =
            context.resources.getDimensionPixelSize(R.dimen.transaction_icon_padding)
        u = Utils(context)
    }
}
