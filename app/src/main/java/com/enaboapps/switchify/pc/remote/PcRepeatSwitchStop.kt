package com.enaboapps.switchify.pc.remote

import java.util.concurrent.atomic.AtomicReference

fun interface PcSwitchStopHandle {
    fun release()
}

interface PcRepeatSwitchStop {
    fun arm(onStop: () -> Unit): PcSwitchStopHandle

    fun isAvailable(): Boolean

    companion object {
        val None = object : PcRepeatSwitchStop {
            override fun arm(onStop: () -> Unit) = PcSwitchStopHandle {}
            override fun isAvailable() = false
        }
    }
}

class PcSwitchRepeatStopHook(
    private val availability: () -> Boolean = { false },
    private val onArmedChanged: (Boolean) -> Unit = {}
) : PcRepeatSwitchStop {
    private class Armed(val onStop: () -> Unit)

    private val armed = AtomicReference<Armed?>(null)

    override fun arm(onStop: () -> Unit): PcSwitchStopHandle {
        val entry = Armed(onStop)
        if (armed.getAndSet(entry) == null) onArmedChanged(true)
        return PcSwitchStopHandle { if (armed.compareAndSet(entry, null)) onArmedChanged(false) }
    }

    override fun isAvailable(): Boolean = availability()

    fun isArmed(): Boolean = armed.get() != null

    fun requestStop(): Boolean {
        val entry = armed.getAndSet(null) ?: return false
        onArmedChanged(false)
        entry.onStop()
        return true
    }
}

object PcSwitchRepeatStop {
    @Volatile
    var availability: () -> Boolean = { false }

    @Volatile
    var armedListener: (Boolean) -> Unit = {}

    val hook = PcSwitchRepeatStopHook({ availability() }, { armedListener(it) })

    fun requestStop(): Boolean = hook.requestStop()

    fun isActive(): Boolean = hook.isArmed()
}
