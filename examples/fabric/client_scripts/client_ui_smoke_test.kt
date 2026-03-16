import net.minecraft.client.gui.components.toasts.SystemToast
import top.katton.api.clearClientOverlay
import top.katton.api.clientActionBar
import top.katton.api.clientAddSystemToast
import top.katton.api.clientNowPlaying
import top.katton.api.clientOverlay
import top.katton.api.clientSubtitle
import top.katton.api.clientTell
import top.katton.api.clientTitle
import top.katton.api.clientTitleTimes
import top.katton.api.clientToast
import top.katton.api.isClientInMenu
import top.katton.api.isInClientWorld
import top.katton.api.once
import top.katton.api.playClientSound
import top.katton.api.runOnClient

fun clientUiSmokeTestMain(){
    runOnClient {
        clientTell("[Katton] Client UI smoke test start")
        clientTell("inWorld=${isInClientWorld()}, inMenu=${isClientInMenu()}")

        clientTitleTimes(fadeInTicks = 8, stayTicks = 30, fadeOutTicks = 10)
        clientTitle("Katton Client API")
        clientSubtitle("UI smoke test")

        clientOverlay("Overlay message from script", tinted = true)
        clientActionBar("Action bar ping")
        clientNowPlaying("Now Playing style hint")
        clientAddSystemToast(SystemToast.SystemToastId.NARRATOR_TOGGLE, "Toast fallback test", "If no native toast path, this still prints")

        playClientSound("minecraft:entity.experience_orb.pickup", volume = 0.8f, pitch = 1.0f)

        // Clear overlay after a short delay equivalent handled by user re-running script.
        clearClientOverlay()
    }
}

val clientUiSmokeTestEntryPoint = clientUiSmokeTestMain()