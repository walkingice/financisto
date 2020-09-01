package ru.orangesoftware.financisto2.total

import android.os.AsyncTask
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.model.Total
import ru.orangesoftware.financisto.rates.ExchangeRate
import ru.orangesoftware.financisto.utils.Utils
import ru.orangesoftware.financisto2.activity.AbstractActivity
import java.util.ArrayList

abstract class AbstractTotalsDetailsActivity protected constructor(private val titleNodeResId: Int) :
    AbstractActivity() {

    private lateinit var utils: Utils
    private lateinit var layout: LinearLayout
    private lateinit var calculatingNode: View

    protected var shouldShowHomeCurrencyTotal = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
        setContentView(R.layout.totals_details)
        utils = Utils(this)
        layout = findViewById(R.id.list)
        calculatingNode = x.addTitleNodeNoDivider(layout, R.string.calculating)
        findViewById<Button>(R.id.bOK).setOnClickListener { finish() }

        internalOnCreate()
        calculateTotals()
    }

    private fun calculateTotals() {
        val task =
            CalculateAccountsTotalsTask()
        task.execute()
    }

    override fun onClick(v: View, id: Int) {}
    private inner class CalculateAccountsTotalsTask :
        AsyncTask<Void?, Void?, TotalsInfo>() {
        override fun doInBackground(vararg params: Void?): TotalsInfo {
            prepareInBackground()
            val totals = getTotals()
            val totalInHomeCurrency = getTotalInHomeCurrency()
            val homeCurrency = totalInHomeCurrency.currency
            val rates = db.latestRates
            val result: MutableList<TotalInfo> = ArrayList()
            for (total in totals) {
                val rate = rates.getRate(total.currency, homeCurrency)
                val info = TotalInfo(total, rate)
                result.add(info)
            }
            result.sortWith(Comparator { thisTotalInfo, thatTotalInfo ->
                val thisName = thisTotalInfo.total.currency.name
                val thatName = thatTotalInfo.total.currency.name
                thisName.compareTo(thatName)
            })
            return TotalsInfo(result, totalInHomeCurrency)
        }

        override fun onPostExecute(totals: TotalsInfo) {
            calculatingNode!!.visibility = View.GONE
            for (total in totals.totals) {
                val title = getString(titleNodeResId, total.total.currency.name)
                addAmountNode(total.total, title)
            }
            if (shouldShowHomeCurrencyTotal) {
                addAmountNode(totals.totalInHomeCurrency, getString(R.string.home_currency_total))
            }
        }

        private fun addAmountNode(total: Total, title: String) {
            x.addTitleNodeNoDivider(layout, title)
            if (total.isError) {
                addAmountAndErrorNode(total)
            } else {
                addSingleAmountNode(total)
            }
        }

        private fun addAmountAndErrorNode(total: Total) {
            val data = x.addInfoNode(layout, -1, R.string.not_available, "")
            val dr = resources.getDrawable(R.drawable.total_error)
            dr.setBounds(0, 0, dr.intrinsicWidth, dr.intrinsicHeight)
            if (total.currency === Currency.EMPTY) {
                data.setText(R.string.currency_make_default_warning)
            } else {
                data.text = total.getError(this@AbstractTotalsDetailsActivity)
            }
            data.setError("Error!", dr)
        }

        private fun addSingleAmountNode(total: Total) {
            val label = x.addInfoNodeSingle(layout, -1, "")
            utils.setAmountText(label, total)
        }
    }

    protected open fun internalOnCreate() {}
    protected abstract fun getTotalInHomeCurrency(): Total
    protected abstract fun getTotals(): Array<Total>

    protected fun prepareInBackground() {}

    private class TotalInfo(val total: Total, val rate: ExchangeRate)

    private class TotalsInfo(
        val totals: List<TotalInfo>,
        val totalInHomeCurrency: Total
    )
}
