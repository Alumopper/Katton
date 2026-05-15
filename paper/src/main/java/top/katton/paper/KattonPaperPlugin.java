package top.katton.paper;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import top.katton.Katton;
import top.katton.LoadState;
import top.katton.command.ScriptCommand;
import top.katton.engine.ScriptReloadManager;
import top.katton.pack.ScriptPackManager;
import top.katton.registry.KattonRegistry;
import top.katton.util.Event;

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
            io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
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
        Katton.server = null;
        Katton.globalState = LoadState.SERVER_STOPPED;
        KattonRegistry.INSTANCE.clearWorldRegistrations();
        Katton.clearWorldAndServerEvents();
        ScriptPackManager.INSTANCE.clearWorldDirectory();
    }

    /**
     * Fires when the server has fully loaded.
     * Equivalent to Fabric's SERVER_STARTED lifecycle event.
     */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        Katton.server = getServer();
        Katton.globalState = LoadState.SERVER_STARTED;
        ScriptReloadManager.reloadScriptsAsync(getServer(), serverOk -> {
            if (serverOk) {
                getServer().getScheduler().runTask(this, () ->
                    ScriptCommand.syncCommandTree(getServer())
                );
            }
        });
    }

    /**
     * Initializes all 14 Paper event bridge objects.
     * Mirrors Fabric's {@code eventInitialize()} pattern.
     */
    private void initEventBridges() {
        top.katton.api.event.ServerEvent.INSTANCE.initialize(this);
        top.katton.api.event.PlayerEvent.INSTANCE.initialize(this);
        top.katton.api.event.ServerPlayerEvent.INSTANCE.initialize(this);
        top.katton.api.event.LivingBehaviorEvent.INSTANCE.initialize(this);
        top.katton.api.event.ServerLivingEntityEvent.INSTANCE.initialize(this);
        top.katton.api.event.ServerEntityEvent.INSTANCE.initialize(this);
        top.katton.api.event.ServerEntityCombatEvent.INSTANCE.initialize(this);
        top.katton.api.event.ServerMobEffectEvent.INSTANCE.initialize(this);
        top.katton.api.event.ServerMessageEvent.INSTANCE.initialize(this);
        top.katton.api.event.ItemEvent.INSTANCE.initialize(this);
        top.katton.api.event.ItemComponentEvent.INSTANCE.initialize(this);
        top.katton.api.event.LivingUseItemEvent.INSTANCE.initialize(this);
        top.katton.api.event.ChunkAndBlockEvent.INSTANCE.initialize(this);
        top.katton.api.event.LootTableEvent.INSTANCE.initialize(this);
    }
}
