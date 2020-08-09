package ru.orangesoftware.financisto2.common

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * A helper delegation to create instance on onCreate, and destroy on onDestroy
 */
abstract class InstanceCreatingDelegation<InstanceType>(
    lifecycleOwner: LifecycleOwner
) : ReadOnlyProperty<LifecycleOwner, InstanceType>,
    LifecycleObserver {

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    private var instance: InstanceType? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        instance = createInstance()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        onDestroyInstance(instance!!)
        instance = null
    }

    override operator fun getValue(
        thisRef: LifecycleOwner, property: KProperty<*>
    ): InstanceType = instance!!

    open protected fun onDestroyInstance(instance: InstanceType) {
    }

    abstract fun createInstance(): InstanceType
}
