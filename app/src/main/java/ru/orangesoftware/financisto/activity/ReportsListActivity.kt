/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 * Denis Solonenko - initial API and implementation
 * Abdsandryk Souza - implementing 2D chart reports
 */
package ru.orangesoftware.financisto.activity

import android.app.ListActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import ru.orangesoftware.financisto.R.layout
import ru.orangesoftware.financisto.adapter.SummaryEntityListAdapter
import ru.orangesoftware.financisto.db.MyEntityManager
import ru.orangesoftware.financisto.graph.Report2DChart
import ru.orangesoftware.financisto.report.Report
import ru.orangesoftware.financisto.report.ReportType
import ru.orangesoftware.financisto.report.ReportType.BY_ACCOUNT_BY_PERIOD
import ru.orangesoftware.financisto.report.ReportType.BY_CATEGORY
import ru.orangesoftware.financisto.report.ReportType.BY_CATEGORY_BY_PERIOD
import ru.orangesoftware.financisto.report.ReportType.BY_LOCATION
import ru.orangesoftware.financisto.report.ReportType.BY_LOCATION_BY_PERIOD
import ru.orangesoftware.financisto.report.ReportType.BY_PAYEE
import ru.orangesoftware.financisto.report.ReportType.BY_PAYEE_BY_PERIOD
import ru.orangesoftware.financisto.report.ReportType.BY_PERIOD
import ru.orangesoftware.financisto.report.ReportType.BY_PROJECT
import ru.orangesoftware.financisto.report.ReportType.BY_PROJECT_BY_PERIOD
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import java.util.ArrayList

class ReportsListActivity : ListActivity() {
    private lateinit var reports: Array<ReportType>
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reports = reportsList
        setContentView(layout.reports_list)
        listAdapter = SummaryEntityListAdapter(this, reports)
    }

    override fun onPause() {
        super.onPause()
        PinProtection.lock(this)
    }

    override fun onResume() {
        super.onResume()
        PinProtection.unlock(this)
    }

    override fun onListItemClick(
        l: ListView,
        v: View,
        position: Int,
        id: Long
    ) {
        if (reports[position].isConventionalBarReport) {
            // Conventional Bars reports
            val intent = Intent(this, ReportActivity::class.java)
            intent.putExtra(
                EXTRA_REPORT_TYPE,
                reports[position].name
            )
            startActivity(intent)
        } else {
            // 2D Chart reports
            val intent = Intent(this, Report2DChartActivity::class.java)
            intent.putExtra(Report2DChart.REPORT_TYPE, reports[position].name)
            startActivity(intent)
        }
    }

    private val reportsList: Array<ReportType>
        private get() {
            val reports = ArrayList<ReportType>()
            reports.add(BY_PERIOD)
            reports.add(BY_CATEGORY)
            if (MyPreferences.isShowPayee(baseContext)) {
                reports.add(BY_PAYEE)
            }
            if (MyPreferences.isShowLocation(baseContext)) {
                reports.add(BY_LOCATION)
            }
            if (MyPreferences.isShowProject(baseContext)) {
                reports.add(BY_PROJECT)
            }
            reports.add(BY_ACCOUNT_BY_PERIOD)
            reports.add(BY_CATEGORY_BY_PERIOD)
            if (MyPreferences.isShowPayee(baseContext)) {
                reports.add(BY_PAYEE_BY_PERIOD)
            }
            if (MyPreferences.isShowLocation(baseContext)) {
                reports.add(BY_LOCATION_BY_PERIOD)
            }
            if (MyPreferences.isShowProject(baseContext)) {
                reports.add(BY_PROJECT_BY_PERIOD)
            }
            return reports.toTypedArray()
        }

    companion object {
        const val EXTRA_REPORT_TYPE = "reportType"

        @JvmStatic
        fun createReport(
            context: Context?,
            em: MyEntityManager,
            extras: Bundle
        ): Report {
            val reportTypeName =
                extras.getString(EXTRA_REPORT_TYPE)
            val reportType = ReportType.valueOf(reportTypeName)
            val c = em.homeCurrency
            return reportType.createReport(context, c)
        }
    }
}
