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
package ru.orangesoftware.financisto2.transaction

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import greendroid.widget.QuickActionGrid
import greendroid.widget.QuickActionWidget
import greendroid.widget.QuickActionWidget.OnQuickActionClickListener
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.MyQuickAction
import ru.orangesoftware.financisto.activity.SplitTransactionActivity
import ru.orangesoftware.financisto.activity.SplitTransferActivity
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.MyEntity
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.SplitAdjuster
import ru.orangesoftware.financisto.utils.TransactionUtils
import ru.orangesoftware.financisto.utils.Utils
import ru.orangesoftware.financisto2.activity.CategorySelector
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.ArrayList
import java.util.IdentityHashMap
import java.util.LinkedList

class TransactionActivity : AbstractTransactionActivity() {
    private val currencyAsAccount = Currency()
    private var idSequence: Long = 0
    private val viewToSplitMap = IdentityHashMap<View?, Transaction>()
    private var differenceText: TextView? = null
    private var isUpdateBalanceMode = false
    private var currentBalance: Long = 0
    private var util: Utils? = null
    private var splitsLayout: LinearLayout? = null
    private var unsplitAmountText: TextView? = null
    private var currencyText: TextView? = null
    private lateinit var unsplitActionGrid: QuickActionWidget
    private var selectedOriginCurrencyId: Long = -1
    override fun getLayoutId(): Int {
        return if (MyPreferences.isUseFixedLayout(this))
            R.layout.activity_transaction_fixed
        else
            R.layout.activity_transaction_free
    }

    override fun internalOnCreate() {
        util = Utils(this)
        intent?.let { intent ->
            if (intent.hasExtra(CURRENT_BALANCE_EXTRA)) {
                currentBalance = intent.getLongExtra(CURRENT_BALANCE_EXTRA, 0)
                isUpdateBalanceMode = true
            } else if (intent.hasExtra(AMOUNT_EXTRA)) {
                currentBalance = intent.getLongExtra(AMOUNT_EXTRA, 0)
            }
        }
        if (transaction.isTemplateLike) {
            setTitle(if (transaction.isTemplate()) R.string.transaction_template else R.string.transaction_schedule)
            if (transaction.isTemplate()) {
                dateText.isEnabled = false
                timeText.isEnabled = false
            }
        }
        prepareUnsplitActionGrid()
        currencyAsAccount.name = getString(R.string.original_currency_as_account)
    }

    private fun prepareUnsplitActionGrid() {
        unsplitActionGrid = QuickActionGrid(this)
        unsplitActionGrid.addQuickAction(
            MyQuickAction(this, R.drawable.ic_action_add, R.string.transaction)
        )
        unsplitActionGrid.addQuickAction(
            MyQuickAction(this, R.drawable.ic_action_transfer, R.string.transfer)
        )
        unsplitActionGrid.addQuickAction(
            MyQuickAction(this, R.drawable.ic_action_tick, R.string.unsplit_adjust_amount)
        )
        unsplitActionGrid.addQuickAction(
            MyQuickAction(this, R.drawable.ic_action_tick, R.string.unsplit_adjust_evenly)
        )
        unsplitActionGrid.addQuickAction(
            MyQuickAction(this, R.drawable.ic_action_tick, R.string.unsplit_adjust_last)
        )
        unsplitActionGrid.setOnQuickActionClickListener(unsplitActionListener)
    }

    private val unsplitActionListener =
        OnQuickActionClickListener { widget: QuickActionWidget?, position: Int ->
            when (position) {
                0 -> createSplit(false)
                1 -> createSplit(true)
                2 -> unsplitAdjustAmount()
                3 -> unsplitAdjustEvenly()
                4 -> unsplitAdjustLast()
            }
        }

    private fun unsplitAdjustAmount() {
        val splitAmount = calculateSplitAmount()
        rateView.fromAmount = splitAmount
        updateUnsplitAmount()
    }

    private fun unsplitAdjustEvenly() {
        val unsplitAmount = calculateUnsplitAmount()
        if (unsplitAmount != 0L) {
            val splits: List<Transaction> =
                ArrayList(viewToSplitMap.values)
            SplitAdjuster.adjustEvenly(splits, unsplitAmount)
            updateSplits()
        }
    }

    private fun unsplitAdjustLast() {
        val unsplitAmount = calculateUnsplitAmount()
        if (unsplitAmount != 0L) {
            var latestTransaction: Transaction? = null
            for (t in viewToSplitMap.values) {
                if (latestTransaction == null || latestTransaction.id > t.id) {
                    latestTransaction = t
                }
            }
            if (latestTransaction != null) {
                SplitAdjuster.adjustSplit(latestTransaction, unsplitAmount)
                updateSplits()
            }
        }
    }

    private fun updateSplits() {
        for ((v, split) in viewToSplitMap) {
            setSplitData(v, split)
        }
        updateUnsplitAmount()
    }

    override fun fetchCategories() {
        categorySelector.fetchCategories(!isUpdateBalanceMode)
    }

    override fun createListNodes(layout: LinearLayout) {
        //account
        accountText = x.addListNode(layout, R.id.account, R.string.account, R.string.select_account)
        //payee
        isShowPayee = MyPreferences.isShowPayee(this)
        if (isShowPayee) {
            createPayeeNode(layout)
        }
        //category
        categorySelector.createNode(layout, CategorySelector.SelectorType.TRANSACTION)
        //amount
        currencyText = if (!isUpdateBalanceMode && MyPreferences.isShowCurrency(this)) {
            x.addListNode(
                layout,
                R.id.original_currency,
                R.string.currency,
                R.string.original_currency_as_account
            )
        } else {
            TextView(this)
        }
        rateView.createTransactionUI()
        // difference
        if (isUpdateBalanceMode) {
            differenceText = x.addInfoNode(layout, -1, R.string.difference, "0")
            rateView.fromAmount = currentBalance
            rateView.setAmountFromChangeListener { oldAmount: Long, newAmount: Long ->
                val balanceDifference = newAmount - currentBalance
                util!!.setAmountText(differenceText, rateView.currencyFrom, balanceDifference, true)
            }
            if (currentBalance > 0) {
                rateView.setIncome()
            } else {
                rateView.setExpense()
            }
        } else {
            if (currentBalance > 0) {
                rateView.setIncome()
            } else {
                rateView.setExpense()
            }
            createSplitsLayout(layout)
            rateView.setAmountFromChangeListener { oldAmount: Long, newAmount: Long -> updateUnsplitAmount() }
        }
    }

    private fun selectLastCategoryForPayee(id: Long) {
        val p = db.get(Payee::class.java, id)
        if (p != null) {
            categorySelector.selectCategory(p.lastCategoryId)
        }
    }

    private fun createSplitsLayout(layout: LinearLayout) {
        splitsLayout = LinearLayout(this)
        splitsLayout!!.orientation = LinearLayout.VERTICAL
        layout.addView(
            splitsLayout,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    override fun addOrRemoveSplits() {
        if (splitsLayout == null) {
            return
        }
        if (categorySelector.isSplitCategorySelected) {
            val v = x.addNodeUnsplit(splitsLayout)
            unsplitAmountText = v.findViewById(R.id.data)
            updateUnsplitAmount()
        } else {
            splitsLayout!!.removeAllViews()
        }
    }

    private fun updateUnsplitAmount() {
        if (unsplitAmountText != null) {
            val amountDifference = calculateUnsplitAmount()
            util!!.setAmountText(unsplitAmountText, rateView.currencyFrom, amountDifference, false)
        }
    }

    private fun calculateUnsplitAmount(): Long {
        val splitAmount = calculateSplitAmount()
        return rateView.fromAmount - splitAmount
    }

    private fun calculateSplitAmount(): Long {
        var amount: Long = 0
        for (split in viewToSplitMap.values) {
            amount += split.fromAmount
        }
        return amount
    }

    override fun switchIncomeExpenseButton(category: Category) {
        if (!isUpdateBalanceMode) {
            if (category.isIncome) {
                rateView.setIncome()
            } else {
                rateView.setExpense()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {
            accountText.requestFocusFromTouch()
        }
    }

    override fun onOKClicked(): Boolean {
        if (checkSelectedAccount() && checkUnsplitAmount() && checkSelectedEntities()) {
            updateTransactionFromUI()
            return true
        }
        return false
    }

    private fun checkSelectedAccount(): Boolean {
        return checkSelectedId(selectedAccountId, R.string.select_account)
    }

    private fun checkUnsplitAmount(): Boolean {
        if (categorySelector.isSplitCategorySelected) {
            val unsplitAmount = calculateUnsplitAmount()
            if (unsplitAmount != 0L) {
                Toast.makeText(this, R.string.unsplit_amount_greater_than_zero, Toast.LENGTH_LONG)
                    .show()
                return false
            }
        }
        return true
    }

    override fun editTransaction(transaction: Transaction) {
        selectAccount(transaction.fromAccountId, false)
        commonEditTransaction(transaction)
        selectCurrency(transaction)
        fetchSplits()
        selectPayee(transaction.payeeId)
    }

    private fun selectCurrency(transaction: Transaction) {
        if (transaction.originalCurrencyId > 0) {
            selectOriginalCurrency(transaction.originalCurrencyId)
            rateView.fromAmount = transaction.originalFromAmount
            rateView.toAmount = transaction.fromAmount
        } else {
            if (transaction.fromAmount != 0L) {
                rateView.fromAmount = transaction.fromAmount
            }
        }
    }

    private fun fetchSplits() {
        val splits =
            db.getSplitsForTransaction(transaction.id)
        for (split in splits) {
            split.categoryAttributes = db.getAllAttributesForTransaction(split.id)
            if (split.originalCurrencyId > 0) {
                split.fromAmount = split.originalFromAmount
            }
            addOrEditSplit(split)
        }
    }

    private fun updateTransactionFromUI() {
        updateTransactionFromUI(transaction)
        transaction.fromAccountId = selectedAccount!!.id
        var amount = rateView.fromAmount
        if (isUpdateBalanceMode) {
            amount -= currentBalance
        }
        transaction.fromAmount = amount
        updateTransactionOriginalAmount()
        if (categorySelector.isSplitCategorySelected) {
            transaction.splits =
                LinkedList(viewToSplitMap.values)
        } else {
            transaction.splits = null
        }
    }

    private fun updateTransactionOriginalAmount() {
        if (isDifferentCurrency) {
            transaction.originalCurrencyId = selectedOriginCurrencyId
            transaction.originalFromAmount = rateView.fromAmount
            transaction.fromAmount = rateView.toAmount
        } else {
            transaction.originalCurrencyId = 0
            transaction.originalFromAmount = 0
        }
    }

    private val isDifferentCurrency: Boolean
        private get() = selectedOriginCurrencyId > 0 && selectedOriginCurrencyId != selectedAccount!!.currency.id

    override fun selectAccount(
        accountId: Long,
        selectLast: Boolean
    ): Account {
        val a = super.selectAccount(accountId, selectLast)
        if (a != null) {
            if (selectLast && !isShowPayee && isRememberLastCategory) {
                categorySelector.selectCategory(a.lastCategoryId)
            }
        }
        if (selectedOriginCurrencyId > 0) {
            selectOriginalCurrency(selectedOriginCurrencyId)
        }
        return a!!
    }

    override fun onClick(v: View, id: Int) {
        super.onClick(v, id)
        when (id) {
            R.id.unsplit_action -> unsplitActionGrid!!.show(v)
            R.id.add_split -> createSplit(false)
            R.id.add_split_transfer -> {
                if (selectedOriginCurrencyId > 0) {
                    Toast.makeText(
                        this,
                        R.string.split_transfer_not_supported_yet,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    createSplit(true)
                }
            }
            R.id.delete_split -> {
                val parentView = v.parent as View
                deleteSplit(parentView)
            }
            R.id.original_currency -> {
                val currencies =
                    db.allCurrenciesList
                currencies.add(0, currencyAsAccount)
                val adapter =
                    TransactionUtils.createCurrencyAdapter(this, currencies)
                val selectedPos = MyEntity.indexOf(currencies, selectedOriginCurrencyId)
                x.selectItemId(this, R.id.currency, R.string.currency, adapter, selectedPos)
            }
        }
        val split = viewToSplitMap[v]
        if (split != null) {
            split.unsplitAmount = split.fromAmount + calculateUnsplitAmount()
            editSplit(
                split,
                if (split.toAccountId > 0) SplitTransferActivity::class.java else SplitTransactionActivity::class.java
            )
        }
    }

    override fun onSelectedPos(id: Int, selectedPos: Int) {
        super.onSelectedPos(id, selectedPos)
        when (id) {
            R.id.payee -> if (isRememberLastCategory) {
                selectLastCategoryForPayee(payeeSelector!!.selectedEntityId)
            }
        }
    }

    override fun onSelectedId(id: Int, selectedId: Long) {
        super.onSelectedId(id, selectedId)
        when (id) {
            R.id.currency -> selectOriginalCurrency(selectedId)
            R.id.payee -> if (isRememberLastCategory) {
                selectLastCategoryForPayee(selectedId)
            }
        }
    }

    private fun selectOriginalCurrency(selectedId: Long) {
        selectedOriginCurrencyId = selectedId
        if (selectedId == -1L) {
            if (selectedAccount != null) {
                if (selectedAccount!!.currency.id == rateView.currencyToId) {
                    rateView.fromAmount = rateView.toAmount
                }
            }
            selectAccountCurrency()
        } else {
            val toAmount = rateView.toAmount
            val currency =
                CurrencyCache.getCurrency(db, selectedId)
            rateView.selectCurrencyFrom(currency)
            if (selectedAccount != null) {
                if (selectedId == selectedAccount!!.currency.id) {
                    if (selectedId == rateView.currencyToId) {
                        rateView.fromAmount = toAmount
                    }
                    selectAccountCurrency()
                    return
                }
                rateView.selectCurrencyTo(selectedAccount!!.currency)
            }
            currencyText!!.text = currency.name
        }
    }

    private fun selectAccountCurrency() {
        rateView.selectSameCurrency(if (selectedAccount != null) selectedAccount!!.currency else Currency.EMPTY)
        currencyText!!.setText(R.string.original_currency_as_account)
    }

    private fun createSplit(asTransfer: Boolean) {
        val split =
            Transaction()
        split.id = --idSequence
        split.fromAccountId = selectedAccountId
        split.unsplitAmount = calculateUnsplitAmount()
        split.fromAmount = split.unsplitAmount
        split.originalCurrencyId = selectedOriginCurrencyId
        editSplit(
            split,
            if (asTransfer) SplitTransferActivity::class.java else SplitTransactionActivity::class.java
        )
    }

    private fun editSplit(
        split: Transaction,
        splitActivityClass: Class<*>
    ) {
        val intent = Intent(this, splitActivityClass)
        split.toIntentAsSplit(intent)
        startActivityForResult(intent, SPLIT_REQUEST)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPLIT_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                val split = Transaction.fromIntentAsSplit(data)
                addOrEditSplit(split)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.d("Financisto", "onSaveInstanceState")
        try {
            if (categorySelector.isSplitCategorySelected) {
                Log.d("Financisto", "Saving splits...")
                val state =
                    ActivityState()
                state.categoryId = categorySelector.selectedCategoryId
                state.idSequence = idSequence
                state.splits = ArrayList(
                    viewToSplitMap.values
                )
                ByteArrayOutputStream().use { s ->
                    val out = ObjectOutputStream(s)
                    out.writeObject(state)
                    outState.putByteArray(
                        ACTIVITY_STATE,
                        s.toByteArray()
                    )
                }
            }
        } catch (e: IOException) {
            Log.e("Financisto", "Unable to save state", e)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Log.d("Financisto", "onRestoreInstanceState")
        val bytes =
            savedInstanceState.getByteArray(ACTIVITY_STATE)
        if (bytes != null) {
            try {
                ByteArrayInputStream(bytes).use { s ->
                    val `in` = ObjectInputStream(s)
                    val state =
                        `in`.readObject() as ActivityState
                    if (state.categoryId == Category.SPLIT_CATEGORY_ID) {
                        Log.d("Financisto", "Restoring splits...")
                        viewToSplitMap.clear()
                        splitsLayout!!.removeAllViews()
                        idSequence = state.idSequence
                        categorySelector.selectCategory(state.categoryId)
                        for (split in state.splits!!) {
                            addOrEditSplit(split)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Financisto", "Unable to restore state", e)
            }
        }
    }

    private fun addOrEditSplit(split: Transaction) {
        var v = findView(split)
        if (v == null) {
            v = x.addSplitNodeMinus(
                splitsLayout,
                R.id.edit_aplit,
                R.id.delete_split,
                R.string.split,
                ""
            )
        }
        setSplitData(v, split)
        viewToSplitMap[v] = split
        updateUnsplitAmount()
    }

    private fun findView(split: Transaction): View? {
        for ((key, s) in viewToSplitMap) {
            if (s.id == split.id) {
                return key
            }
        }
        return null
    }

    private fun setSplitData(
        v: View?,
        split: Transaction
    ) {
        val label = v!!.findViewById<TextView>(R.id.label)
        val data = v.findViewById<TextView>(R.id.data)
        setSplitData(split, label, data)
    }

    private fun setSplitData(
        split: Transaction,
        label: TextView,
        data: TextView
    ) {
        if (split.isTransfer) {
            setSplitDataTransfer(split, label, data)
        } else {
            setSplitDataTransaction(split, label, data)
        }
    }

    private fun setSplitDataTransaction(
        split: Transaction,
        label: TextView,
        data: TextView
    ) {
        label.text = createSplitTransactionTitle(split)
        val currency = currency
        util!!.setAmountText(data, currency, split.fromAmount, false)
    }

    private fun createSplitTransactionTitle(split: Transaction): String {
        val sb = StringBuilder()
        val category =
            db.getCategoryWithParent(split.categoryId)
        sb.append(category.title)
        if (Utils.isNotEmpty(split.note)) {
            sb.append(" (").append(split.note).append(")")
        }
        return sb.toString()
    }

    private fun setSplitDataTransfer(
        split: Transaction,
        label: TextView,
        data: TextView
    ) {
        val fromAccount = db.getAccount(split.fromAccountId)
        val toAccount = db.getAccount(split.toAccountId)
        util!!.setTransferTitleText(label, fromAccount, toAccount)
        util!!.setTransferAmountText(
            data,
            fromAccount.currency,
            split.fromAmount,
            toAccount.currency,
            split.toAmount
        )
    }

    private fun deleteSplit(v: View) {
        val split = viewToSplitMap.remove(v)
        if (split != null) {
            removeSplitView(v)
            updateUnsplitAmount()
        }
    }

    private fun removeSplitView(v: View) {
        splitsLayout!!.removeView(v)
        val dividerView = v.tag as View
        if (dividerView != null) {
            splitsLayout!!.removeView(dividerView)
        }
    }

    private val currency: Currency
        private get() {
            if (selectedOriginCurrencyId > 0) {
                return CurrencyCache.getCurrency(db, selectedOriginCurrencyId)
            }
            return if (selectedAccount != null) {
                selectedAccount!!.currency
            } else Currency.EMPTY
        }

    private class ActivityState : Serializable {
        var categoryId: Long = 0
        var idSequence: Long = 0
        var splits: List<Transaction>? = null
    }

    companion object {
        const val CURRENT_BALANCE_EXTRA = "accountCurrentBalance"
        const val AMOUNT_EXTRA = "accountAmount"
        const val ACTIVITY_STATE = "ACTIVITY_STATE"
        private const val SPLIT_REQUEST = 5001
    }
}
