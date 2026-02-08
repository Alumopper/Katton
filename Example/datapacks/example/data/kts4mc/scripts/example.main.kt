//@file:DependsOn("../../versions/1.21.11/minecraft.jar")
//@file:DependsOn("../../mods/katton-1.0.0.jar")
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.arrow.Arrow
import net.minecraft.world.level.Level
import top.katton.api.KattonEvents
import top.katton.api.getEntityNbt
import top.katton.api.tell

fun main() {
    KattonEvents.ServerEntity.onEntityLoad += { entity: Entity, level: ServerLevel -> run {
        if(entity !is Arrow) return@run
        //if an arrow is shot by a player, check the bow's data
        val player = entity.owner
        if(player is ServerPlayer) {
            tell(player,"hello")
            onArrowShot(player, entity)
        }
    } }

    KattonEvents.ServerTick.onStartServerTick += { server: MinecraftServer ->
        server.playerList.players.forEach { player ->
            tell(player, "hello")
        }
        processTNTArrow()
    }
}
val mainScript = main()

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