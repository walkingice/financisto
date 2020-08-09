package ru.orangesoftware.financisto2

import android.database.Cursor
import android.os.Parcelable
import android.util.Log
import android.widget.ListAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment

abstract class AbstractListViewController(
    val fragment: Fragment
) {

    lateinit var listView: ListView
    protected lateinit var cursor: Cursor
    protected lateinit var adapter: ListAdapter

    fun onCreate() {
        val rootView = fragment.view ?: return
        listView = rootView.findViewById<ListView>(android.R.id.list)
        cursor = createCursorImpl()
        recreateAdapter(cursor)
    }

    fun onDestroy() {
        cursor.close()
    }

    fun recreateCursor() {
        Log.i("AbstractListFragment", "Recreating cursor")
        val state: Parcelable = listView.onSaveInstanceState()
        try {
            createCursorImpl().also {
                recreateAdapter(it)
                cursor.close()
                cursor = it
            }
        } finally {
            listView.onRestoreInstanceState(state)
            onCursorRecreatedImpl()
        }
    }

    private fun recreateAdapter(cursor: Cursor) {
        adapter = createAdapterImpl(cursor)
        listView.adapter = adapter
        onAdapterRecreatedImpl()
    }

    protected abstract fun createCursorImpl(): Cursor
    protected abstract fun createAdapterImpl(cursor: Cursor): ListAdapter
    protected abstract fun onCursorRecreatedImpl()
    protected abstract fun onAdapterRecreatedImpl()
}
