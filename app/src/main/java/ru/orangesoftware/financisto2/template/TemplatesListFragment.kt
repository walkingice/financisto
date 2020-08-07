package ru.orangesoftware.financisto2.template

import android.database.Cursor
import android.os.Bundle
import android.view.View
import android.widget.ListAdapter
import android.widget.TextView
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto2.blotter.BlotterFragment
import ru.orangesoftware.financisto2.blotter.BlotterListAdapter

open class TemplatesListFragment : BlotterFragment() {

    override fun calculateTotals() {
        // do nothing
    }

    override fun createCursor(): Cursor {
        val sortOrder: String = when (MyPreferences.getTemplatesSortOrder(requireContext())) {
            MyPreferences.TemplatesSortOrder.NAME -> BlotterFilter.SORT_BY_TEMPLATE_NAME
            MyPreferences.TemplatesSortOrder.ACCOUNT -> BlotterFilter.SORY_BY_ACCOUNT_NAME
            else -> BlotterFilter.SORT_NEWER_TO_OLDER
        }
        return db.getAllTemplates(blotterFilter, sortOrder)
    }

    override fun createAdapter(cursor: Cursor): ListAdapter {
        return object : BlotterListAdapter(requireContext(), db, cursor) {
            override val isShowRunningBalance: Boolean
                get() = false
        }
    }

    override fun internalOnCreate(view: View, savedInstanceState: Bundle?) {
        super.internalOnCreate(view, savedInstanceState)
        // remove filter button and totals
//        bFilter?.visibility = View.GONE
        if (showAllBlotterButtons) {
//            bTemplate?.visibility = View.GONE
        }
        totalText?.visibility = View.GONE
        internalOnCreateTemplates(view)
    }

    override fun addTemplateToAddButton(): Boolean = false

    protected fun internalOnCreateTemplates(view: View) {
        // change empty list message
        view.findViewById<TextView>(android.R.id.empty)?.setText(R.string.no_templates)
        // fix filter
        blotterFilter = WhereFilter("templates")
        blotterFilter.eq(BlotterFilter.IS_TEMPLATE, 1.toString())
        blotterFilter.eq(BlotterFilter.PARENT_ID, 0.toString())
    }

    companion object {
        fun newInstance(): TemplatesListFragment {
            val args = Bundle()
            val fragment = TemplatesListFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
