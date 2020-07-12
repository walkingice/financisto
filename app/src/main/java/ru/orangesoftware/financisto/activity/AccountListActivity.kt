/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 *
 * Contributors:
 * Denis Solonenko - initial API and implementation
 */
package ru.orangesoftware.financisto.activity

import android.app.AlertDialog.Builder
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.PopupMenu
import android.widget.TextView
import greendroid.widget.QuickActionGrid
import greendroid.widget.QuickActionWidget
import greendroid.widget.QuickActionWidget.OnQuickActionClickListener
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.R.drawable
import ru.orangesoftware.financisto.R.id
import ru.orangesoftware.financisto.R.layout
import ru.orangesoftware.financisto.R.menu
import ru.orangesoftware.financisto.R.string
import ru.orangesoftware.financisto.activity.MenuListItem.MENU_BACKUP
import ru.orangesoftware.financisto.adapter.AccountListAdapter2
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.blotter.TotalCalculationTask
import ru.orangesoftware.financisto.bus.GreenRobotBus_
import ru.orangesoftware.financisto.bus.SwitchToMenuTabEvent
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.dialog.AccountInfoDialog
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.utils.IntegrityCheckAutobackup
import ru.orangesoftware.financisto.utils.MenuItemInfo
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.view.NodeInflater
import java.util.ArrayList
import java.util.concurrent.TimeUnit.DAYS

class AccountListActivity : AbstractListActivity(layout.account_list) {
    private var accountActionGrid: QuickActionWidget? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()
        setupMenuButton()
        calculateTotals()
        integrityCheck()
    }

    private fun setupUi() {
        findViewById<View>(id.integrity_error).setOnClickListener { v: View ->
            v.visibility = View.GONE
        }
        listView.onItemLongClickListener =
            OnItemLongClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                selectedId = id
                prepareAccountActionGrid()
                accountActionGrid!!.show(view)
                true
            }
    }

    private fun setupMenuButton() {
        val bMenu = findViewById<ImageButton>(id.bMenu)
        if (MyPreferences.isShowMenuButtonOnAccountsScreen(this)) {
            bMenu.setOnClickListener { v: View? ->
                val popupMenu =
                    PopupMenu(this@AccountListActivity, bMenu)
                val inflater = menuInflater
                inflater.inflate(menu.account_list_menu, popupMenu.menu)
                popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                    handlePopupMenu(item.itemId)
                    true
                }
                popupMenu.show()
            }
        } else {
            bMenu.visibility = View.GONE
        }
    }

    private fun handlePopupMenu(id: Int) {
        when (id) {
            R.id.backup -> MENU_BACKUP.call(this)
            R.id.go_to_menu -> GreenRobotBus_.getInstance_(this).post(SwitchToMenuTabEvent())
        }
    }

    protected fun prepareAccountActionGrid() {
        val a = db.getAccount(selectedId)
        accountActionGrid = QuickActionGrid(this)
        accountActionGrid?.addQuickAction(
            MyQuickAction(
                this,
                drawable.ic_action_info,
                string.info
            )
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(
                this,
                drawable.ic_action_list,
                string.blotter
            )
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(
                this,
                drawable.ic_action_edit,
                string.edit
            )
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(
                this,
                drawable.ic_action_add,
                string.transaction
            )
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(
                this,
                drawable.ic_action_transfer,
                string.transfer
            )
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(
                this,
                drawable.ic_action_tick,
                string.balance
            )
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(
                this,
                drawable.ic_action_flash,
                string.delete_old_transactions
            )
        )
        if (a.isActive) {
            accountActionGrid?.addQuickAction(
                MyQuickAction(
                    this,
                    drawable.ic_action_lock_closed,
                    string.close_account
                )
            )
        } else {
            accountActionGrid?.addQuickAction(
                MyQuickAction(
                    this,
                    drawable.ic_action_lock_open,
                    string.reopen_account
                )
            )
        }
        accountActionGrid?.addQuickAction(
            MyQuickAction(
                this,
                drawable.ic_action_trash,
                string.delete_account
            )
        )
        accountActionGrid?.setOnQuickActionClickListener(accountActionListener)
    }

    private val accountActionListener =
        OnQuickActionClickListener { widget, position ->
            when (position) {
                0 -> showAccountInfo(selectedId)
                1 -> showAccountTransactions(selectedId)
                2 -> editAccount(selectedId)
                3 -> addTransaction(selectedId, TransactionActivity::class.java)
                4 -> addTransaction(selectedId, TransferActivity::class.java)
                5 -> updateAccountBalance(selectedId)
                6 -> purgeAccount()
                7 -> closeOrOpenAccount()
                8 -> deleteAccount()
            }
        }

    private fun addTransaction(
        accountId: Long,
        clazz: Class<out AbstractTransactionActivity?>
    ) {
        val intent = Intent(this, clazz)
        intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId)
        startActivityForResult(intent, VIEW_ACCOUNT_REQUEST)
    }

    override fun recreateCursor() {
        super.recreateCursor()
        calculateTotals()
    }

    private var totalCalculationTask: AccountTotalsCalculationTask? = null
    private fun calculateTotals() {
        if (totalCalculationTask != null) {
            totalCalculationTask!!.stop()
            totalCalculationTask!!.cancel(true)
        }
        val totalText = findViewById<TextView>(id.total)
        totalText.setOnClickListener { view: View? -> showTotals() }
        totalCalculationTask = AccountTotalsCalculationTask(this, db, totalText)
        totalCalculationTask!!.execute()
    }

    private fun showTotals() {
        val intent = Intent(this, AccountListTotalsDetailsActivity::class.java)
        startActivityForResult(intent, -1)
    }

    class AccountTotalsCalculationTask internal constructor(
        context: Context?,
        private val db: DatabaseAdapter,
        totalText: TextView?
    ) : TotalCalculationTask(context, totalText) {
        override fun getTotalInHomeCurrency(): Total {
            return db.accountsTotalInHomeCurrency
        }

        override fun getTotals(): Array<Total?> {
            return arrayOfNulls(0)
        }

    }

    override fun createAdapter(cursor: Cursor): ListAdapter {
        return AccountListAdapter2(this, cursor)
    }

    override fun createCursor(): Cursor {
        return if (MyPreferences.isHideClosedAccounts(this)) {
            db.allActiveAccounts
        } else {
            db.allAccounts
        }
    }

    override fun createContextMenus(id: Long): List<MenuItemInfo> {
        return ArrayList()
    }

    override fun onPopupItemSelected(
        itemId: Int,
        view: View,
        position: Int,
        id: Long
    ): Boolean {
        // do nothing
        return true
    }

    private fun updateAccountBalance(id: Long): Boolean {
        val a = db.getAccount(id)
        if (a != null) {
            val intent = Intent(this, TransactionActivity::class.java)
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, a.id)
            intent.putExtra(TransactionActivity.CURRENT_BALANCE_EXTRA, a.totalAmount)
            startActivityForResult(intent, 0)
            return true
        }
        return false
    }

    override fun addItem() {
        val intent = Intent(this@AccountListActivity, AccountActivity::class.java)
        startActivityForResult(intent, NEW_ACCOUNT_REQUEST)
    }

    override fun deleteItem(
        v: View,
        position: Int,
        id: Long
    ) {
        Builder(this)
            .setMessage(string.delete_account_confirm)
            .setPositiveButton(
                string.yes
            ) { arg0: DialogInterface?, arg1: Int ->
                db.deleteAccount(id)
                recreateCursor()
            }
            .setNegativeButton(string.no, null)
            .show()
    }

    public override fun editItem(
        v: View,
        position: Int,
        id: Long
    ) {
        editAccount(id)
    }

    private fun editAccount(id: Long) {
        val intent = Intent(this@AccountListActivity, AccountActivity::class.java)
        intent.putExtra(AccountActivity.ACCOUNT_ID_EXTRA, id)
        startActivityForResult(intent, EDIT_ACCOUNT_REQUEST)
    }

    private var selectedId: Long = -1
    private fun showAccountInfo(id: Long) {
        val layoutInflater =
            getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflater = NodeInflater(layoutInflater)
        val accountInfoDialog = AccountInfoDialog(this, id, db, inflater)
        accountInfoDialog.show()
    }

    override fun onItemClick(
        v: View,
        position: Int,
        id: Long
    ) {
        if (MyPreferences.isQuickMenuEnabledForAccount(this)) {
            selectedId = id
            prepareAccountActionGrid()
            accountActionGrid!!.show(v)
        } else {
            showAccountTransactions(id)
        }
    }

    override fun viewItem(
        v: View,
        position: Int,
        id: Long
    ) {
        showAccountTransactions(id)
    }

    private fun showAccountTransactions(id: Long) {
        val account = db.getAccount(id)
        if (account != null) {
            val intent = Intent(this@AccountListActivity, BlotterActivity::class.java)
            Criteria.eq(
                BlotterFilter.FROM_ACCOUNT_ID,
                id.toString()
            )
                .toIntent(account.title, intent)
            intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, true)
            startActivityForResult(intent, VIEW_ACCOUNT_REQUEST)
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VIEW_ACCOUNT_REQUEST || requestCode == PURGE_ACCOUNT_REQUEST) {
            recreateCursor()
        }
    }

    private fun purgeAccount() {
        val intent = Intent(this, PurgeAccountActivity::class.java)
        intent.putExtra(PurgeAccountActivity.ACCOUNT_ID, selectedId)
        startActivityForResult(intent, PURGE_ACCOUNT_REQUEST)
    }

    private fun closeOrOpenAccount() {
        val a = db.getAccount(selectedId)
        if (a.isActive) {
            Builder(this)
                .setMessage(string.close_account_confirm)
                .setPositiveButton(
                    string.yes
                ) { arg0: DialogInterface?, arg1: Int ->
                    flipAccountActive(a)
                }
                .setNegativeButton(string.no, null)
                .show()
        } else {
            flipAccountActive(a)
        }
    }

    private fun flipAccountActive(a: Account) {
        a.isActive = !a.isActive
        db.saveAccount(a)
        recreateCursor()
    }

    private fun deleteAccount() {
        Builder(this)
            .setMessage(string.delete_account_confirm)
            .setPositiveButton(
                string.yes
            ) { arg0: DialogInterface?, arg1: Int ->
                db.deleteAccount(selectedId)
                recreateCursor()
            }
            .setNegativeButton(string.no, null)
            .show()
    }

    override fun integrityCheck() {
        IntegrityCheckTask(this).execute(
            IntegrityCheckAutobackup(
                this,
                DAYS.toMillis(7)
            )
        )
    }

    companion object {
        private const val NEW_ACCOUNT_REQUEST = 1
        const val EDIT_ACCOUNT_REQUEST = 2
        private const val VIEW_ACCOUNT_REQUEST = 3
        private const val PURGE_ACCOUNT_REQUEST = 4
    }
}
