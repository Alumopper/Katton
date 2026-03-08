package top.katton;

import net.fabricmc.api.ModInitializer;
import top.katton.api.dpcaller.EntityEvent;
import top.katton.api.event.*;

import static top.katton.Katton.mainInitialize;

public class KattonFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // Fabric 平台启动点：调用 common 的初始化适配器
        mainInitialize();
        eventInitialize();
        // 在实体加载时，如果该实体有注册的 tick 事件，则在世界 tick 时调用
        ServerEntityEvent.INSTANCE.getOnAfterEntityLoad().plusAssign(entity ->
                EntityEvent.INSTANCE.getOnTickHandlers().get(entity.getEntity())
                        .getHandler().invoke(entity.getEntity(), entity.getWorld()));
    }

    private void eventInitialize() {
        ChunkAndBlockEvent.INSTANCE.initialize();
        ItemComponentEvent.INSTANCE.initialize();
        ItemEvent.INSTANCE.initialize();
        LivingBehaviorEvent.INSTANCE.initialize();
        LootTableEvent.INSTANCE.initialize();
        PlayerEvent.INSTANCE.initialize();
        ServerEntityCombatEvent.INSTANCE.initialize();
        ServerEntityEvent.INSTANCE.initialize();
        ServerEvent.INSTANCE.initialize();
        ServerLivingEntityEvent.INSTANCE.initialize();
        ServerMessageEvent.INSTANCE.initialize();
        ServerMobEffectEvent.INSTANCE.initialize();
        ServerPlayerEvent.INSTANCE.initialize();
    }
}
