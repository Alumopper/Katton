package top.katton

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent

@Mod(Katton.MOD_ID)
class KattonNeoForge(modEventBus: IEventBus, modContainer: ModContainer) {

    init {
        Katton.mainInitialize()
        
        modEventBus.addListener(::commonSetup)
    }

    private fun commonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
        }
    }
}
