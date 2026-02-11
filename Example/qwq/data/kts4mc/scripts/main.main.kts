@file:DependsOn(
"G:\\AST\\kts4mc-template-1.21.11\\run\\saves\\新的世界\\datapacks\\lib\\26.1-snapshot-6-Fabric.jar",
"G:\\AST\\kts4mc-template-1.21.11\\build\\libs\\katton-1.0.0.jar"
)

import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.level.Level
import top.katton.api.KattonEvents
import top.katton.api.getEntityNbt

KattonEvents.ServerEntity.onEntityLoad += { arg -> run {
    if(arg.entity !is Arrow) return@run
    //if a player shoots an arrow, check the bow's data
    if((arg.entity as Projectile).owner is ServerPlayer) {
        onArrowShot((arg.entity as Projectile).owner as ServerPlayer, arg.entity as Arrow)
    }
} }

KattonEvents.ServerTick.onStartServerTick += { _ ->
    processTNTArrow()
}

val tntArrow = HashSet<Arrow>()

fun onArrowShot(player: ServerPlayer, arrow: Arrow) {
    val customData = player.mainHandItem.components.get(DataComponents.CUSTOM_DATA) ?: return
    if(!customData.copyTag().getBooleanOr("tnt", false)) return
    //this arrow is shot by a tnt bow, make it explode
    tntArrow.add(arrow)
}

fun processTNTArrow() {
    val iterator = tntArrow.iterator()
    while(iterator.hasNext()){
        val arrow = iterator.next()
        if(getEntityNbt(arrow).getBooleanOr("inGround", false)){
            arrow.level().explode(
                arrow,
                arrow.damageSources().explosion(arrow, arrow.owner),
                null,
                arrow.position(),
                16.0f,
                false,
                Level.ExplosionInteraction.TNT
            )
            iterator.remove()
        }
    }
}