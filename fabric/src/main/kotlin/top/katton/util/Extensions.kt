package top.katton.util

import net.minecraft.util.TriState
import top.katton.bridger.EventResult

fun TriState.toFabric(): net.fabricmc.fabric.api.util.TriState {
    return when (this) {
        TriState.TRUE -> net.fabricmc.fabric.api.util.TriState.TRUE
        TriState.FALSE -> net.fabricmc.fabric.api.util.TriState.FALSE
        TriState.DEFAULT -> net.fabricmc.fabric.api.util.TriState.DEFAULT
    }
}

fun EventResult.toFabric(): net.fabricmc.fabric.api.util.EventResult {
    return when (this) {
        EventResult.PASS -> net.fabricmc.fabric.api.util.EventResult.PASS
        EventResult.ALLOW -> net.fabricmc.fabric.api.util.EventResult.ALLOW
        EventResult.DENY -> net.fabricmc.fabric.api.util.EventResult.DENY
    }
}