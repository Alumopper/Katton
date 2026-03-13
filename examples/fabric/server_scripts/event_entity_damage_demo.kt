import top.katton.api.dpcaller.tell
import top.katton.api.event.AllowDamageArg
import top.katton.api.event.ServerLivingEntityEvent

fun eventEntityDamageDemo() {
    ServerLivingEntityEvent.onAllowDamage += allow@ fun(arg: AllowDamageArg): Boolean {
        val level = arg.entity.level()
        if (level.isClientSide) return true

        val sourceEntity = arg.source.entity
        val attacker = sourceEntity as? net.minecraft.server.level.ServerPlayer ?: return true

        if (arg.amount > 8.0f) {
            tell(attacker, "[event-demo] 你本次伤害 ${arg.amount} 被脚本拦截（阈值 > 8）")
            return false
        }

        return true
    }
}

@Suppress("unused") private val eventEntityDamageDemo = eventEntityDamageDemo()

