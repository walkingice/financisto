package ru.orangesoftware.financisto2.common.view

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

interface Presenter<T, VH> where VH : RecyclerView.ViewHolder {
    fun onCreateViewHolder(parent: ViewGroup): VH
    fun onBindViewHolder(viewHolder: VH, item: T)
}
