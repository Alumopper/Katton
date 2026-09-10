import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import net.minecraft.client.Minecraft
import top.katton.api.*

@ClientScriptEntrypoint(ClientPhase.READY)
fun audioPeerProbe() {
    val mc = Minecraft.getInstance()
    val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "Katton-AudioPeer").apply { isDaemon = true } }
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180)
    var connected = false
    var attempted = false
    timer.scheduleAtFixedRate({ mc.execute {
        if (!attempted) {
            attempted = true
            val address = "127.0.0.1:25676"
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                net.minecraft.client.gui.screens.TitleScreen(), mc,
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                net.minecraft.client.multiplayer.ServerData("Katton Audio Probe", address,
                    net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null)
        }
        if (mc.level != null) connected = true
        if (connected && mc.level == null || System.nanoTime() > deadline) {
            println("AUDIO_PEER_PROBE_EXIT connected=$connected")
            timer.shutdown(); mc.stop()
        }
    } }, 250, 250, TimeUnit.MILLISECONDS)
}
