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
package ru.orangesoftware.financisto2.activity

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.widget.TabHost
import android.widget.TabHost.OnTabChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.R.drawable
import ru.orangesoftware.financisto2.accountlist.AccountListFragment
import ru.orangesoftware.financisto.activity.BudgetListActivity
import ru.orangesoftware.financisto.activity.MenuListActivity_
import ru.orangesoftware.financisto.activity.ReportsListActivity
import ru.orangesoftware.financisto2.blotter.BlotterFragment
import ru.orangesoftware.financisto.bus.GreenRobotBus
import ru.orangesoftware.financisto.bus.GreenRobotBus_
import ru.orangesoftware.financisto.bus.RefreshCurrentTab
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper
import ru.orangesoftware.financisto.dialog.WebViewDialog
import ru.orangesoftware.financisto.utils.CurrencyCache
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection

class MainActivity : AppCompatActivity(), OnTabChangeListener {
    private var greenRobotBus: GreenRobotBus? = null

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)
        greenRobotBus = GreenRobotBus_.getInstance_(this)

        val tabLayout = findViewById<TabLayout>(R.id.tabs)
        val viewPager = findViewById<ViewPager2>(R.id.fragment_container)
        val adapter =
            FragmentStateAdapterImpl(
                this
            )
        viewPager.adapter = adapter
        TabLayoutMediator(tabLayout, viewPager, adapter.createStrategy()).attach()
        initialLoad()
//        val tabHost = tabHost
        // setupAccountsTab(tabHost)
        //setupBlotterTab(tabHost)
        //setupBudgetsTab(tabHost)
        //setupReportsTab(tabHost)
        //setupMenuTab(tabHost)
        val screen = MyPreferences.getStartupScreen(this)
        if (screen == MyPreferences.StartupScreen.BLOTTER) {
            viewPager.currentItem = FragmentStateAdapterImpl.Tabs.BLOTTER.ordinal
        }
//        tabHost.setCurrentTabByTag(screen.tag)
//        tabHost.setOnTabChangedListener(this)
    }

    @Subscribe(threadMode = MAIN)
    fun onRefreshCurrentTab(e: RefreshCurrentTab?) {
//        refreshCurrentTab()
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
                    getString(R.string.no_category)
                )
                updateFieldInTable(
                    x,
                    DatabaseHelper.CATEGORY_TABLE,
                    -1,
                    "title",
                    getString(R.string.split)
                )
                updateFieldInTable(
                    x,
                    DatabaseHelper.PROJECT_TABLE,
                    0,
                    "title",
                    getString(R.string.no_project)
                )
                updateFieldInTable(
                    x,
                    DatabaseHelper.LOCATIONS_TABLE,
                    0,
                    "name",
                    getString(R.string.current_location)
                )
                updateFieldInTable(
                    x,
                    DatabaseHelper.LOCATIONS_TABLE,
                    0,
                    "title",
                    getString(R.string.current_location)
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
//        val currentActivity = localActivityManager.currentActivity
//        if (currentActivity is RefreshSupportedActivity) {
//            val activity = currentActivity as RefreshSupportedActivity
//            activity.recreateCursor()
//            activity.integrityCheck()
//        }
    }

    private fun setupBudgetsTab(tabHost: TabHost) {
        tabHost.addTab(
            tabHost.newTabSpec("budgets")
                .setIndicator(
                    getString(R.string.budgets),
                    resources.getDrawable(drawable.ic_tab_budgets)
                )
                .setContent(Intent(this, BudgetListActivity::class.java))
        )
    }

    private fun setupReportsTab(tabHost: TabHost) {
        tabHost.addTab(
            tabHost.newTabSpec("reports")
                .setIndicator(
                    getString(R.string.reports),
                    resources.getDrawable(drawable.ic_tab_reports)
                )
                .setContent(Intent(this, ReportsListActivity::class.java))
        )
    }

    private fun setupMenuTab(tabHost: TabHost) {
        tabHost.addTab(
            tabHost.newTabSpec("menu")
                .setIndicator(
                    getString(R.string.menu),
                    resources.getDrawable(drawable.ic_tab_menu)
                )
                .setContent(Intent(this, MenuListActivity_::class.java))
        )
    }

    class FragmentStateAdapterImpl(val activity: FragmentActivity) :
        FragmentStateAdapter(activity) {
        enum class Tabs {
            ACCOUNT_LIST,
            BLOTTER
        }

        override fun getItemCount(): Int {
            return Tabs.values().size
        }

        override fun createFragment(position: Int): Fragment = when (position) {
            Tabs.ACCOUNT_LIST.ordinal -> AccountListFragment()
            Tabs.BLOTTER.ordinal -> BlotterFragment.newInstance(saveFilter = true)
            else -> TODO("Default fragment not yet implemented")
        }

        fun createStrategy() = TabLayoutMediator.TabConfigurationStrategy { tab, position ->
            tab.text = when (position) {
                Tabs.ACCOUNT_LIST.ordinal -> activity.getString(R.string.accounts)
                Tabs.BLOTTER.ordinal -> activity.getString(R.string.blotter)
                else -> "Unknown"
            }
        }
    }

}
