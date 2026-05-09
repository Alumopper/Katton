# Script Loading Lifecycle

This document traces the full execution path from mod initialization through client script execution, covering both singleplayer and multiplayer scenarios for Fabric and NeoForge.

## 1. Mod Initialization (Common Entry Point)

Both loaders call `Katton.mainInitialize()` during mod construction:

```kt
KattonRegistry.INSTANCE.initialize()          // init 10 sub-registries + custom DataComponentTypes
ScriptPackManager.INSTANCE.setGameDirectory(...)
ScriptEngine.setCacheDirectory(...)            // <gameDir>/.katton/compiled-script-cache/
ScriptPackManager.INSTANCE.refreshGlobalPacks() // scan <gameDir>/kattonpacks/
```

**Fabric** (`KattonFabric.onInitialize()`):
- `Networking.initialize()` — register common payload types
- `ServerNetworking.setPlaySender(ServerPlayNetworking::send)`
- Register server lifecycle events (command, started, stopped)

**NeoForge** (`KattonNeoForge` constructor):
- Register platform hooks (entity attributes, dynamic registry, spawn placements)
- `modEventBus.addListener(ServerNetworkingNeoForge.INSTANCE::onRegisterPayloadHandlers)` — mod bus payload registration
- `ServerNetworking.setPlaySender(PacketDistributor::sendToPlayer)`
- Register game bus event bridges (11 event classes)
- Register server lifecycle events

## 2. Client Initialization

### Fabric

```java
// KattonClientFabric.onInitializeClient()
Networking.initialize();                              // payload type registration
ClientNetworkingFabric.INSTANCE.initialize();           // client packet receivers

ClientPlayConnectionEvents.JOIN.register((_, _, client) -> {
    if (!hasJoinedSinceDisconnect && client.isSingleplayer()) {
        Katton.reloadClientScriptsAsync();             // ← singleplayer trigger
    }
});

ClientPlayConnectionEvents.DISCONNECT.register((_, _) -> {
    Katton.clearWorldAndServerEvents();
    ServerPackCacheManager.INSTANCE.reset();
});
```

### NeoForge

```java
// KattonClientNeoForge (@EventBusSubscriber)
@SubscribeEvent
static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
    Class.forName("top.katton.platform.NeoForgeEntityRendererHooks"); // force init
    NeoForge.EVENT_BUS.addListener(KattonClientNeoForge::onLoggingIn);
    NeoForge.EVENT_BUS.addListener(KattonClientNeoForge::onDisconnect);
    NeoForge.EVENT_BUS.addListener(KattonClientNeoForge::onClientTick);
}

static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
    if (Minecraft.getInstance().isSingleplayer()) {
        Katton.reloadClientScriptsAsync();              // ← singleplayer trigger
    }
}

static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
    Katton.clearWorldAndServerEvents();
    ServerPackCacheManager.INSTANCE.reset();
}
```

## 3. Payload Registration

### Fabric

```kotlin
// Networking.kt — common payload type registration
PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.register(ScriptPackHashListPacket.TYPE, ...)
PayloadTypeRegistryImpl.CLIENTBOUND_CONFIGURATION.register(ScriptPackBundlePacket.TYPE, ...)
SERVERBOUND_CONFIGURATION.register(ScriptPackRequestPacket.TYPE, ...)
```

### NeoForge

```kotlin
// ServerNetworkingNeoForge — registered via modEventBus.addListener()
fun onRegisterPayloadHandlers(event: RegisterPayloadHandlersEvent) {
    val registrar = event.registrar("1")
    // Server → Client:
    registrar.configurationToClient(ScriptPackHashListPacket.TYPE, codec, clientHandler)
    registrar.configurationToClient(ScriptPackBundlePacket.TYPE, codec, clientHandler)
    // Client → Server:
    registrar.configurationToServer(ScriptPackRequestPacket.TYPE, codec, serverHandler)
}
```

## 4. Multiplayer Script Pack Sync

### 4.1 Server Sends Script Packs

**Fabric** — `ServerConfigurationPacketListenerImpl.<init>` RETURN mixin:

```java
// ServerConfigurationPacketListenerImplMixin (Fabric)
@Inject(method = "<init>", at = @At("RETURN"))
private void katton$onInit(...) {
    ServerNetworking.sendInitialScriptPackSync(THIS, ServerConfigurationNetworking::send);
}
```

**NeoForge** — `NetworkRegistry.initializeNeoForgeConnection()` RETURN mixin:

```java
// NetworkRegistryMixin (NeoForge)
@Inject(method = "initializeNeoForgeConnection", at = @At("RETURN"), remap = false)
private static void katton$afterNeoForgeNegotiation(ServerConfigurationPacketListener listener, ...) {
    var impl = (ServerConfigurationPacketListenerImpl) listener;
    ServerNetworking.sendInitialScriptPackSync(impl, ServerConfigurationPacketListenerImpl::send);
}
```

> **Why the difference**: NeoForge validates custom payloads against negotiated channels.
> `initializeNeoForgeConnection` is the point where NeoForge finishes channel negotiation,
> so payloads sent after this call pass `NetworkRegistry.checkPacket()`. Fabric's networking
> layer has no equivalent channel validation.

### 4.2 `sendInitialScriptPackSync` (Common)

```kotlin
// ServerNetworking.kt
fun sendInitialScriptPackSync(handler, sender) {
    val hashPacket = createScriptPackHashPacket()       // collect all pack hashes
    sender(handler, hashPacket)                          // send hash list
    if (hashPacket.entries.isEmpty()) return
    sender(handler, createScriptPackBundlePacket(...))   // send all pack files
}
```

The bundle is sent immediately (no C→S round-trip) because the client's common case is
that it needs all packs.

### 4.3 Client Receives Script Packs

Both platforms follow the same pattern via `ServerPackCacheManager`:

```
Netty thread:
  1. prepareMainThreadSync()         → create CountDownLatch(1)
  2. context.enqueueWork {           → dispatch to Render thread
       handleHashList(packet)        → store expected hashes
       handleBundle(packet)           → persist files to disk, verify hashes
       Katton.reloadClientScripts()  → compile & execute all scripts
     }
  3. awaitMainThreadSync()           → block Netty thread until latch released
```

Pack files are cached under `<gameDir>/serverpacks/<sha256(serverIp)>/<base64(syncId)>/`.

### 4.4 Client Executes Scripts Before Registry Sync

**Fabric** — `ClientRegistrySyncHandler.receivePacket` HEAD mixin:

```java
// ClientRegistrySyncHandlerMixin
@Inject(method = "receivePacket", at = @At("HEAD"))
private static void katton$onReceivePacketHead(...) {
    ServerPackCacheManager.INSTANCE.executePendingScriptsBeforeRegistryCheck();
}
```

**NeoForge** — `ClientConfigurationPacketListenerImpl.handleRegistryData` HEAD mixin:

```java
// ClientConfigurationPacketListenerImplMixin
@Inject(method = "handleRegistryData", at = @At("HEAD"), remap = false)
private static void katton$onHandleRegistryData(...) {
    ServerPackCacheManager.INSTANCE.executePendingScriptsBeforeRegistryCheck();
}
```

> `executePendingScriptsBeforeRegistryCheck()` is currently a **no-op** because
> `reloadClientScripts()` was already called synchronously inside `handleBundle`.
> The mixin exists as a safety net in case the reload path ever becomes async.

### 4.5 Fix Item Components After Registry Data Processing

After `RegistryDataCollector.collectGameRegistries()` runs `DataComponentInitializers.build()`,
custom `ITEM_NAME` and `ITEM_MODEL` are overwritten with defaults. The fix:

```java
// RegistryDataCollectorMixin (same file for both Fabric and NeoForge)
@Inject(method = "collectGameRegistries", at = @At("RETURN"))
private void katton$onCollectGameRegistriesReturn(...) {
    KattonRegistry.ITEMS.reapplyCustomItemComponents();
}
```

This calls `bindHolderComponents()` for every registered Katton item, restoring custom
data components.

## 5. `reloadClientScripts()` — The Core

### 5.1 Synchronous Reload

```java
public static boolean reloadClientScripts() {
    // 1. Clear state
    if (!preserveIntegratedServerState) {
        Event.clearHandlers();                            // clear script event handlers
        InjectionManager.beginReload();                    // reset ByteBuddy injections
    }
    KattonClientRenderApiKt.clearClientRenderers();        // clear render callbacks
    KattonRegistry.ENTITY_RENDERERS.INSTANCE.beginReload(); // reset entity renderers

    // 2. Refresh pack directories
    ScriptPackManager.INSTANCE.refreshGlobalPacks();
    ScriptPackManager.INSTANCE.refreshWorldPacks();

    // 3. Merge local + server-cache packs, compile, execute
    List<ScriptPack> mergedPacks = new ArrayList<>(ScriptPackManager.INSTANCE.collectExecutablePacks());
    mergedPacks.addAll(ServerPackCacheManager.INSTANCE.collectExecutablePacks());
    ScriptEngine.compileAndExecuteAll(mergedPacks, ScriptEnvironment.CLIENT);

    // During execution, @ClientScriptEntrypoint functions call:
    //   registerNativeItem()      → Item registered in BuiltInRegistries
    //   registerHudRenderer()     → render callbacks stored
    //   registerAnimatedEntityRenderer() → renderers stored via hooks
}
```

### 5.2 Asynchronous Wrapper

```java
public static boolean reloadClientScriptsAsync() {
    // Guard: no concurrent reloads
    CLIENT_RELOAD_EXECUTOR.execute(() -> {
        reloadClientScripts();         // calls above method
    });
}
```

Thread safety is provided by:
- `AtomicBoolean CLIENT_RELOAD_RUNNING` — prevents concurrent reloads
- `CompletableFuture<Void> clientReloadFuture` — allows callers to wait via `awaitClientReloadCompletion()`

## 6. Singleplayer vs Multiplayer Flow Comparison

```
SINGLEPLAYER:
  ClientInitialize → JOIN/LoggingIn → reloadClientScriptsAsync()
    → reloadClientScripts()
      → compile & execute scripts
      → items/blocks/etc. registered
    → (no RegistryDataCollector runs in singleplayer move to world)
    → World rendered ✅

MULTIPLAYER (Fabric):
  Server mixin <init> RETURN → sendInitialScriptPackSync → hash + bundle
    → Client receives → handleBundle → reloadClientScripts()
      → scripts execute, items registered
    → ClientRegistrySyncHandler.receivePacket
      → executePendingScriptsBeforeRegistryCheck() (no-op)
    → RegistryDataCollector.collectGameRegistries()
      → DataComponentInitializers.build() overwrites components ❌
    → RegistryDataCollectorMixin @RETURN
      → reapplyCustomItemComponents() restores components ✅
    → Client enters world

MULTIPLAYER (NeoForge):
  Connection established → client sends ModdedNetworkQueryPayload
    → Server initializeNeoForgeConnection() negotiates channels
    → NetworkRegistryMixin @RETURN → sendInitialScriptPackSync
    → Client receives → handleBundle → reloadClientScripts()
      → scripts execute, items registered
    → ClientConfigurationPacketListenerImpl.handleRegistryData
      → executePendingScriptsBeforeRegistryCheck() (no-op)
    → RegistryDataCollector.collectGameRegistries()
      → DataComponentInitializers.build() overwrites components ❌
    → RegistryDataCollectorMixin @RETURN
      → reapplyCustomItemComponents() restores components ✅
    → Client enters world
```

## 7. Key Architecture Differences

| Aspect | Fabric | NeoForge |
|---|---|---|
| **Singleplayer trigger** | `ClientPlayConnectionEvents.JOIN` | `ClientPlayerNetworkEvent.LoggingIn` |
| **Server send timing** | `ServerConfigurationPacketListenerImpl.<init>` RETURN | `NetworkRegistry.initializeNeoForgeConnection()` RETURN |
| **Send API** | `ServerConfigurationNetworking::send` | `ServerConfigurationPacketListenerImpl::send` |
| **Client execute hook** | `ClientRegistrySyncHandler.receivePacket` HEAD | `ClientConfigurationPacketListenerImpl.handleRegistryData` HEAD |
| **Payload registration** | `PayloadTypeRegistryImpl` (Fabric API) | `RegisterPayloadHandlersEvent` + `registrar.configurationToClient` |
| **Component overwrite fix** | `RegistryDataCollectorMixin` (vanilla class) | `RegistryDataCollectorMixin` (same vanilla class) |
| **Entity renderer context** | `EntityRenderers.createEntityRenderers()` HEAD mixin | `EntityRenderersEvent.RegisterRenderers` event listener |
| **Entity renderer dispatch** | `EntityRenderDispatcherMixin.getRenderer` @HEAD | `EntityRenderDispatcherMixin.getRenderer` @HEAD |

## 8. Timing Constraint (Critical)

The script packs must be received, compiled, and executed **before** the client's registry
sync validation runs, because scripts register items (via `registerNativeItem`) that need to
be in `BuiltInRegistries` before the client asks "does the server have items I don't know about?".

The solution has three parts:

1. **Server eagerly sends** hash + bundle during configuration (no round-trip waiting)
2. **Client processes** on render thread immediately, blocking Netty thread with `CountDownLatch`
3. **Component restoration** at `collectGameRegistries` RETURN fixes the
   `DataComponentInitializers.build()` overwrite

## 9. File Map

```
common/
├── src/main/java/top/katton/Katton.java
│   ├── mainInitialize()              — mod init
│   ├── reloadClientScripts()         — core reload logic
│   └── reloadClientScriptsAsync()    — async wrapper
├── src/main/kotlin/top/katton/
│   ├── network/ServerNetworking.kt   — sendInitialScriptPackSync
│   └── pack/ServerPackCacheManager.kt — client pack receive + cache

fabric/
├── src/main/java/top/katton/
│   ├── KattonClientFabric.java
│   └── mixin/
│       ├── ClientRegistrySyncHandlerMixin.java
│       ├── ServerConfigurationPacketListenerImplMixin.java
│       ├── RegistryDataCollectorMixin.java
│       └── EntityRenderDispatcherMixin.java
├── src/main/kotlin/top/katton/network/
│   ├── Networking.kt
│   └── ClientNetworkingFabric.kt

neoforge/
├── src/main/java/top/katton/
│   ├── KattonNeoForge.java
│   ├── KattonClientNeoForge.java
│   └── mixin/
│       ├── ClientConfigurationPacketListenerImplMixin.java
│       ├── ServerConfigurationPacketListenerImplMixin.java (marker)
│       ├── NetworkRegistryMixin.java
│       ├── RegistryDataCollectorMixin.java
│       └── EntityRenderDispatcherMixin.java
├── src/main/kotlin/top/katton/network/
│   └── ServerNetworkingNeoForge.kt
```
