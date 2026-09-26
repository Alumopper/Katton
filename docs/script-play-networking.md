# Fabric and NeoForge script play networking

Payload types and their codecs are registered for the lifetime of the process. Registering `SaveUnlockPayload` from a reloadable script leaves its codec tied to an old `PackClassLoader`; the next script generation creates a different class with the same name, and encoding fails with `ClassCastException`.

Use Katton's host-loaded `katton:script_payload` instead. Scripts exchange bytes under their own logical channel IDs; the fixed payload class and codec are registered once by Katton, while script handlers are automatically removed/replaced on reload. The following script API and imports are identical on Fabric and NeoForge:

```kotlin
import net.minecraft.resources.Identifier
import top.katton.api.ClientPhase
import top.katton.api.ClientScriptEntrypoint
import top.katton.api.ServerPhase
import top.katton.api.ServerScriptEntrypoint
import top.katton.network.ClientScriptPlayNetworking
import top.katton.network.ScriptPlayNetworking

private val channel = Identifier.parse("anki:save_unlock")

@ServerScriptEntrypoint(ServerPhase.READY)
fun registerServerPackets() {
    ScriptPlayNetworking.registerReceiver(channel) { player, bytes ->
        println("Received ${bytes.size} bytes")
        ScriptPlayNetworking.sendToClient(player, channel, "ok".encodeToByteArray())
    }
}

@ClientScriptEntrypoint(ClientPhase.JOINED)
fun registerClientPackets() {
    ClientScriptPlayNetworking.registerReceiver(channel) { bytes ->
        println("Server replied: ${bytes.decodeToString()}")
    }
}

fun sendSave(bytes: ByteArray): Boolean = ClientScriptPlayNetworking.sendToServer(channel, bytes)
```

Call `sendSave` from a client event or save hook, not during registration. Before changing server state, decode the bytes and validate the player, inventory slot, and item data on the server. Both send methods return `false` if the peer does not support the Katton channel. The payload limit is 16 KiB. Paper has no modded client and cannot use this API. Scripts that still register their own payload type must migrate to the Katton channel and restart the game to remove old process-wide registrations. Do not use command/SNBT transport as a replacement for binary packets.
