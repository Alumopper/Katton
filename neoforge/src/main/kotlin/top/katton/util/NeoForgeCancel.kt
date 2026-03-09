package top.katton.util

import net.neoforged.bus.api.ICancellableEvent

fun setCancel(kattonEvent: Cancellable, neoEvent: ICancellableEvent) {
    if (!neoEvent.isCanceled) neoEvent.isCanceled = kattonEvent.isCanceled()
}