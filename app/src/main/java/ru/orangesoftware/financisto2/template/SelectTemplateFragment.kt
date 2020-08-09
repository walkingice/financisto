package ru.orangesoftware.financisto2.template

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.ListAdapter
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.adapter.TemplateListAdapter

class SelectTemplateFragment : TemplatesListFragment() {

    override fun getLayoutResourceId(): Int = R.layout.fragment_select_template

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
