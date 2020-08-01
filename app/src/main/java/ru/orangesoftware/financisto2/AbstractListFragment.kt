package ru.orangesoftware.financisto2

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.ListView
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.utils.MenuItemInfo
import ru.orangesoftware.financisto.utils.PinProtection
import java.util.LinkedList

abstract class AbstractListFragment : Fragment() {
    val MENU_VIEW = Menu.FIRST + 1
    val MENU_EDIT = Menu.FIRST + 2
    val MENU_DELETE = Menu.FIRST + 3
    val MENU_ADD = Menu.FIRST + 4

    protected lateinit var db: DatabaseAdapter
    protected lateinit var cursor: Cursor
    protected lateinit var adapter: ListAdapter
    protected lateinit var listView: ListView
    protected lateinit var bAdd: ImageButton

    protected var enablePin = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DatabaseAdapter(requireContext())
        db.open()
        cursor = createCursor()
    }

    override fun onDestroy() {
        db.close()
        cursor.close()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        if (enablePin) PinProtection.lock(requireContext())
    }

    override fun onResume() {
        super.onResume()
        if (enablePin) PinProtection.unlock(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView = view.findViewById(android.R.id.list)
        internalOnCreate(view, savedInstanceState)
        recreateAdapter(cursor)
        listView.setOnItemLongClickListener { _: AdapterView<*>?, v: View?, position: Int, id: Long ->
            val popupMenu = PopupMenu(requireContext(), v)
            val menu = popupMenu.menu
            val menus: List<MenuItemInfo> = createContextMenus(id)
            var i = 0
            for (m in menus) {
                if (m.enabled) {
                    menu.add(0, m.menuId, i++, m.titleId)
                }
            }
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                onPopupItemSelected(item.itemId, v, position, id)
            }
            popupMenu.show()
            true
        }
        listView.setOnItemClickListener { _, v, position, id -> onItemClick(v, position, id) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            recreateCursor()
        }
    }

    protected open fun internalOnCreate(inflatedView: View, savedInstanceState: Bundle?) {
        bAdd = inflatedView.findViewById(R.id.bAdd)
        bAdd.setOnClickListener { arg0: View? -> addItem() }
    }

    protected open fun createContextMenus(id: Long): MutableList<MenuItemInfo> {
        val menus: MutableList<MenuItemInfo> = LinkedList()
        menus.add(MenuItemInfo(MENU_VIEW, R.string.view))
        menus.add(MenuItemInfo(MENU_EDIT, R.string.edit))
        menus.add(MenuItemInfo(MENU_DELETE, R.string.delete))
        return menus
    }

    open fun onPopupItemSelected(itemId: Int, view: View?, position: Int, id: Long): Boolean {
        return when (itemId) {
            MENU_VIEW -> {
                viewItem(view, position, id)
                true
            }
            MENU_EDIT -> {
                editItem(view, position, id)
                true
            }
            MENU_DELETE -> {
                deleteItem(view, position, id)
                true
            }
            else -> false
        }
    }

    protected open fun onItemClick(v: View?, position: Int, id: Long) {
        viewItem(v, position, id)
    }

    protected open fun addItem() {}

    protected fun recreateCursor() {
        Log.i("AbstractListFragment", "Recreating cursor")
        val state: Parcelable = listView.onSaveInstanceState()
        try {
            createCursor().also {
                recreateAdapter(it)
                cursor.close()
                cursor = it
            }
        } finally {
            listView.onRestoreInstanceState(state)
            onCursorRecreated()
        }
    }

    private fun recreateAdapter(cursor: Cursor) {
        adapter = createAdapter(cursor)
        listView.adapter = adapter
        onAdapterRecreated()
    }

    protected open fun onCursorRecreated() {}
    protected open fun onAdapterRecreated() {}

    open fun integrityCheck() {}

    protected abstract fun createCursor(): Cursor
    protected abstract fun createAdapter(cursor: Cursor?): ListAdapter
    protected abstract fun deleteItem(v: View?, position: Int, id: Long)
    protected abstract fun editItem(v: View?, position: Int, id: Long)
    protected abstract fun viewItem(v: View?, position: Int, id: Long)
}
