package ru.orangesoftware.financisto2.blotter

import androidx.fragment.app.Fragment
import ru.orangesoftware.financisto2.activity.SingleFragmentActivity

open class BlotterActivity : SingleFragmentActivity() {
    override fun createFragment(): Fragment = BlotterFragment.newInstance()
}
