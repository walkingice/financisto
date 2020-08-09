package ru.orangesoftware.financisto2.common

import androidx.fragment.app.Fragment
import ru.orangesoftware.financisto.db.DatabaseAdapter

class DbAdapterInstanceDelegation(
    val fragment: Fragment
) : InstanceCreatingDelegation<DatabaseAdapter>(fragment) {

    override fun createInstance(): DatabaseAdapter {
        return DatabaseAdapter(fragment.requireContext()).also { it.open() }
    }
}
