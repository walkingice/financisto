package ru.orangesoftware.financisto2.template

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ListAdapter
import android.widget.TextView
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.activity.MyEntityListActivity
import ru.orangesoftware.financisto.adapter.TemplateListAdapter
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.filter.Criteria
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.widget.SearchFilterTextWatcherListener

class SelectTemplateFragment : TemplatesListFragment() {

    override fun getLayoutResourceId(): Int = R.layout.fragment_select_template
    private var multiplierText: TextView? = null
    private var searchFilter: EditText? = null

    override fun internalOnCreate(view: View, savedInstanceState: Bundle?) {
        internalOnCreateTemplates(view)
        listViewController.listView.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                returnResult(id, true)
                true
            }
        var b = view.findViewById<Button>(R.id.bEditTemplates)
        b.setOnClickListener { arg0: View? ->
            val intent = Intent(requireActivity(), TemplatesListActivity::class.java)
            startActivity(intent)
            requireActivity().setResult(Activity.RESULT_CANCELED)
            requireActivity().finish()
        }
        b = view.findViewById(R.id.bCancel)
        b.setOnClickListener { arg0: View? ->
            requireActivity().setResult(Activity.RESULT_CANCELED)
            requireActivity().finish()
        }
        multiplierText = view.findViewById(R.id.multiplier)

        searchFilter = view.findViewById(R.id.searchFilter)
        searchFilter!!.addTextChangedListener(object :
            SearchFilterTextWatcherListener(MyEntityListActivity.FILTER_DELAY_MILLIS) {
            override fun clearFilter(oldFilter: String) {
                blotterFilter.remove(BlotterFilter.TEMPLATE_NAME)
            }

            override fun applyFilter(filter: String) {
                var filter = filter
                if (!TextUtils.isEmpty(filter)) {
                    filter = "%" + filter.replace(" ", "%") + "%"
                    blotterFilter.put(
                        Criteria.or(
                            Criteria(
                                BlotterFilter.TEMPLATE_NAME,
                                WhereFilter.Operation.LIKE,
                                filter
                            ),
                            Criteria(
                                BlotterFilter.CATEGORY_NAME,
                                WhereFilter.Operation.LIKE,
                                filter
                            )
                        )
                    )
                }
                listViewController.recreateCursor()
            }
        })
    }

    override fun createCursor(): Cursor {
        return super.createCursor()
    }

    override fun registerForContextMenu(view: View) {}
    override fun createAdapter(cursor: Cursor): ListAdapter {
        return TemplateListAdapter(requireContext(), db, cursor)
    }

    override fun onItemClick(v: View, position: Int, id: Long) {
        returnResult(id, false)
    }

    override fun viewItem(v: View, position: Int, id: Long) {
        returnResult(id, false)
    }

    override fun editItem(v: View, position: Int, id: Long) {
    }

    fun returnResult(id: Long, edit: Boolean) {
        val intent = Intent()
        intent.putExtra(TEMPATE_ID, id)
        if (edit) intent.putExtra(EDIT_AFTER_CREATION, true)
        requireActivity().setResult(Activity.RESULT_OK, intent)
        requireActivity().finish()
    }

    companion object {
        const val TEMPATE_ID = "template_id"
        const val EDIT_AFTER_CREATION = "edit_after_creation"

        fun newInstance(): TemplatesListFragment {
            val args = Bundle()
            val fragment = SelectTemplateFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
