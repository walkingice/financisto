package ru.orangesoftware.financisto2

import android.database.Cursor
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ListAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import ru.orangesoftware.financisto2.common.view.CursorAdapter

typealias ClickListener = (view: View, position: Int, id: Long) -> Unit

abstract class AbstractListViewController(
    val fragment: Fragment
) {

    protected lateinit var cursor: Cursor
    private var isLegacy = true

    private var recyclerView: RecyclerView? = null
    private var recyclerAdapter: CursorAdapter<*, *, *>? = null
    private var listView: ListView? = null
    private var listAdapter: ListAdapter? = null

    fun onCreate() {
        val rootView = fragment.view ?: return
        val list = rootView.findViewById<View>(android.R.id.list)
        isLegacy = list is ListView
        cursor = createCursorImpl()
        if (isLegacy) {
            listView = list as ListView

        } else {
            recyclerView = list as RecyclerView
        }
        recreateAdapter(cursor)
    }

    fun onDestroy() {
        cursor.close()
        listView?.adapter = null
        recyclerView?.adapter = null
    }

    fun recreateCursor() {
        Log.i("AbstractListFragment", "Recreating cursor")
        try {
            createCursorImpl().also {
                if (isLegacy) {
                    recreateCursorForListView(it)
                } else {
                    onCursorUpdateForRecyclerView(it)
                }
                cursor.close()
                cursor = it
            }
        } finally {
            onCursorRecreatedImpl()
        }
    }

    private fun onCursorUpdateForRecyclerView(cursor: Cursor) {
        recyclerAdapter?.cursor = cursor
        recyclerAdapter?.notifyDataSetChanged()
    }

    private fun recreateCursorForListView(cursor: Cursor) {
        val state = listView?.onSaveInstanceState()
        recreateAdapter(cursor)
        if (state != null) {
            listView?.onRestoreInstanceState(state)
        }
    }

    fun setOnItemLongClickListener(clickListener: ClickListener) {
        if (isLegacy) {
            listView?.setOnItemLongClickListener { _: AdapterView<*>?, v: View, position: Int, id: Long ->
                clickListener(v, position, id)
                true
            }
        }
    }

    fun setOnItemClickListener(clickListener: ClickListener) {
        if (isLegacy) {
            listView?.setOnItemClickListener { _: AdapterView<*>?, v: View, position: Int, id: Long ->
                clickListener(v, position, id)
            }
        }
    }

    private fun recreateAdapter(cursor: Cursor) {
        if (isLegacy) {
            listAdapter = createListAdapterImpl(cursor)
            listView?.adapter = listAdapter
        } else {
            // recycler adapter only need to be created once
            if (recyclerAdapter == null) {
                recyclerAdapter = createRecyclerAdapterImpl(cursor)
                recyclerView?.adapter = recyclerAdapter
                onCursorUpdateForRecyclerView(cursor)
            }
        }
        onAdapterRecreatedImpl()
    }

    protected abstract fun createCursorImpl(): Cursor
    protected abstract fun createListAdapterImpl(cursor: Cursor): ListAdapter?
    protected abstract fun createRecyclerAdapterImpl(cursor: Cursor): CursorAdapter<*, *, *>?
    protected abstract fun onCursorRecreatedImpl()
    protected abstract fun onAdapterRecreatedImpl()
}
