package ru.orangesoftware.financisto2.common.view

import android.database.Cursor
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

abstract class CursorAdapter<T : Any, VH : RecyclerView.ViewHolder, P : Presenter<T, VH>>(
    private val presenter: P
) : RecyclerView.Adapter<VH>() {

    var cursor: Cursor? = null

    override fun getItemViewType(position: Int): Int {
        // only provide one view type
        return 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return presenter.onCreateViewHolder(parent)
    }

    override fun getItemCount(): Int {
        return cursor?.count ?: 0
    }


    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = cursor ?: return
        val item: T = getItem(position = position, cursor = c)
        presenter.onBindViewHolder(holder, item)
    }

    abstract fun getItem(cursor: Cursor, position: Int): T
}


