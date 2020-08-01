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
package ru.orangesoftware.financisto2.blotter

import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ResourceCursorAdapter
import android.widget.TextView
import androidx.annotation.LayoutRes
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper.BlotterColumns
import ru.orangesoftware.financisto.model.Category
import ru.orangesoftware.financisto.model.CategoryEntity
import ru.orangesoftware.financisto.model.TransactionStatus
import ru.orangesoftware.financisto.recur.Recurrence
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.StringUtil
import ru.orangesoftware.financisto.utils.TransactionTitleUtils
import ru.orangesoftware.financisto.utils.Utils
import java.util.Date
import java.util.HashMap

open class BlotterListAdapter @JvmOverloads constructor(
    context: Context,
    protected val db: DatabaseAdapter,
    cursor: Cursor,
    @LayoutRes layoutId: Int = R.layout.list_item_blotter,
    autoRequery: Boolean = false
) : ResourceCursorAdapter(context, layoutId, cursor, autoRequery) {
    private val dt = Date()
    protected val sb = StringBuilder()
    protected val icBlotterIncome: Drawable
    protected val icBlotterExpense: Drawable
    protected val icBlotterTransfer: Drawable
    protected val icBlotterSplit: Drawable
    protected val u: Utils
    private val colors: IntArray
    private var allChecked = true
    private val checkedItems =
        HashMap<Long, Boolean>()
    protected open val isShowRunningBalance: Boolean

    init {
        icBlotterIncome = context.resources.getDrawable(R.drawable.ic_action_arrow_left_bottom)
        icBlotterExpense = context.resources.getDrawable(R.drawable.ic_action_arrow_right_top)
        icBlotterTransfer = context.resources.getDrawable(R.drawable.ic_action_arrow_top_down)
        icBlotterSplit = context.resources.getDrawable(R.drawable.ic_action_share)
        u = Utils(context)
        colors = initializeColors(context)
        isShowRunningBalance = MyPreferences.isShowRunningBalance(context)
    }

    val allCheckedIds: LongArray
        get() {
            val checkedCount = checkedCount
            val ids = LongArray(checkedCount)
            var k = 0
            if (allChecked) {
                val count = count
                val addAll = count == checkedCount
                for (i in 0 until count) {
                    val id = getItemId(i)
                    val checked = addAll || getCheckedState(id)
                    if (checked) {
                        ids[k++] = id
                    }
                }
            } else {
                for (id in checkedItems.keys) {
                    ids[k++] = id
                }
            }
            return ids
        }

    private fun initializeColors(context: Context): IntArray {
        val r = context.resources
        val statuses = TransactionStatus.values()
        val count = statuses.size
        val colors = IntArray(count)
        for (i in 0 until count) {
            colors[i] = r.getColor(statuses[i].colorId)
        }
        return colors
    }

    override fun newView(
        context: Context,
        cursor: Cursor,
        parent: ViewGroup
    ): View {
        val view = super.newView(context, cursor, parent)
        createHolder(view)
        return view
    }

    private fun createHolder(view: View) {
        view.tag = BlotterViewHolder(view)
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        val v = view.tag as BlotterViewHolder
        bindView(v, context, cursor)
    }

    protected open fun bindView(v: BlotterViewHolder, context: Context?, cursor: Cursor) {
        val toAccountId = cursor.getLong(BlotterColumns.to_account_id.ordinal)
        val isTemplate = cursor.getInt(BlotterColumns.is_template.ordinal)
        val noteView = if (isTemplate == 1) v.bottomView else v.centerView
        if (toAccountId > 0) {
            v.topView.setText(R.string.transfer)
            val fromAccountTitle = cursor.getString(BlotterColumns.from_account_title.ordinal)
            val toAccountTitle = cursor.getString(BlotterColumns.to_account_title.ordinal)
            u.setTransferTitleText(noteView, fromAccountTitle, toAccountTitle)
            val fromCurrencyId = cursor.getLong(BlotterColumns.from_account_currency_id.ordinal)
            val fromCurrency = CurrencyCache.getCurrency(db, fromCurrencyId)
            val toCurrencyId = cursor.getLong(BlotterColumns.to_account_currency_id.ordinal)
            val toCurrency = CurrencyCache.getCurrency(db, toCurrencyId)
            val fromAmount = cursor.getLong(BlotterColumns.from_amount.ordinal)
            val toAmount = cursor.getLong(BlotterColumns.to_amount.ordinal)
            val fromBalance = cursor.getLong(BlotterColumns.from_account_balance.ordinal)
            val toBalance = cursor.getLong(BlotterColumns.to_account_balance.ordinal)
            u.setTransferAmountText(
                v.rightCenterView,
                fromCurrency,
                fromAmount,
                toCurrency,
                toAmount
            )
            if (v.rightView != null) {
                u.setTransferBalanceText(
                    v.rightView,
                    fromCurrency,
                    fromBalance,
                    toCurrency,
                    toBalance
                )
            }
            v.iconView.setImageDrawable(icBlotterTransfer)
            v.iconView.setColorFilter(u.transferColor)
        } else {
            val fromAccountTitle =
                cursor.getString(BlotterColumns.from_account_title.ordinal)
            v.topView.text = fromAccountTitle
            setTransactionTitleText(cursor, noteView)
            sb.setLength(0)
            val fromCurrencyId = cursor.getLong(BlotterColumns.from_account_currency_id.ordinal)
            val fromCurrency = CurrencyCache.getCurrency(db, fromCurrencyId)
            val amount = cursor.getLong(BlotterColumns.from_amount.ordinal)
            val originalCurrencyId = cursor.getLong(BlotterColumns.original_currency_id.ordinal)
            if (originalCurrencyId > 0) {
                val originalCurrency = CurrencyCache.getCurrency(db, originalCurrencyId)
                val originalAmount = cursor.getLong(BlotterColumns.original_from_amount.ordinal)
                u.setAmountText(
                    sb,
                    v.rightCenterView,
                    originalCurrency,
                    originalAmount,
                    fromCurrency,
                    amount,
                    true
                )
            } else {
                u.setAmountText(sb, v.rightCenterView, fromCurrency, amount, true)
            }
            val categoryId = cursor.getLong(BlotterColumns.category_id.ordinal)
            if (Category.isSplit(categoryId)) {
                v.iconView.setImageDrawable(icBlotterSplit)
                v.iconView.setColorFilter(u.splitColor)
            } else if (amount == 0L) {
                val categoryType = cursor.getInt(BlotterColumns.category_type.ordinal)
                if (categoryType == CategoryEntity.TYPE_INCOME) {
                    v.iconView.setImageDrawable(icBlotterIncome)
                    v.iconView.setColorFilter(u.positiveColor)
                } else if (categoryType == CategoryEntity.TYPE_EXPENSE) {
                    v.iconView.setImageDrawable(icBlotterExpense)
                    v.iconView.setColorFilter(u.negativeColor)
                }
            } else {
                if (amount > 0) {
                    v.iconView.setImageDrawable(icBlotterIncome)
                    v.iconView.setColorFilter(u.positiveColor)
                } else if (amount < 0) {
                    v.iconView.setImageDrawable(icBlotterExpense)
                    v.iconView.setColorFilter(u.negativeColor)
                }
            }
            if (v.rightView != null) {
                val balance = cursor.getLong(BlotterColumns.from_account_balance.ordinal)
                v.rightView.text = Utils.amountToString(
                    fromCurrency,
                    balance,
                    false
                )
            }
        }
        setIndicatorColor(v, cursor)
        if (isTemplate == 1) {
            val templateName = cursor.getString(BlotterColumns.template_name.ordinal)
            v.centerView.text = templateName
        } else {
            val recurrence = cursor.getString(BlotterColumns.recurrence.ordinal)
            if (isTemplate == 2 && recurrence != null) {
                val r = Recurrence.parse(recurrence)
                v.bottomView.text = r.toInfoString(context)
                v.bottomView.setTextColor(v.topView.textColors.defaultColor)
            } else {
                val date = cursor.getLong(BlotterColumns.datetime.ordinal)
                dt.time = date
                v.bottomView.text = StringUtil.capitalize(
                    DateUtils.formatDateTime(
                        context, dt.time,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY
                    )
                )
                if (isTemplate == 0 && date > System.currentTimeMillis()) {
                    u.setFutureTextColor(v.bottomView)
                } else {
                    v.bottomView.setTextColor(v.topView.textColors.defaultColor)
                }
            }
        }
        removeRightViewIfNeeded(v)
        if (v.checkBox != null) {
            val parent = cursor.getLong(BlotterColumns.parent_id.ordinal)
            val id = if (parent > 0) parent else cursor.getLong(BlotterColumns._id.ordinal)
            v.checkBox.setOnClickListener(View.OnClickListener {
                updateCheckedState(id, allChecked xor v.checkBox.isChecked)
            })
            v.checkBox.isChecked = getCheckedState(id)
        }
    }

    private fun setTransactionTitleText(
        cursor: Cursor,
        noteView: TextView
    ) {
        sb.setLength(0)
        val payee = cursor.getString(BlotterColumns.payee.ordinal)
        val note = cursor.getString(BlotterColumns.note.ordinal)
        val locationId = cursor.getLong(BlotterColumns.location_id.ordinal)
        val location = getLocationTitle(cursor, locationId)
        val categoryId = cursor.getLong(BlotterColumns.category_id.ordinal)
        val category = getCategoryTitle(cursor, categoryId)
        val text = TransactionTitleUtils.generateTransactionTitle(
            sb,
            payee,
            note,
            location,
            categoryId,
            category
        )
        noteView.text = text
        noteView.setTextColor(Color.WHITE)
    }

    private fun getCategoryTitle(
        cursor: Cursor,
        categoryId: Long
    ): String {
        var category = ""
        if (categoryId != 0L) {
            category = cursor.getString(BlotterColumns.category_title.ordinal)
        }
        return category
    }

    private fun getLocationTitle(
        cursor: Cursor,
        locationId: Long
    ): String {
        var location = ""
        if (locationId > 0) {
            location = cursor.getString(BlotterColumns.location.ordinal)
        }
        return location
    }

    fun removeRightViewIfNeeded(v: BlotterViewHolder) {
        if (v.rightView != null && !isShowRunningBalance) {
            v.rightView.visibility = View.GONE
        }
    }

    fun setIndicatorColor(v: BlotterViewHolder, cursor: Cursor) {
        val status =
            TransactionStatus.valueOf(cursor.getString(BlotterColumns.status.ordinal))
        v.indicator.setBackgroundColor(colors[status.ordinal])
    }

    private fun getCheckedState(id: Long): Boolean {
        return checkedItems[id] == null == allChecked
    }

    private fun updateCheckedState(id: Long, checked: Boolean) {
        if (checked) {
            checkedItems[id] = true
        } else {
            checkedItems.remove(id)
        }
    }

    val checkedCount: Int
        get() = if (allChecked) count - checkedItems.size else checkedItems.size

    fun checkAll() {
        allChecked = true
        checkedItems.clear()
        notifyDataSetInvalidated()
    }

    fun uncheckAll() {
        allChecked = false
        checkedItems.clear()
        notifyDataSetInvalidated()
    }

    class BlotterViewHolder(view: View) {
        val layout: ViewGroup = view.findViewById(R.id.layout)
        val indicator: View = view.findViewById(R.id.indicator)
        val topView: TextView = view.findViewById(R.id.top)
        val centerView: TextView = view.findViewById(R.id.center)
        val bottomView: TextView = view.findViewById(R.id.bottom)
        val rightCenterView: TextView = view.findViewById(R.id.right_center)
        val rightView: TextView? = view.findViewById(R.id.right)
        val iconView: ImageView = view.findViewById(R.id.right_top)
        val checkBox: CheckBox? = view.findViewById(R.id.cb)

    }
}
