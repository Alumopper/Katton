package test

import net.minecraft.server.level.ServerPlayer
import top.katton.api.dpcaller.tell

fun say2(player: ServerPlayer) {
    sayMeow2(player)
}

fun sayMeow(player: ServerPlayer) {
    tell(player, "meow~" as Any)
}
