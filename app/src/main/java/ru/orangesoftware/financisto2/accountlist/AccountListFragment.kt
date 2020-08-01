package ru.orangesoftware.financisto2.accountlist

import android.app.AlertDialog.Builder
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.PopupMenu
import android.widget.TextView
import greendroid.widget.QuickActionGrid
import greendroid.widget.QuickActionWidget
import ru.orangesoftware.financisto2.AbstractListFragment
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity
import ru.orangesoftware.financisto.activity.AccountActivity
import ru.orangesoftware.financisto.activity.AccountListTotalsDetailsActivity
import ru.orangesoftware.financisto.activity.BlotterActivity
import ru.orangesoftware.financisto.activity.BlotterFilterActivity
import ru.orangesoftware.financisto.activity.IntegrityCheckTask
import ru.orangesoftware.financisto.activity.MenuListActivity_
import ru.orangesoftware.financisto.activity.MenuListItem.MENU_BACKUP
import ru.orangesoftware.financisto.activity.MyQuickAction
import ru.orangesoftware.financisto.activity.PurgeAccountActivity
import ru.orangesoftware.financisto.activity.TransactionActivity
import ru.orangesoftware.financisto.activity.TransferActivity
import ru.orangesoftware.financisto.adapter.AccountListAdapter2
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.blotter.TotalCalculationTask
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

class AccountListFragment : AbstractListFragment() {

    private lateinit var inflatedView: View
    private var accountActionGrid: QuickActionWidget? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        inflatedView = inflater.inflate(R.layout.fragment_account_list, container, false)
        return inflatedView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        setupMenuButton()
        calculateTotals()
        integrityCheck()
    }

    private fun setupUi() {
        inflatedView.findViewById<View>(R.id.integrity_error).setOnClickListener { v: View ->
            v.visibility = View.GONE
        }
        listView.onItemLongClickListener =
            OnItemLongClickListener { _: AdapterView<*>?, view: View?, _: Int, id: Long ->
                selectedId = id
                prepareAccountActionGrid()
                accountActionGrid!!.show(view)
                true
            }
    }

    private fun setupMenuButton() {
        val bMenu = inflatedView.findViewById<ImageButton>(R.id.bMenu)
        if (MyPreferences.isShowMenuButtonOnAccountsScreen(requireContext())) {
            bMenu.setOnClickListener { v: View? ->
                val popupMenu = PopupMenu(requireContext(), bMenu)
                val inflater = requireActivity().menuInflater
                inflater.inflate(R.menu.account_list_menu, popupMenu.menu)
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
            R.id.backup -> MENU_BACKUP.call(requireActivity())
            R.id.go_to_menu -> {
                val intent = Intent(requireContext(), MenuListActivity_::class.java)
                startActivity(intent)
            }
        }
    }

    protected fun prepareAccountActionGrid() {
        val a = db.getAccount(selectedId)
        accountActionGrid = QuickActionGrid(requireContext())
        accountActionGrid?.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_info, R.string.info)
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_list, R.string.blotter)
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_edit, R.string.edit)
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_add, R.string.transaction)
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_transfer, R.string.transfer)
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_tick, R.string.balance)
        )
        accountActionGrid?.addQuickAction(
            MyQuickAction(
                requireContext(),
                R.drawable.ic_action_flash,
                R.string.delete_old_transactions
            )
        )
        if (a.isActive) {
            accountActionGrid?.addQuickAction(
                MyQuickAction(
                    requireContext(),
                    R.drawable.ic_action_lock_closed,
                    R.string.close_account
                )
            )
        } else {
            accountActionGrid?.addQuickAction(
                MyQuickAction(
                    requireContext(),
                    R.drawable.ic_action_lock_open,
                    R.string.reopen_account
                )
            )
        }
        accountActionGrid?.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_trash, R.string.delete_account)

        )
        accountActionGrid?.setOnQuickActionClickListener(accountActionListener)
    }

    private val accountActionListener =
        QuickActionWidget.OnQuickActionClickListener { _, position ->
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
        val intent = Intent(requireActivity(), clazz)
        intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId)
        startActivityForResult(
            intent,
            VIEW_ACCOUNT_REQUEST
        )
    }

    override fun onCursorRecreated() {
        calculateTotals()
    }

    private var totalCalculationTask: AccountTotalsCalculationTask? = null

    private fun calculateTotals() {
        if (totalCalculationTask != null) {
            totalCalculationTask!!.stop()
            totalCalculationTask!!.cancel(true)
        }
        val totalText = inflatedView.findViewById<TextView>(R.id.total)
        totalText.setOnClickListener { view: View? -> showTotals() }
        totalCalculationTask =
            AccountTotalsCalculationTask(
                requireContext(),
                db,
                totalText
            )
        totalCalculationTask!!.execute()
    }

    private fun showTotals() {
        val intent = Intent(requireActivity(), AccountListTotalsDetailsActivity::class.java)
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

    override fun createAdapter(cursor: Cursor?): ListAdapter {
        return AccountListAdapter2(requireContext(), cursor)
    }

    override fun createCursor(): Cursor {
        return if (MyPreferences.isHideClosedAccounts(requireContext())) {
            db.allActiveAccounts
        } else {
            db.allAccounts
        }
    }

    override fun createContextMenus(id: Long): MutableList<MenuItemInfo> {
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
            val intent = Intent(requireActivity(), TransactionActivity::class.java)
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, a.id)
            intent.putExtra(TransactionActivity.CURRENT_BALANCE_EXTRA, a.totalAmount)
            startActivityForResult(intent, 0)
            return true
        }
        return false
    }

    override fun addItem() {
        val intent = Intent(requireContext(), AccountActivity::class.java)
        startActivityForResult(
            intent,
            NEW_ACCOUNT_REQUEST
        )
    }

    override fun deleteItem(view: View, position: Int, id: Long) {
        Builder(requireContext())
            .setMessage(R.string.delete_account_confirm)
            .setPositiveButton(R.string.yes) { arg0: DialogInterface?, arg1: Int ->
                db.deleteAccount(id)
                recreateCursor()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun editItem(view: View, position: Int, id: Long) {
        editAccount(id)
    }

    private fun editAccount(id: Long) {
        val intent = Intent(requireActivity(), AccountActivity::class.java)
        intent.putExtra(AccountActivity.ACCOUNT_ID_EXTRA, id)
        startActivityForResult(
            intent,
            EDIT_ACCOUNT_REQUEST
        )
    }

    private var selectedId: Long = -1
    private fun showAccountInfo(id: Long) {
        val layoutInflater =
            requireActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflater = NodeInflater(layoutInflater)
        val accountInfoDialog = AccountInfoDialog(requireActivity(), id, db, inflater)
        accountInfoDialog.show()
    }

    override fun onItemClick(view: View, position: Int, id: Long) {
        showAccountTransactions(id)
    }

    override fun viewItem(view: View, position: Int, id: Long) {
        showAccountTransactions(id)
    }

    private fun showAccountTransactions(id: Long) {
        val account = db.getAccount(id)
        if (account != null) {
            val intent = Intent(requireActivity(), BlotterActivity::class.java)
            Criteria.eq(
                BlotterFilter.FROM_ACCOUNT_ID,
                id.toString()
            )
                .toIntent(account.title, intent)
            intent.putExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, true)
            startActivityForResult(
                intent,
                VIEW_ACCOUNT_REQUEST
            )
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VIEW_ACCOUNT_REQUEST || requestCode == PURGE_ACCOUNT_REQUEST) {
            recreateCursor()
        }
    }

    private fun purgeAccount() {
        val intent = Intent(requireActivity(), PurgeAccountActivity::class.java)
        intent.putExtra(PurgeAccountActivity.ACCOUNT_ID, selectedId)
        startActivityForResult(
            intent,
            PURGE_ACCOUNT_REQUEST
        )
    }

    private fun closeOrOpenAccount() {
        val a = db.getAccount(selectedId)
        if (a.isActive) {
            Builder(requireContext())
                .setMessage(R.string.close_account_confirm)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    flipAccountActive(a)
                }
                .setNegativeButton(R.string.no, null)
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
        Builder(requireContext())
            .setMessage(R.string.delete_account_confirm)
            .setPositiveButton(R.string.yes) { arg0: DialogInterface?, arg1: Int ->
                db.deleteAccount(selectedId)
                recreateCursor()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    override fun integrityCheck() {
        IntegrityCheckTask(requireActivity()).execute(
            IntegrityCheckAutobackup(
                requireContext(),
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
