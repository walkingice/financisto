package ru.orangesoftware.financisto.blotter

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
import greendroid.widget.QuickActionGrid
import greendroid.widget.QuickActionWidget
import greendroid.widget.QuickActionWidget.OnQuickActionClickListener
import ru.orangesoftware.financisto.AbstractListFragment
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.AbstractTransactionActivity
import ru.orangesoftware.financisto.activity.AccountWidget
import ru.orangesoftware.financisto.activity.BlotterFilterActivity
import ru.orangesoftware.financisto.activity.BlotterOperations
import ru.orangesoftware.financisto.activity.BlotterTotalsDetailsActivity
import ru.orangesoftware.financisto.activity.FilterState
import ru.orangesoftware.financisto.activity.IntegrityCheckTask
import ru.orangesoftware.financisto.activity.MonthlyViewActivity
import ru.orangesoftware.financisto.activity.MyQuickAction
import ru.orangesoftware.financisto.activity.SelectTemplateActivity
import ru.orangesoftware.financisto.activity.TransactionActivity
import ru.orangesoftware.financisto.activity.TransferActivity
import ru.orangesoftware.financisto.adapter.BlotterListAdapter
import ru.orangesoftware.financisto.adapter.TransactionsListAdapter
import ru.orangesoftware.financisto.dialog.TransactionInfoDialog
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.AccountType
import ru.orangesoftware.financisto.utils.IntegrityCheckRunningBalance
import ru.orangesoftware.financisto.utils.MenuItemInfo
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.view.NodeInflater

open class BlotterFragment : AbstractListFragment() {

    private val NEW_TRANSACTION_REQUEST = 1
    private val NEW_TRANSFER_REQUEST = 3
    private val NEW_TRANSACTION_FROM_TEMPLATE_REQUEST = 5
    private val MONTHLY_VIEW_REQUEST = 6
    private val BILL_PREVIEW_REQUEST = 7

    protected val FILTER_REQUEST = 6
    private val MENU_DUPLICATE = MENU_ADD + 1
    private val MENU_SAVE_AS_TEMPLATE = MENU_ADD + 2

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
        override fun onDeleteTransaction(transactionId: Long) {
            deleteTransaction(transactionId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        inflatedView = inflater.inflate(R.layout.fragment_blotter, container, false)
        return inflatedView
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

    override fun internalOnCreate(view: View, savedInstanceState: Bundle?) {
        super.internalOnCreate(inflatedView, savedInstanceState)
        bFilter = inflatedView.findViewById<ImageButton>(R.id.bFilter)
        bFilter!!.setOnClickListener { v: View? ->
            val intent = Intent(requireActivity(), BlotterFilterActivity::class.java)
            blotterFilter.toIntent(intent)
            intent.putExtra(
                BlotterFilterActivity.IS_ACCOUNT_FILTER,
                isAccountBlotter && blotterFilter.accountId > 0
            )
            startActivityForResult(intent, FILTER_REQUEST)
        }
        totalText = inflatedView.findViewById<TextView>(R.id.total)
        totalText!!.setOnClickListener { view: View? -> showTotals() }
        val intent: Intent = requireActivity().intent
        if (intent != null) {
            blotterFilter = WhereFilter.fromIntent(intent)
            saveFilter = intent.getBooleanExtra(SAVE_FILTER, false)
            isAccountBlotter =
                intent.getBooleanExtra(BlotterFilterActivity.IS_ACCOUNT_FILTER, false)
        }
        if (savedInstanceState != null) {
            blotterFilter = WhereFilter.fromBundle(savedInstanceState)
        }
        if (saveFilter && blotterFilter.isEmpty) {
            blotterFilter = WhereFilter.fromSharedPreferences(requireActivity().getPreferences(0))
        }
        showAllBlotterButtons =
            !isAccountBlotter && !MyPreferences.isCollapseBlotterButtons(requireContext())
        if (showAllBlotterButtons) {
            bTransfer = inflatedView.findViewById<ImageButton>(R.id.bTransfer)
            bTransfer!!.visibility = View.VISIBLE
            bTransfer!!.setOnClickListener { arg0: View? ->
                addItem(
                    NEW_TRANSFER_REQUEST,
                    TransferActivity::class.java
                )
            }
            bTemplate = inflatedView.findViewById<ImageButton>(R.id.bTemplate)
            bTemplate!!.visibility = View.VISIBLE
            bTemplate!!.setOnClickListener { v: View? -> createFromTemplate() }
        }
        bSearch = inflatedView.findViewById<ImageButton>(R.id.bSearch)
        bSearch!!.setOnClickListener { method: View? ->
            val searchText: EditText = inflatedView.findViewById<EditText>(R.id.search_text)
            val searchLayout: FrameLayout =
                inflatedView.findViewById<FrameLayout>(R.id.search_text_frame)
            val searchTextClearButton: ImageButton =
                inflatedView.findViewById<ImageButton>(R.id.search_text_clear)
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            searchText.onFocusChangeListener = OnFocusChangeListener { view: View, b: Boolean ->
                if (!view.hasFocus()) {
                    imm.hideSoftInputFromWindow(searchLayout.windowToken, 0)
                }
            }
            searchTextClearButton.setOnClickListener { view: View? ->
                searchText.setText(
                    ""
                )
            }
            if (searchLayout.visibility == View.VISIBLE) {
                imm.hideSoftInputFromWindow(searchLayout.windowToken, 0)
                searchLayout.visibility = View.GONE
                return@setOnClickListener
            }
            searchLayout.visibility = View.VISIBLE
            searchText.requestFocusFromTouch()
            imm.showSoftInput(searchText, InputMethodManager.SHOW_IMPLICIT)
            searchText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                }

                override fun onTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                }

                override fun afterTextChanged(editable: Editable) {
                    val clearButton: ImageButton = inflatedView.findViewById(R.id.search_text_clear)
                    val text = editable.toString()
                    blotterFilter.remove(BlotterFilter.NOTE)
                    if (!text.isEmpty()) {
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
                if (!searchFilterText.isEmpty()) {
                    searchFilterText = searchFilterText.substring(1, searchFilterText.length - 1)
                    searchText.setText(searchFilterText)
                }
            }
        }
        applyFilter()
        applyPopupMenu()
        calculateTotals()
        prepareTransactionActionGrid()
        prepareAddButtonActionGrid()
    }

    private fun applyPopupMenu() {
        bMenu = inflatedView.findViewById<ImageButton>(R.id.bMenu)
        if (isAccountBlotter) {
            bMenu!!.setOnClickListener { v: View? ->
                val popupMenu =
                    PopupMenu(requireContext(), bMenu)
                val accountId = blotterFilter.accountId
                if (accountId != -1L) {
                    // get account type
                    val account =
                        db.getAccount(accountId)
                    val type =
                        AccountType.valueOf(account.type)
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
        } else {
            bMenu!!.visibility = View.GONE
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
        transactionActionGrid = QuickActionGrid(requireActivity())
        transactionActionGrid!!.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_info, R.string.info)
        )
        transactionActionGrid!!.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_edit, R.string.edit)
        )
        transactionActionGrid!!.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_trash, R.string.delete)
        )
        transactionActionGrid!!.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_copy, R.string.duplicate)
        )
        transactionActionGrid!!.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_tick, R.string.clear)
        )
        transactionActionGrid!!.addQuickAction(
            MyQuickAction(requireContext(), R.drawable.ic_action_double_tick, R.string.reconcile)
        )
        transactionActionGrid!!.setOnQuickActionClickListener(transactionActionListener)
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
        addButtonActionGrid = QuickActionGrid(requireContext())
        addButtonActionGrid!!.addQuickAction(
            MyQuickAction(
                requireContext(),
                R.drawable.actionbar_add_big,
                R.string.transaction
            )
        )
        addButtonActionGrid!!.addQuickAction(
            MyQuickAction(
                requireContext(),
                R.drawable.ic_action_transfer,
                R.string.transfer
            )
        )
        if (addTemplateToAddButton()) {
            addButtonActionGrid!!.addQuickAction(
                MyQuickAction(
                    requireContext(),
                    R.drawable.actionbar_tiles_large,
                    R.string.template
                )
            )
        } else {
            addButtonActionGrid!!.setNumColumns(2)
        }
        addButtonActionGrid!!.setOnQuickActionClickListener(addButtonActionListener)
    }

    protected fun addTemplateToAddButton(): Boolean {
        return true
    }

    private val addButtonActionListener =
        OnQuickActionClickListener { widget: QuickActionWidget?, position: Int ->
            when (position) {
                0 -> addItem(
                    NEW_TRANSACTION_REQUEST,
                    TransactionActivity::class.java
                )
                1 -> addItem(NEW_TRANSFER_REQUEST, TransferActivity::class.java)
                2 -> createFromTemplate()
            }
        }

    private fun clearTransaction(selectedId: Long) {
        BlotterOperations(requireActivity(), db, selectedId, callback).clearTransaction()
        recreateCursor()
    }

    private fun reconcileTransaction(selectedId: Long) {
        BlotterOperations(requireActivity(), db, selectedId, callback).reconcileTransaction()
        recreateCursor()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        blotterFilter.toBundle(outState)
    }

    protected fun createFromTemplate() {
        val intent = Intent(requireActivity(), SelectTemplateActivity::class.java)
        startActivityForResult(intent, NEW_TRANSACTION_FROM_TEMPLATE_REQUEST)
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

    override fun onPopupItemSelected(
        itemId: Int,
        view: View?,
        position: Int,
        id: Long
    ): Boolean {
        if (!super.onPopupItemSelected(itemId, view, position, id)) {
            when (itemId) {
                MENU_DUPLICATE -> {
                    duplicateTransaction(id, 1)
                    return true
                }
                MENU_SAVE_AS_TEMPLATE -> {
                    BlotterOperations(requireActivity(), db, id, callback).duplicateAsTemplate()
                    Toast.makeText(
                        requireContext(),
                        R.string.save_as_template_success,
                        Toast.LENGTH_SHORT
                    )
                        .show()
                    return true
                }
            }
        }
        return false
    }

    private fun duplicateTransaction(id: Long, multiplier: Int): Long {
        val newId =
            BlotterOperations(requireActivity(), db, id, callback).duplicateTransaction(multiplier)
        val toastText: String
        toastText = if (multiplier > 1) {
            getString(R.string.duplicate_success_with_multiplier, multiplier)
        } else {
            getString(R.string.duplicate_success)
        }
        Toast.makeText(requireContext(), toastText, Toast.LENGTH_LONG).show()
        recreateCursor()
        AccountWidget.updateWidgets(requireContext())
        return newId
    }

    override fun addItem() {
        if (showAllBlotterButtons) {
            addItem(NEW_TRANSACTION_REQUEST, TransactionActivity::class.java)
        } else {
            addButtonActionGrid!!.show(bAdd)
        }
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

    override fun createCursor(): Cursor {
        return if (isAccountBlotter) {
            db.getBlotterForAccount(blotterFilter)
        } else {
            db.getBlotter(blotterFilter)
        }
    }

    override fun createAdapter(cursor: Cursor?): ListAdapter {
        return if (isAccountBlotter) {
            TransactionsListAdapter(requireContext(), db, cursor)
        } else {
            BlotterListAdapter(requireContext(), db, cursor)
        }
    }

    override fun deleteItem(
        v: View?,
        position: Int,
        id: Long
    ) {
        deleteTransaction(id)
    }

    private fun deleteTransaction(id: Long) {
        BlotterOperations(requireActivity(), db, id, callback).deleteTransaction()
    }

    fun afterDeletingTransaction(id: Long) {
        recreateCursor()
        AccountWidget.updateWidgets(requireContext())
    }

    override fun editItem(v: View?, position: Int, id: Long) {
        editTransaction(id)
    }

    private fun editTransaction(id: Long) {
        BlotterOperations(this.requireActivity(), db, id, callback).editTransaction()
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
            calculateTotals()
        }
    }

    private fun createTransactionFromTemplate(data: Intent) {
        val templateId = data.getLongExtra(SelectTemplateActivity.TEMPATE_ID, -1)
        val multiplier = data.getIntExtra(SelectTemplateActivity.MULTIPLIER, 1)
        val edit =
            data.getBooleanExtra(SelectTemplateActivity.EDIT_AFTER_CREATION, false)
        if (templateId > 0) {
            val id = duplicateTransaction(templateId, multiplier)
            val t = db.getTransaction(id)
            if (t.fromAmount == 0L || edit) {

                BlotterOperations(requireActivity(), db, id, callback).asNewFromTemplate()
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
            bAdd.visibility = if (a != null && a.isActive) View.VISIBLE else View.GONE
            if (showAllBlotterButtons) {
                bTransfer!!.visibility = if (a != null && a.isActive) View.VISIBLE else View.GONE
            }
        }
        val title = blotterFilter.title
        if (title != null) {
            requireActivity().title = getString(R.string.blotter) + " : " + title
        }
        updateFilterImage()
    }

    protected fun updateFilterImage() {
        FilterState.updateFilterColor(requireContext(), blotterFilter, bFilter)
    }

    private val inflater: NodeInflater? = null
    private var selectedId: Long = -1

    override fun onItemClick(v: View?, position: Int, id: Long) {
        if (MyPreferences.isQuickMenuEnabledForTransaction(requireContext())) {
            selectedId = id
            transactionActionGrid?.show(v)
        } else {
            showTransactionInfo(id)
        }
    }

    override fun viewItem(v: View?, position: Int, id: Long) {
        showTransactionInfo(id)
    }

    private fun showTransactionInfo(id: Long) {
        val transactionInfoView = TransactionInfoDialog(requireContext(), db, inflater)
        transactionInfoView.show(this.requireActivity(), id, callback)
    }

    override fun integrityCheck() {
        IntegrityCheckTask(requireActivity()).execute(
            IntegrityCheckRunningBalance(
                requireContext(),
                db
            )
        )
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
        const val SAVE_FILTER = "saveFilter"
        const val EXTRA_FILTER_ACCOUNTS = "filterAccounts"

        fun newInstance(saveFilter: Boolean): BlotterFragment {
            val bundle = Bundle().apply {
                putBoolean(SAVE_FILTER, saveFilter)
            }
            return BlotterFragment().apply {
                arguments = bundle
            }
        }
    }
}
