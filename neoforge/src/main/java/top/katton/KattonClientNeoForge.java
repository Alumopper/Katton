package top.katton;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import top.katton.network.ClientNetworkingNeoForge;

@EventBusSubscriber(
        modid = Katton.MOD_ID,
        value = { Dist.CLIENT }
)
public class KattonClientNeoForge {

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event){
        ClientNetworkingNeoForge.INSTANCE.reset();
    }

}
