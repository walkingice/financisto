package ru.orangesoftware.financisto2.transaction

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseHelper.AccountColumns
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Transaction
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto2.activity.CategorySelector

class TransferActivity : AbstractTransactionActivity() {
    private var accountFromText: TextView? = null
    private var accountToText: TextView? = null
    private var selectedAccountFromId: Long = -1
    private var selectedAccountToId: Long = -1
    override fun internalOnCreate() {
        if (transaction.isTemplateLike) {
            setTitle(if (transaction.isTemplate()) R.string.transfer_template else R.string.transfer_schedule)
            if (transaction.isTemplate()) {
                dateText.isEnabled = false
                timeText.isEnabled = false
            }
        }
    }

    override fun fetchCategories() {
        categorySelector.fetchCategories(false)
        categorySelector.doNotShowSplitCategory()
    }

    override fun getLayoutId(): Int {
        return if (MyPreferences.isUseFixedLayout(this))
            R.layout.activity_transfer_fixed
        else
            R.layout.activity_transfer_free
    }

    override fun createListNodes(layout: LinearLayout) {
        accountFromText =
            x.addListNode(layout, R.id.account_from, R.string.account_from, R.string.select_account)
        accountToText =
            x.addListNode(layout, R.id.account_to, R.string.account_to, R.string.select_account)
        // amounts
        rateView.createTransferUI()
        // payee
        isShowPayee = MyPreferences.isShowPayeeInTransfers(this)
        if (isShowPayee) {
            createPayeeNode(layout)
        }
        // category
        if (MyPreferences.isShowCategoryInTransferScreen(this)) {
            categorySelector.createNode(layout, CategorySelector.SelectorType.TRANSFER)
        } else {
            categorySelector.createDummyNode()
        }
    }

    override fun editTransaction(transaction: Transaction) {
        if (transaction.fromAccountId > 0) {
            val fromAccount = db.getAccount(transaction.fromAccountId)
            selectAccount(fromAccount, accountFromText, false)
            rateView.selectCurrencyFrom(fromAccount.currency)
            rateView.fromAmount = transaction.fromAmount
            selectedAccountFromId = transaction.fromAccountId
        }
        commonEditTransaction(transaction)
        if (transaction.toAccountId > 0) {
            val toAccount = db.getAccount(transaction.toAccountId)
            selectAccount(toAccount, accountToText, false)
            rateView.selectCurrencyTo(toAccount.currency)
            rateView.toAmount = transaction.toAmount
            selectedAccountToId = transaction.toAccountId
        }
        selectPayee(transaction.payeeId)
    }

    override fun onOKClicked(): Boolean {
        if (selectedAccountFromId == -1L) {
            Toast.makeText(this, R.string.select_from_account, Toast.LENGTH_SHORT).show()
            return false
        }
        if (selectedAccountToId == -1L) {
            Toast.makeText(this, R.string.select_to_account, Toast.LENGTH_SHORT).show()
            return false
        }
        if (selectedAccountFromId == selectedAccountToId) {
            Toast.makeText(
                this,
                R.string.select_to_account_differ_from_to_account,
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        if (checkSelectedEntities()) {
            updateTransferFromUI()
            return true
        }
        return false
    }

    private fun updateTransferFromUI() {
        updateTransactionFromUI(transaction)
        transaction.fromAccountId = selectedAccountFromId
        transaction.toAccountId = selectedAccountToId
        transaction.fromAmount = rateView.fromAmount
        transaction.toAmount = rateView.toAmount
    }

    override fun onClick(v: View, id: Int) {
        super.onClick(v, id)
        when (id) {
            R.id.account_from -> x.select(
                this, R.id.account_from, R.string.account, accountCursor, accountAdapter,
                AccountColumns.ID, selectedAccountFromId
            )
            R.id.account_to -> x.select(
                this, R.id.account_to, R.string.account, accountCursor, accountAdapter,
                AccountColumns.ID, selectedAccountToId
            )
        }
    }

    override fun onSelectedId(id: Int, selectedId: Long) {
        super.onSelectedId(id, selectedId)
        when (id) {
            R.id.account_from -> selectFromAccount(selectedId)
            R.id.account_to -> selectToAccount(selectedId)
        }
    }

    private fun selectFromAccount(selectedId: Long) {
        selectAccount(selectedId, true)
    }

    private fun selectToAccount(selectedId: Long) {
        val account = db.getAccount(selectedId)
        if (account != null) {
            selectAccount(account, accountToText, false)
            selectedAccountToId = selectedId
            rateView.selectCurrencyTo(account.currency)
        }
    }

    override fun selectAccount(
        accountId: Long,
        selectLast: Boolean
    ): Account {
        val account = db.getAccount(accountId)
        if (account != null) {
            selectAccount(account, accountFromText, selectLast)
            selectedAccountFromId = accountId
            rateView.selectCurrencyFrom(account.currency)
        }
        return account
    }

    protected fun selectAccount(
        account: Account,
        accountText: TextView?,
        selectLast: Boolean
    ) {
        accountText!!.text = account.title
        if (selectLast && isRememberLastAccount) {
            selectToAccount(account.lastAccountId)
        }
    }
}
