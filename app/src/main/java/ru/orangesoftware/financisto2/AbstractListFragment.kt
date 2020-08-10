package ru.orangesoftware.financisto2

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.ImageButton
import android.widget.ListAdapter
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.MenuItemInfo
import ru.orangesoftware.financisto.utils.PinProtection
import ru.orangesoftware.financisto2.common.view.CursorAdapter
import java.util.LinkedList

private const val MENU_VIEW = Menu.FIRST + 1
private const val MENU_EDIT = Menu.FIRST + 2
private const val MENU_DELETE = Menu.FIRST + 3
private const val MENU_ADD = Menu.FIRST + 4

abstract class AbstractListFragment : Fragment() {

    protected var enablePin = true
    protected val listViewController = ListViewController(this)
    protected var bAdd: ImageButton? = null

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
        listViewController.onCreate()
        listViewController.setOnItemLongClickListener { v, i, l -> onItemLongClick(v, i, l) }
        listViewController.setOnItemClickListener { v, i, l -> onItemClick(v, i, l) }
        internalOnCreate(view, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listViewController.onDestroy()
    }

    protected open fun internalOnCreate(inflatedView: View, savedInstanceState: Bundle?) {
        bAdd = inflatedView.findViewById(R.id.bAdd)
        bAdd?.setOnClickListener { onButtonAddClicked() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            listViewController.recreateCursor()
        }
    }

    protected open fun createContextMenus(id: Long): MutableList<MenuItemInfo> {
        val menus: MutableList<MenuItemInfo> = LinkedList()
        menus.add(MenuItemInfo(MENU_VIEW, R.string.view))
        menus.add(MenuItemInfo(MENU_EDIT, R.string.edit))
        menus.add(MenuItemInfo(MENU_DELETE, R.string.delete))
        return menus
    }

    open fun onPopupItemSelected(itemId: Int, view: View, position: Int, id: Long): Boolean {
        when (itemId) {
            MENU_VIEW -> viewItem(view, position, id)
            MENU_EDIT -> editItem(view, position, id)
            MENU_DELETE -> deleteItem(view, position, id)
            else -> return false
        }
        return true
    }


    protected open fun onItemClick(view: View, position: Int, id: Long) {
        viewItem(view, position, id)
    }

    protected open fun onItemLongClick(view: View, position: Int, id: Long) {
        val popupMenu = PopupMenu(requireContext(), view)
        val menu = popupMenu.menu
        val menus: List<MenuItemInfo> = createContextMenus(id)
        menus.filter { menuItemInfo -> menuItemInfo.enabled }
            .forEachIndexed { i, m -> menu.add(0, m.menuId, i, m.titleId) }
        popupMenu.setOnMenuItemClickListener {
            onPopupItemSelected(it.itemId, view, position, id)
        }
        popupMenu.show()
    }

    protected open fun onButtonAddClicked() {}

    inner class ListViewController(fragment: Fragment) : AbstractListViewController(fragment) {
        override fun createCursorImpl(): Cursor {
            return createCursor()
        }

        override fun createListAdapterImpl(cursor: Cursor): ListAdapter? {
            return createListAdapter(cursor)
        }

        override fun createRecyclerAdapterImpl(cursor: Cursor): CursorAdapter<*, *, *>? {
            return createRecyclerAdapter(cursor)
        }

        override fun onCursorRecreatedImpl() {
            return onCursorRecreated()
        }

        override fun onAdapterRecreatedImpl() {
            return onAdapterRecreated()
        }
    }

    open fun integrityCheck() {}

    protected open fun onCursorRecreated() {}
    protected open fun onAdapterRecreated() {}
    protected open fun createListAdapter(cursor: Cursor): ListAdapter? = null
    protected open fun createRecyclerAdapter(cursor: Cursor): CursorAdapter<*, *, *>? = null
    protected abstract fun createCursor(): Cursor

    protected abstract fun deleteItem(view: View, position: Int, id: Long)
    protected abstract fun editItem(view: View, position: Int, id: Long)
    protected abstract fun viewItem(view: View, position: Int, id: Long)

    companion object {
        const val LAST_MENU_INDEX = MENU_ADD
    }
}
