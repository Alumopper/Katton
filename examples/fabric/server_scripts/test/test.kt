package test

import net.minecraft.server.level.ServerPlayer
import top.katton.api.dpcaller.tell

fun say(player: ServerPlayer) {
    sayMeow(player)
}

fun sayMeow2(player: ServerPlayer) {
    tell(player, "meow~" as Any)
}
