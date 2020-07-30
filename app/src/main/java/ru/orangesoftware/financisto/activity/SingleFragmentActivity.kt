package ru.orangesoftware.financisto.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import ru.orangesoftware.financisto.R

private const val TAG_MAIN_FRAGMENT = "main_fragment"

abstract class SingleFragmentActivity : AppCompatActivity() {
    protected lateinit var mainFragment: Fragment

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_with_one_fragment)
        mainFragment = createFragment()
        setFragment(mainFragment)
    }

    private fun setFragment(mainFragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .add(R.id.fragment_container, mainFragment, TAG_MAIN_FRAGMENT)
            .commit()
    }

    abstract fun createFragment(): Fragment
}
