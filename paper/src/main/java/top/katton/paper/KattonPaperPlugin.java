package top.katton.paper;

import kotlin.Unit;
import net.minecraft.server.MinecraftServer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import top.katton.Katton;
import top.katton.LoadState;
import top.katton.api.event.*;
import top.katton.command.ScriptCommand;
import top.katton.engine.ScriptReloadManager;
import top.katton.pack.ScriptPackManager;
import top.katton.registry.KattonRegistry;

import static io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.*;

/**
 * Paper plugin entrypoint for Katton.
 * <p>
 * Paper is a server-only platform. This entrypoint:
 * <ul>
 *   <li>Calls {@link Katton#paperInitialize()} instead of {@link Katton#mainInitialize()}
 *       — no registry mutation to avoid inconsistency with vanilla clients.</li>
 *   <li>Does NOT initialize client features (renderers, GUI, client APIs).</li>
 *   <li>Wires Paper lifecycle events to Katton's script engine.</li>
 * </ul>
 */
public class KattonPaperPlugin extends JavaPlugin implements Listener {

    @Override
    public void onLoad() {
        getLogger().info("Katton Paper loading...");
    }

    @Override
    public void onEnable() {
        getLogger().info("Katton Paper enabling...");

        // 1. Set game directory (plugins/.. = server root)
        Katton.setGameDirectory(getDataFolder().getParentFile().toPath());
        // 2. Paper-specific initialization — no registry mutation
        Katton.paperInitialize();

        // 3. Register commands via Paper lifecycle manager
        getLifecycleManager().registerEventHandler(
            COMMANDS,
            event -> event.registrar().register(
                "katton",
                "Katton script management",
                java.util.List.of("kts"),
                new KattonPaperCommand()
            )
        );

        // 4. Register event listeners for server lifecycle
        getServer().getPluginManager().registerEvents(this, this);

        // 5. Initialize Paper event bridges
        initEventBridges();

        getLogger().info("Katton Paper enabled. Use /katton reload to reload scripts.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Katton Paper disabling...");
        final MinecraftServer server = Katton.server != null ? Katton.server : MinecraftServer.getServer();
        if (Katton.server != null) {
            ServerEvent.onServerStopping.invoke(new ServerArg(Katton.server));
        }
        if (server != null) {
            ServerEvent.onServerStopped.invoke(new ServerArg(server));
        }
        Katton.server = null;
        Katton.globalState = LoadState.SERVER_STOPPED;
        KattonRegistry.clearWorldRegistrations();
        Katton.clearWorldAndServerEvents();
        ScriptPackManager.INSTANCE.clearWorldDirectory();
    }

    /**
     * Fires when the server has fully loaded.
     * Equivalent to Fabric's SERVER_STARTED lifecycle event.
     */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        Katton.server = MinecraftServer.getServer();
        Katton.globalState = LoadState.SERVER_STARTED;
        ScriptReloadManager.reloadScriptsAsync(MinecraftServer.getServer(), serverOk -> {
            if (serverOk) {
                getServer().getScheduler().runTask(this, () ->
                    ScriptCommand.syncCommandTree(MinecraftServer.getServer())
                );
            }
            return Unit.INSTANCE;
        });
    }

    /**
     * Initializes all 14 Paper event bridge objects.
     * Mirrors Fabric's {@code eventInitialize()} pattern.
     */
    private void initEventBridges() {
        ServerEvent.initialize(this);
        PlayerEvent.initialize(this);
        ServerPlayerEvent.initialize(this);
        LivingBehaviorEvent.initialize(this);
        ServerLivingEntityEvent.initialize(this);
        ServerEntityEvent.initialize(this);
        ServerEntityCombatEvent.initialize(this);
        ServerMobEffectEvent.initialize(this);
        ServerMessageEvent.initialize(this);
        ItemEvent.initialize(this);
        ItemComponentEvent.initialize(this);
        LivingUseItemEvent.initialize(this);
        ChunkAndBlockEvent.initialize(this);
        LootTableEvent.initialize(this);
    }
}
