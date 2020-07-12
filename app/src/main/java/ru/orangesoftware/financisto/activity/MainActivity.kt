/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 *
 * Contributors:
 * Denis Solonenko - initial API and implementation
 */
package ru.orangesoftware.financisto.activity

import android.app.TabActivity
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.widget.TabHost
import android.widget.TabHost.OnTabChangeListener
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import ru.orangesoftware.financisto.R.drawable
import ru.orangesoftware.financisto.R.string
import ru.orangesoftware.financisto.bus.GreenRobotBus
import ru.orangesoftware.financisto.bus.GreenRobotBus_
import ru.orangesoftware.financisto.bus.RefreshCurrentTab
import ru.orangesoftware.financisto.bus.SwitchToMenuTabEvent
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.dialog.WebViewDialog
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection

class MainActivity : TabActivity(), OnTabChangeListener {
    private var greenRobotBus: GreenRobotBus? = null
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        greenRobotBus = GreenRobotBus_.getInstance_(this)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        initialLoad()
        val tabHost = tabHost
        setupAccountsTab(tabHost)
        //setupBlotterTab(tabHost)
        //setupBudgetsTab(tabHost)
        //setupReportsTab(tabHost)
        //setupMenuTab(tabHost)
        val screen = MyPreferences.getStartupScreen(this)
        tabHost.setCurrentTabByTag(screen.tag)
        tabHost.setOnTabChangedListener(this)
    }

    @Subscribe(threadMode = MAIN)
    fun onSwitchToMenuTab(event: SwitchToMenuTabEvent?) {
        tabHost.setCurrentTabByTag("menu")
    }

    @Subscribe(threadMode = MAIN)
    fun onRefreshCurrentTab(e: RefreshCurrentTab?) {
        refreshCurrentTab()
    }

    override fun onResume() {
        super.onResume()
        greenRobotBus!!.register(this)
        PinProtection.unlock(this)
        if (PinProtection.isUnlocked()) {
            WebViewDialog.checkVersionAndShowWhatsNewIfNeeded(this)
        }
    }

    override fun onPause() {
        super.onPause()
        greenRobotBus!!.unregister(this)
        PinProtection.lock(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        PinProtection.immediateLock(this)
    }

    private fun initialLoad() {
        val t3: Long
        val t2: Long
        val t1: Long
        val t0 = System.currentTimeMillis()
        val db = DatabaseAdapter(this)
        db.open()
        try {
            val x = db.db()
            x.beginTransaction()
            t1 = System.currentTimeMillis()
            try {
                updateFieldInTable(
                    x,
                    DatabaseHelper.CATEGORY_TABLE,
                    0,
                    "title",
                    getString(string.no_category)
                )
                updateFieldInTable(
                    x,
                    DatabaseHelper.CATEGORY_TABLE,
                    -1,
                    "title",
                    getString(string.split)
                )
                updateFieldInTable(
                    x,
                    DatabaseHelper.PROJECT_TABLE,
                    0,
                    "title",
                    getString(string.no_project)
                )
                updateFieldInTable(
                    x,
                    DatabaseHelper.LOCATIONS_TABLE,
                    0,
                    "name",
                    getString(string.current_location)
                )
                updateFieldInTable(
                    x,
                    DatabaseHelper.LOCATIONS_TABLE,
                    0,
                    "title",
                    getString(string.current_location)
                )
                x.setTransactionSuccessful()
            } finally {
                x.endTransaction()
            }
            t2 = System.currentTimeMillis()
            if (MyPreferences.shouldUpdateHomeCurrency(this)) {
                db.setDefaultHomeCurrency()
            }
            CurrencyCache.initialize(db)
            t3 = System.currentTimeMillis()
            if (MyPreferences.shouldRebuildRunningBalance(this)) {
                db.rebuildRunningBalances()
            }
            if (MyPreferences.shouldUpdateAccountsLastTransactionDate(this)) {
                db.updateAccountsLastTransactionDate()
            }
        } finally {
            db.close()
        }
        val t4 = System.currentTimeMillis()
        Log.d(
            "Financisto",
            "Load time = " + (t4 - t0) + "ms = " + (t2 - t1) + "ms+" + (t3 - t2) + "ms+" + (t4 - t3) + "ms"
        )
    }

    private fun updateFieldInTable(
        db: SQLiteDatabase,
        table: String,
        id: Long,
        field: String,
        value: String
    ) {
        db.execSQL(
            "update $table set $field=? where _id=?",
            arrayOf<Any>(value, id)
        )
    }

    override fun onTabChanged(tabId: String) {
        Log.d("Financisto", "About to update tab $tabId")
        val t0 = System.currentTimeMillis()
        refreshCurrentTab()
        val t1 = System.currentTimeMillis()
        Log.d("Financisto", "Tab " + tabId + " updated in " + (t1 - t0) + "ms")
    }

    fun refreshCurrentTab() {
        val currentActivity = localActivityManager.currentActivity
        if (currentActivity is RefreshSupportedActivity) {
            val activity = currentActivity as RefreshSupportedActivity
            activity.recreateCursor()
            activity.integrityCheck()
        }
    }

    private fun setupAccountsTab(tabHost: TabHost) {
        tabHost.addTab(
            tabHost.newTabSpec("accounts")
                .setIndicator(
                    getString(string.accounts),
                    resources.getDrawable(drawable.ic_tab_accounts)
                )
                .setContent(Intent(this, AccountListActivity::class.java))
        )
    }

    private fun setupBlotterTab(tabHost: TabHost) {
        val intent = Intent(this, BlotterActivity::class.java)
        intent.putExtra(BlotterActivity.SAVE_FILTER, true)
        intent.putExtra(BlotterActivity.EXTRA_FILTER_ACCOUNTS, true)
        tabHost.addTab(
            tabHost.newTabSpec("blotter")
                .setIndicator(
                    getString(string.blotter),
                    resources.getDrawable(drawable.ic_tab_blotter)
                )
                .setContent(intent)
        )
    }

    private fun setupBudgetsTab(tabHost: TabHost) {
        tabHost.addTab(
            tabHost.newTabSpec("budgets")
                .setIndicator(
                    getString(string.budgets),
                    resources.getDrawable(drawable.ic_tab_budgets)
                )
                .setContent(Intent(this, BudgetListActivity::class.java))
        )
    }

    private fun setupReportsTab(tabHost: TabHost) {
        tabHost.addTab(
            tabHost.newTabSpec("reports")
                .setIndicator(
                    getString(string.reports),
                    resources.getDrawable(drawable.ic_tab_reports)
                )
                .setContent(Intent(this, ReportsListActivity::class.java))
        )
    }

    private fun setupMenuTab(tabHost: TabHost) {
        tabHost.addTab(
            tabHost.newTabSpec("menu")
                .setIndicator(
                    getString(string.menu),
                    resources.getDrawable(drawable.ic_tab_menu)
                )
                .setContent(Intent(this, MenuListActivity_::class.java))
        )
    }
}
