package ru.orangesoftware.financisto2.total

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.blotter.AccountTotalCalculationTask
import ru.orangesoftware.financisto.blotter.BlotterFilter
import ru.orangesoftware.financisto.blotter.BlotterTotalCalculationTask
import ru.orangesoftware.financisto.blotter.TotalCalculationTask
import ru.orangesoftware.financisto.filter.WhereFilter
import ru.orangesoftware.financisto.model.Total

class BlotterTotalsDetailsActivity :
    AbstractTotalsDetailsActivity(R.string.blotter_total_in_currency) {

    @Volatile
    private var totalCalculationTask: TotalCalculationTask? = null
    override fun internalOnCreate() {
        intent?.let {
            val blotterFilter = WhereFilter.fromIntent(it)
            cleanupFilter(blotterFilter)
            totalCalculationTask = createTotalCalculationTask(blotterFilter)
        }
    }

    private fun cleanupFilter(blotterFilter: WhereFilter) {
        blotterFilter.remove(BlotterFilter.BUDGET_ID)
    }

    private fun createTotalCalculationTask(blotterFilter: WhereFilter): TotalCalculationTask {
        val filter = WhereFilter.copyOf(blotterFilter)
        return if (filter.accountId > 0) {
            shouldShowHomeCurrencyTotal = false
            AccountTotalCalculationTask(this, db, filter, null)
        } else {
            BlotterTotalCalculationTask(this, db, filter, null)
        }
    }

    override fun getTotalInHomeCurrency(): Total {
        return totalCalculationTask!!.totalInHomeCurrency
    }

    override fun getTotals(): Array<Total> {
        return totalCalculationTask!!.totals
    }
}
