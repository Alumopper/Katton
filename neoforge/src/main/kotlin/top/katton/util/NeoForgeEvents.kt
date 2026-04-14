package top.katton.util

import net.neoforged.bus.api.Event
import net.neoforged.bus.api.ICancellableEvent
import net.neoforged.neoforge.common.NeoForge
import top.katton.KattonNeoForge

fun setCancel(kattonEvent: Cancellable, neoEvent: ICancellableEvent) {
    if (!neoEvent.isCanceled) neoEvent.isCanceled = kattonEvent.isCanceled()
}

fun <T: Event> registerReloadable(event: T, register: (T) -> Unit) {
    NeoForge.EVENT_BUS.register(event)
}