package ru.orangesoftware.financisto2.blotter

import android.app.Activity
import android.app.AlertDialog.Builder
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.LayoutRes
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import greendroid.widget.QuickActionGrid
import greendroid.widget.QuickActionWidget
import greendroid.widget.QuickActionWidget.OnQuickActionClickListener
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity
import ru.orangesoftware.financisto.activity.AccountWidget
import ru.orangesoftware.financisto.activity.BlotterFilterActivity
import ru.orangesoftware.financisto.activity.BlotterTotalsDetailsActivity
import ru.orangesoftware.financisto.activity.FilterState
import ru.orangesoftware.financisto.activity.IntegrityCheckTask
import ru.orangesoftware.financisto.activity.MonthlyViewActivity
import ru.orangesoftware.financisto.activity.MyQuickAction
import ru.orangesoftware.financisto.activity.TransactionActivity
import ru.orangesoftware.financisto.activity.TransferActivity
import ru.orangesoftware.financisto.adapter.TransactionsListAdapter
import ru.orangesoftware.financisto.blotter.AccountTotalCalculationTask
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.blotter.BlotterTotalCalculationTask
import ru.orangesoftware.financisto.blotter.TotalCalculationTask
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.AccountType
import ru.orangesoftware.financisto.utils.IntegrityCheckRunningBalance
import ru.orangesoftware.financisto.utils.MenuItemInfo
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.view.NodeInflater
import ru.orangesoftware.financisto2.AbstractListFragment
import ru.orangesoftware.financisto2.dialog.TransactionInfoDialog
import ru.orangesoftware.financisto2.template.SelectTemplateActivity
import ru.orangesoftware.financisto2.template.SelectTemplateFragment

private const val NEW_TRANSACTION_REQUEST = 1
private const val NEW_TRANSFER_REQUEST = 3
private const val NEW_TRANSACTION_FROM_TEMPLATE_REQUEST = 5
private const val MONTHLY_VIEW_REQUEST = 6
private const val BILL_PREVIEW_REQUEST = 7
private const val FILTER_REQUEST = 6

open class BlotterFragment : AbstractListFragment() {

    private lateinit var nodeInflater: NodeInflater
    private var selectedId: Long = -1

    protected var totalText: TextView? = null
    protected var bFilter: ImageButton? = null
    protected var bTransfer: ImageButton? = null
    protected var bTemplate: ImageButton? = null
    protected var bSearch: ImageButton? = null
    protected var bMenu: ImageButton? = null

    protected var transactionActionGrid: QuickActionGrid? = null
    protected var addButtonActionGrid: QuickActionGrid? = null

    private var calculationTask: TotalCalculationTask? = null

    protected var saveFilter = false
    protected var blotterFilter = WhereFilter.empty()

    protected var isAccountBlotter = false
    protected var showAllBlotterButtons = true
    private lateinit var inflatedView: View

    private val callback = object : BlotterOperations.DeletionCallback {
        override fun onDeleteTransactionCompleted(transactionId: Long) {
            afterDeletingTransaction(transactionId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        nodeInflater = NodeInflater(inflater)
        inflatedView = inflater.inflate(getLayoutResourceId(), container, false)
        return inflatedView
    }

    @LayoutRes
    protected open fun getLayoutResourceId(): Int = R.layout.fragment_blotter

    override fun internalOnCreate(view: View, savedInstanceState: Bundle?) {
        super.internalOnCreate(inflatedView, savedInstanceState)
        bFilter = inflatedView.findViewById(R.id.bFilter)
        bSearch = inflatedView.findViewById(R.id.bSearch)
        bTransfer = inflatedView.findViewById(R.id.bTransfer)
        bTemplate = inflatedView.findViewById(R.id.bTemplate)
        totalText = inflatedView.findViewById(R.id.total)

        activity?.intent?.let { intent ->
            blotterFilter = WhereFilter.fromIntent(intent)
            saveFilter = intent.getBooleanExtra(SAVE_FILTER, false)
            isAccountBlotter = intent.getBooleanExtra(
                BlotterFilterActivity.IS_ACCOUNT_FILTER, false
            )
        }

        if (savedInstanceState != null) {
            blotterFilter = WhereFilter.fromBundle(savedInstanceState)
        }
        if (saveFilter && blotterFilter.isEmpty) {
            blotterFilter = WhereFilter.fromSharedPreferences(requireActivity().getPreferences(0))
        }
        showAllBlotterButtons =
            !isAccountBlotter && !MyPreferences.isCollapseBlotterButtons(requireContext())

        initBottomAppBar(view)
        initFilterButton()
        initSearchButton()
        if (showAllBlotterButtons) {
            initTransferButton()
            initTemplateButton()
        }
        initTotalText()

        applyFilter()
        applyPopupMenu()
        calculateTotals()
        prepareTransactionActionGrid()
        prepareAddButtonActionGrid()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        blotterFilter.toBundle(outState)
    }

    override fun recreateCursor() {
        super.recreateCursor()
        calculateTotals()
    }

    override fun createContextMenus(id: Long): MutableList<MenuItemInfo> {
        return if (blotterFilter.isTemplate() || blotterFilter.isSchedule) {
            super.createContextMenus(id)
        } else {
            val menus = super.createContextMenus(id)
            menus.add(MenuItemInfo(MENU_DUPLICATE, R.string.duplicate))
            menus.add(MenuItemInfo(MENU_SAVE_AS_TEMPLATE, R.string.save_as_template))
            menus
        }
    }

    override fun onPopupItemSelected(itemId: Int, view: View, position: Int, id: Long): Boolean {
        if (!super.onPopupItemSelected(itemId, view, position, id)) {
            when (itemId) {
                MENU_DUPLICATE -> duplicateTransaction(id, 1)
                MENU_SAVE_AS_TEMPLATE -> {
                    BlotterOperations(this, db, id, callback).duplicateAsTemplate()
                    Toast.makeText(
                        requireContext(),
                        R.string.save_as_template_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            return true
        }
        return false
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        if (requestCode == FILTER_REQUEST) {
            if (resultCode == Activity.RESULT_FIRST_USER) {
                blotterFilter.clear()
            } else if (resultCode == Activity.RESULT_OK) {
                blotterFilter = WhereFilter.fromIntent(data)
            }
            if (saveFilter) {
                saveFilter()
            }
            applyFilter()
            recreateCursor()
        } else if (resultCode == Activity.RESULT_OK && requestCode == NEW_TRANSACTION_FROM_TEMPLATE_REQUEST && data != null) {
            createTransactionFromTemplate(data)
        }
        if (resultCode == Activity.RESULT_OK || resultCode == Activity.RESULT_FIRST_USER) {
            recreateCursor()
        }
    }

    override fun createCursor(): Cursor {
        return if (isAccountBlotter) {
            db.getBlotterForAccount(blotterFilter)
        } else {
            db.getBlotter(blotterFilter)
        }
    }

    override fun createAdapter(cursor: Cursor): ListAdapter {
        return if (isAccountBlotter) {
            TransactionsListAdapter(requireContext(), db, cursor)
        } else {
            BlotterListAdapter(requireContext(), db, cursor)
        }
    }

    override fun deleteItem(view: View, position: Int, id: Long) {
        deleteTransaction(id)
    }

    override fun editItem(view: View, position: Int, id: Long) {
        editTransaction(id)
    }

    override fun onItemLongClick(view: View, position: Int, id: Long) {
        if (MyPreferences.isQuickMenuEnabledForTransaction(requireContext())) {
            selectedId = id
            transactionActionGrid?.show(view)
        } else {
            showTransactionInfo(id)
        }
    }

    override fun viewItem(view: View, position: Int, id: Long) {
        showTransactionInfo(id)
    }

    override fun integrityCheck() {
        val activity = requireActivity()
        val check = IntegrityCheckRunningBalance(activity, db)
        IntegrityCheckTask(activity).execute(check)
    }

    protected open fun calculateTotals() {
        calculationTask?.stop()
        calculationTask?.cancel(true)
        calculationTask = createTotalCalculationTask()
        calculationTask!!.execute()
    }

    protected fun createTotalCalculationTask(): TotalCalculationTask? {
        val filter = WhereFilter.copyOf(blotterFilter)
        return if (filter.accountId > 0) {
            AccountTotalCalculationTask(requireContext(), db, filter, totalText)
        } else {
            BlotterTotalCalculationTask(requireContext(), db, filter, totalText)
        }
    }

    private fun initTotalText() {
        totalText?.setOnClickListener { view: View? -> showTotals() }
    }

    private fun initBottomAppBar(inflated: View) {
        val fab = inflated.findViewById<FloatingActionButton>(R.id.fragment_bottom_fab) ?: return
        fab.setOnClickListener { onTemplateButtonClicked() }
        val bar = inflated.findViewById<BottomAppBar>(R.id.fragment_bottom_bar) ?: return

        fun menuClickListener(menu: MenuItem): Boolean {
            when (menu.itemId) {
                R.id.menu_item_add -> onButtonAddClicked()
                R.id.menu_item_transfer -> onTransferButtonClicked()
                R.id.menu_item_template -> onTemplateButtonClicked()
                R.id.menu_item_search -> onSearchButtonClicked()
                R.id.menu_item_filter -> onFilterButtonClicked()
                R.id.menu_item_menu -> onMenuButtonClicked()
                else -> return false
            }
            return true
        }
        bar.setOnMenuItemClickListener(::menuClickListener)
    }

    override fun onButtonAddClicked() {
        if (showAllBlotterButtons) {
            addItem(NEW_TRANSACTION_REQUEST, TransactionActivity::class.java)
        } else {
            addButtonActionGrid!!.show(bAdd)
        }
    }

    private fun initFilterButton() {
        bFilter?.setOnClickListener { onFilterButtonClicked() }
    }

    private fun initTransferButton() {
        bTransfer?.visibility = View.VISIBLE
        bTransfer?.setOnClickListener { onTransferButtonClicked() }
    }

    private fun initTemplateButton() {
        bTemplate?.visibility = View.VISIBLE
        bTemplate?.setOnClickListener { onTemplateButtonClicked() }
    }

    private fun initSearchButton() {
        bSearch?.setOnClickListener { onSearchButtonClicked() }
    }

    private fun applyPopupMenu() {
        bMenu = inflatedView.findViewById(R.id.bMenu)
        if (isAccountBlotter) {
            bMenu?.setOnClickListener { onMenuButtonClicked() }
        } else {
            bMenu?.visibility = View.GONE
        }
    }

    fun onPopupMenuSelected(id: Int) {
        val accountId = blotterFilter.accountId
        val intent = Intent(requireActivity(), MonthlyViewActivity::class.java)
        intent.putExtra(MonthlyViewActivity.ACCOUNT_EXTRA, accountId)
        when (id) {
            R.id.opt_menu_month -> {
                // call credit card bill activity sending account id
                intent.putExtra(MonthlyViewActivity.BILL_PREVIEW_EXTRA, false)
                startActivityForResult(intent, MONTHLY_VIEW_REQUEST)
            }
            R.id.opt_menu_bill -> if (accountId != -1L) {
                val account = db.getAccount(accountId)

                // call credit card bill activity sending account id
                if (account.paymentDay > 0 && account.closingDay > 0) {
                    intent.putExtra(MonthlyViewActivity.BILL_PREVIEW_EXTRA, true)
                    startActivityForResult(intent, BILL_PREVIEW_REQUEST)
                } else {
                    // display message: need payment and closing day
                    val dlgAlert = Builder(requireContext())
                    dlgAlert.setMessage(R.string.statement_error)
                    dlgAlert.setTitle(R.string.ccard_statement)
                    dlgAlert.setPositiveButton(R.string.ok, null)
                    dlgAlert.setCancelable(true)
                    dlgAlert.create().show()
                }
            }
        }
    }

    private fun showTotals() {
        val intent = Intent(requireActivity(), BlotterTotalsDetailsActivity::class.java)
        blotterFilter.toIntent(intent)
        startActivityForResult(intent, -1)
    }

    protected fun prepareTransactionActionGrid() {
        val grid = QuickActionGrid(requireActivity())
        val context = requireContext()
        transactionActionGrid = grid
        with(grid) {
            addQuickAction(MyQuickAction(context, R.drawable.ic_action_info, R.string.info))
            addQuickAction(MyQuickAction(context, R.drawable.ic_action_edit, R.string.edit))
            addQuickAction(MyQuickAction(context, R.drawable.ic_action_trash, R.string.delete))
            addQuickAction(MyQuickAction(context, R.drawable.ic_action_copy, R.string.duplicate))
            addQuickAction(MyQuickAction(context, R.drawable.ic_action_tick, R.string.clear))
            addQuickAction(
                MyQuickAction(context, R.drawable.ic_action_double_tick, R.string.reconcile)
            )
        }

        grid.setOnQuickActionClickListener(transactionActionListener)
    }

    private val transactionActionListener =
        OnQuickActionClickListener { widget, position ->
            when (position) {
                0 -> showTransactionInfo(selectedId)
                1 -> editTransaction(selectedId)
                2 -> deleteTransaction(selectedId)
                3 -> duplicateTransaction(selectedId, 1)
                4 -> clearTransaction(selectedId)
                5 -> reconcileTransaction(selectedId)
            }
        }

    private fun prepareAddButtonActionGrid() {
        val context = requireContext()
        val grid = QuickActionGrid(context)
        addButtonActionGrid = grid
        grid.addQuickAction(
            MyQuickAction(context, R.drawable.actionbar_add_big, R.string.transaction)
        )
        grid.addQuickAction(
            MyQuickAction(context, R.drawable.ic_action_transfer, R.string.transfer)
        )
        if (addTemplateToAddButton()) {
            grid.addQuickAction(
                MyQuickAction(context, R.drawable.actionbar_tiles_large, R.string.template)
            )
        } else {
            grid.setNumColumns(2)
        }
        grid.setOnQuickActionClickListener(addButtonActionListener)
    }

    protected open fun addTemplateToAddButton(): Boolean {
        return true
    }

    private val addButtonActionListener =
        OnQuickActionClickListener { widget: QuickActionWidget?, position: Int ->
            when (position) {
                0 -> addItem(NEW_TRANSACTION_REQUEST, TransactionActivity::class.java)
                1 -> addItem(NEW_TRANSFER_REQUEST, TransferActivity::class.java)
                2 -> onTemplateButtonClicked()
            }
        }

    private fun clearTransaction(selectedId: Long) {
        BlotterOperations(this, db, selectedId, callback).clearTransaction()
        recreateCursor()
    }

    private fun reconcileTransaction(selectedId: Long) {
        BlotterOperations(this, db, selectedId, callback).reconcileTransaction()
        recreateCursor()
    }

    private fun duplicateTransaction(id: Long, multiplier: Int): Long {
        val newId = BlotterOperations(this, db, id, callback)
            .duplicateTransaction(multiplier)
        val toastText: String = if (multiplier > 1) {
            getString(R.string.duplicate_success_with_multiplier, multiplier)
        } else {
            getString(R.string.duplicate_success)
        }
        Toast.makeText(requireContext(), toastText, Toast.LENGTH_LONG).show()
        recreateCursor()
        AccountWidget.updateWidgets(requireContext())
        return newId
    }

    protected fun addItem(
        requestId: Int,
        clazz: Class<out AbstractTransactionActivity?>?
    ) {
        val intent = Intent(requireContext(), clazz)
        val accountId = blotterFilter.accountId
        if (accountId != -1L) {
            intent.putExtra(TransactionActivity.ACCOUNT_ID_EXTRA, accountId)
        }
        intent.putExtra(TransactionActivity.TEMPLATE_EXTRA, blotterFilter.getIsTemplate())
        startActivityForResult(intent, requestId)
    }

    private fun deleteTransaction(id: Long) {
        BlotterOperations(this, db, id, callback).deleteTransaction()
    }

    fun afterDeletingTransaction(id: Long) {
        recreateCursor()
        AccountWidget.updateWidgets(requireContext())
    }

    private fun editTransaction(id: Long) {
        BlotterOperations(this, db, id, callback).editTransaction()
    }

    protected fun onTemplateButtonClicked() {
        val intent = Intent(requireActivity(), SelectTemplateActivity::class.java)
        startActivityForResult(intent, NEW_TRANSACTION_FROM_TEMPLATE_REQUEST)
    }

    private fun onTransferButtonClicked() {
        addItem(NEW_TRANSFER_REQUEST, TransferActivity::class.java)
    }

    private fun onFilterButtonClicked() {
        val intent = Intent(requireActivity(), BlotterFilterActivity::class.java)
        blotterFilter.toIntent(intent)
        intent.putExtra(
            BlotterFilterActivity.IS_ACCOUNT_FILTER,
            isAccountBlotter && blotterFilter.accountId > 0
        )
        startActivityForResult(intent, FILTER_REQUEST)
    }

    private fun onSearchButtonClicked() {
        val searchText: EditText = inflatedView.findViewById<EditText>(R.id.search_text)
        val searchLayout: FrameLayout = inflatedView.findViewById(R.id.search_text_frame)
        val searchTextClearBtn: ImageButton = inflatedView.findViewById(R.id.search_text_clear)
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        searchText.onFocusChangeListener = OnFocusChangeListener { view: View, b: Boolean ->
            if (!view.hasFocus()) {
                imm.hideSoftInputFromWindow(searchLayout.windowToken, 0)
            }
        }
        searchTextClearBtn.setOnClickListener { searchText.setText("") }
        if (searchLayout.visibility == View.VISIBLE) {
            imm.hideSoftInputFromWindow(searchLayout.windowToken, 0)
            searchLayout.visibility = View.GONE
            return
        }
        searchLayout.visibility = View.VISIBLE
        searchText.requestFocusFromTouch()
        imm.showSoftInput(searchText, InputMethodManager.SHOW_IMPLICIT)
        searchText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(c: CharSequence, i1: Int, i2: Int, i3: Int) {}

            override fun onTextChanged(c: CharSequence, i1: Int, i2: Int, i3: Int) {}

            override fun afterTextChanged(editable: Editable) {
                val clearButton: ImageButton = inflatedView.findViewById(R.id.search_text_clear)
                val text = editable.toString()
                blotterFilter.remove(BlotterFilter.NOTE)
                if (text.isNotEmpty()) {
                    blotterFilter.contains(BlotterFilter.NOTE, text)
                    clearButton.visibility = View.VISIBLE
                } else {
                    clearButton.visibility = View.GONE
                }
                recreateCursor()
                applyFilter()
                saveFilter()
            }
        })
        if (blotterFilter[BlotterFilter.NOTE] != null) {
            var searchFilterText =
                blotterFilter[BlotterFilter.NOTE].stringValue
            if (searchFilterText.isNotEmpty()) {
                searchFilterText = searchFilterText.substring(1, searchFilterText.length - 1)
                searchText.setText(searchFilterText)
            }
        }
    }

    private fun onMenuButtonClicked() {
        val popupMenu = PopupMenu(requireContext(), bMenu)
        val accountId = blotterFilter.accountId
        if (accountId != -1L) {
            // get account type
            val account = db.getAccount(accountId)
            val type = AccountType.valueOf(account.type)
            val inflater: MenuInflater = requireActivity().menuInflater
            if (type.isCreditCard) {
                // Show menu for Credit Cards - bill
                inflater.inflate(R.menu.ccard_blotter_menu, popupMenu.menu)
            } else {
                // Show menu for other accounts - monthly view
                inflater.inflate(R.menu.blotter_menu, popupMenu.menu)
            }
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                onPopupMenuSelected(item.itemId)
                true
            }
            popupMenu.show()
        }
    }

    private fun createTransactionFromTemplate(data: Intent) {
        val templateId = data.getLongExtra(SelectTemplateFragment.TEMPATE_ID, -1)
        val multiplier = data.getIntExtra(SelectTemplateFragment.MULTIPLIER, 1)
        val edit =
            data.getBooleanExtra(SelectTemplateFragment.EDIT_AFTER_CREATION, false)
        if (templateId > 0) {
            val id = duplicateTransaction(templateId, multiplier)
            val t = db.getTransaction(id)
            if (t.fromAmount == 0L || edit) {

                BlotterOperations(this, db, id, callback).asNewFromTemplate()
                    .editTransaction()
            }
        }
    }

    private fun saveFilter() {
        val preferences: SharedPreferences = requireActivity().getPreferences(0)
        blotterFilter.toSharedPreferences(preferences)
    }

    protected fun applyFilter() {
        val accountId = blotterFilter.accountId
        if (accountId != -1L) {
            val a = db.getAccount(accountId)
            bAdd?.visibility = if (a != null && a.isActive) View.VISIBLE else View.GONE
            if (showAllBlotterButtons) {
                bTransfer?.visibility = if (a != null && a.isActive) View.VISIBLE else View.GONE
            }
        }
        val title = blotterFilter.title
        if (title != null) {
            requireActivity().title = getString(R.string.blotter) + " : " + title
        }
        updateFilterImage()
    }

    protected fun updateFilterImage() {
        if (bFilter != null) {
            FilterState.updateFilterColor(requireContext(), blotterFilter, bFilter)
        }
    }

    private fun showTransactionInfo(id: Long) {
        val transactionInfoView = TransactionInfoDialog(requireContext(), db, nodeInflater)
        transactionInfoView.show(this, id, callback)
    }

//    fun onBackPressed() {
//        val searchLayout: FrameLayout =
//            inflatedView.findViewById<FrameLayout>(R.id.search_text_frame)
//        if (searchLayout != null && searchLayout.visibility == View.VISIBLE) {
//            searchLayout.visibility = View.GONE
//        } else {
//            super.onBackPressed()
//        }
//    }

    companion object {
        private const val MENU_DUPLICATE = LAST_MENU_INDEX + 1
        private const val MENU_SAVE_AS_TEMPLATE = LAST_MENU_INDEX + 2
        const val SAVE_FILTER = "saveFilter"
        const val EXTRA_FILTER_ACCOUNTS = "filterAccounts"

        fun newInstance(saveFilter: Boolean = false): BlotterFragment {
            val bundle = Bundle().apply { putBoolean(SAVE_FILTER, saveFilter) }
            return BlotterFragment().apply { arguments = bundle }
        }
    }
}
