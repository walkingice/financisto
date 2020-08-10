package ru.orangesoftware.financisto2.accountlist.view

import android.database.Cursor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.AccountType
import ru.orangesoftware.financisto.model.CardIssuer
import ru.orangesoftware.financisto.model.ElectronicPaymentType
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.Utils
import ru.orangesoftware.financisto2.accountlist.view.AccountListAdapter.AccountPresenter
import ru.orangesoftware.financisto2.accountlist.view.AccountListAdapter.AccountViewHolder
import ru.orangesoftware.financisto2.common.view.CursorAdapter
import ru.orangesoftware.financisto2.common.view.Presenter
import ru.orangesoftware.orb.EntityManager
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs

class AccountListAdapter :
    CursorAdapter<Account, AccountViewHolder, AccountPresenter>(AccountPresenter()) {

    class AccountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var iconView: ImageView = itemView.findViewById(R.id.icon)
        var iconOverView: ImageView = itemView.findViewById(R.id.active_icon)
        var topView: TextView = itemView.findViewById(R.id.top)
        var centerView: TextView = itemView.findViewById(R.id.center)
        var bottomView: TextView = itemView.findViewById(R.id.bottom)
        var rightCenterView: TextView = itemView.findViewById(R.id.right_center)
        var rightView: TextView = itemView.findViewById(R.id.right)
        var progressBar: ProgressBar = itemView.findViewById(R.id.progress)

        init {
            rightView.visibility = View.GONE
            progressBar.visibility = View.GONE
        }
    }

    class AccountPresenter :
        Presenter<Account, AccountViewHolder> {
        var sdf = SimpleDateFormat("yyyy/MM/dd")

        var isShowAccountLastTransactionDate: Boolean = false

        override fun onCreateViewHolder(parent: ViewGroup): AccountViewHolder {
            if (u == null) {
                u = Utils(parent.context)
                isShowAccountLastTransactionDate =
                    MyPreferences.isShowAccountLastTransactionDate(parent.context);
            }

            val inflater = LayoutInflater.from(parent.context)
            val view = inflater.inflate(R.layout.list_item_account, parent, false)
            return AccountViewHolder(view)
        }

        override fun onBindViewHolder(v: AccountViewHolder, a: Account) {
            v.centerView.text = a.title

            val type = AccountType.valueOf(a.type)
            if (type.isCard && a.cardIssuer != null) {
                val cardIssuer = CardIssuer.valueOf(a.cardIssuer)
                v.iconView.setImageResource(cardIssuer.iconId)
            } else if (type.isElectronic && a.cardIssuer != null) {
                val paymentType = ElectronicPaymentType.valueOf(a.cardIssuer)
                v.iconView.setImageResource(paymentType.iconId)
            } else {
                v.iconView.setImageResource(type.iconId)
            }
            if (a.isActive) {
                v.iconView.drawable.mutate().alpha = 0xFF
                v.iconOverView.visibility = View.INVISIBLE
            } else {
                v.iconView.drawable.mutate().alpha = 0x77
                v.iconOverView.visibility = View.VISIBLE
            }

            val sb = StringBuilder()
            if (!a.issuer.isNullOrEmpty()) {
                sb.append(a.issuer)
            }
            if (!a.number.isNullOrEmpty()) {
                sb.append(" #").append(a.number)
            }
            if (sb.isEmpty()) {
                sb.append(v.itemView.context.getString(type.titleId))
            }

            v.topView.text = sb.toString()

            var date = a.creationDate
            if (isShowAccountLastTransactionDate && a.lastTransactionDate > 0) {
                date = a.lastTransactionDate
            }
            v.bottomView.text = sdf.format(Date(date))

            val amount = a.totalAmount
            if (type == AccountType.CREDIT_CARD && a.limitAmount != 0L) {
                val limitAmount = abs(a.limitAmount)
                val balance = limitAmount + amount
                val balancePercentage = 10000 * balance / limitAmount
                u?.setAmountText(v.rightView, a.currency, amount, false)
                u?.setAmountText(v.rightCenterView, a.currency, balance, false)
                v.rightView.visibility = View.VISIBLE
                v.progressBar.max = 10000
                v.progressBar.progress = balancePercentage.toInt()
                v.progressBar.visibility = View.VISIBLE
            } else {
                u?.setAmountText(v.rightCenterView, a.currency, amount, false)
                v.rightView.visibility = View.GONE
                v.progressBar.visibility = View.GONE
            }
        }

        companion object {
            var u: Utils? = null
        }
    }

    override fun getItem(cursor: Cursor, position: Int): Account {
        cursor.moveToPosition(position)
        return EntityManager.loadFromCursor(cursor, Account::class.java)
    }
}
