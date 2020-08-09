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
package ru.orangesoftware.financisto2.activity

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ru.orangesoftware.financisto.activity.ActivityLayout
import ru.orangesoftware.financisto.activity.ActivityLayoutListener
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.MultiChoiceItem
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import ru.orangesoftware.financisto.view.NodeInflater

abstract class AbstractActivity : AppCompatActivity(), ActivityLayoutListener {
    protected lateinit var db: DatabaseAdapter
    protected lateinit var x: ActivityLayout
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layoutInflater =
            getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val nodeInflater = NodeInflater(layoutInflater)
        x = ActivityLayout(nodeInflater, this)
        db = DatabaseAdapter(this)
        db.open()
    }

    override fun onPause() {
        super.onPause()
        if (shouldLock()) {
            PinProtection.lock(this)
        }
    }

    override fun onResume() {
        super.onResume()
        if (shouldLock()) {
            PinProtection.unlock(this)
        }
    }

    protected open fun shouldLock(): Boolean {
        return true
    }

    override fun onClick(v: View) {
        val id = v.id
        onClick(v, id)
    }

    protected abstract fun onClick(v: View, id: Int)
    override fun onSelected(
        id: Int,
        items: List<MultiChoiceItem?>
    ) {
    }

    override fun onSelectedId(id: Int, selectedId: Long) {}
    override fun onSelectedPos(id: Int, selectedPos: Int) {}
    protected fun checkSelected(value: Any?, messageResId: Int): Boolean {
        if (value == null) {
            Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    protected fun checkSelectedId(value: Long, messageResId: Int): Boolean {
        if (value <= 0) {
            Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun onDestroy() {
        db!!.close()
        super.onDestroy()
    }

    companion object {
        fun setVisibility(v: View?, visibility: Int) {
            if (v == null) return
            v.visibility = visibility
            val o = v.tag
            if (o is View) {
                o.visibility = visibility
            }
        }
    }
}
