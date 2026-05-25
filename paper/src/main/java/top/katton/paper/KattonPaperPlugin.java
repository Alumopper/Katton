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
import top.katton.api.event.managed.PaperManagedEvents;
import top.katton.command.ScriptCommand;
import top.katton.engine.ScriptEngine;
import top.katton.engine.ScriptReloadManager;
import top.katton.pack.ScriptPackManager;
import top.katton.registry.KattonRegistry;

import java.io.File;
import java.util.List;

import static io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.*;

/**
 * Paper plugin entrypoint for Katton.
 */
public class KattonPaperPlugin extends JavaPlugin implements Listener {

    private static KattonPaperPlugin instance;

    /**
     * Creates the Paper plugin entrypoint.
     */
    public KattonPaperPlugin() {
    }

    /**
     * Gets the singleton instance of the plugin.
     * @return the plugin instance
     */
    public static KattonPaperPlugin getInstance() {
        return instance;
    }

    @Override
    public void onLoad() {
        getLogger().info("Katton Paper loading...");
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("Katton Paper enabling...");

        // Register the plugin jar as a script compilation classpath entry.
        // On Paper, the plugin classloader may not expose jar URLs via URLClassLoader,
        // and protection domain code sources may not be set. This ensures the shadow jar
        // (containing Kotlin stdlib, Katton API, and scripting runtime) is always available.
        File jarFile = getFile();
        if (jarFile == null || !jarFile.isFile()) {
            try {
                var codeSource = getClass().getProtectionDomain().getCodeSource();
                if (codeSource != null && codeSource.getLocation() != null) {
                    jarFile = new File(codeSource.getLocation().toURI());
                }
            } catch (Exception ignored) {
            }
        }
        if (jarFile == null || !jarFile.isFile()) {
            try {
                var classUrl = getClass().getClassLoader()
                    .getResource("top/katton/paper/KattonPaperPlugin.class");
                if (classUrl != null && "jar".equals(classUrl.getProtocol())) {
                    var path = classUrl.getPath();
                    var bang = path.indexOf('!');
                    if (bang >= 0) {
                        path = path.substring(0, bang);
                    }
                    if (path.startsWith("file:")) {
                        jarFile = new File(path.substring(5));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (jarFile != null && jarFile.isFile()) {
            ScriptEngine.addHostClasspathJar(jarFile);
            getLogger().info("Registered plugin jar for script compilation: " + jarFile);
        } else {
            getLogger().warning("Could not locate plugin jar for script compilation classpath");
        }

        Katton.setGameDirectory(getDataFolder().getParentFile().toPath());
        Katton.paperInitialize();

        //command registration
        getLifecycleManager().registerEventHandler(
            COMMANDS,
            event -> event.registrar().register(
                "katton",
                "Katton script management",
                List.of("kts"),
                new KattonPaperCommand()
            )
        );

        // Register event listeners for server lifecycle
        getServer().getPluginManager().registerEvents(this, this);

        // Initialize managed event listener system (before event bridges)
        PaperManagedEvents.initialize(this);

        // Initialize Paper event bridges
        initEventBridges();

        getLogger().info("Katton Paper enabled. Use /katton reload to reload scripts.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Katton Paper disabling...");
        final MinecraftServer server = Katton.server != null ? Katton.server : MinecraftServer.getServer();
        ServerEvent.onDisable(server);
        ServerEvent.onServerStopped.invoke(new ServerArg(server));
        Katton.server = null;
        Katton.globalState = LoadState.SERVER_STOPPED;
        KattonRegistry.clearWorldRegistrations();
        Katton.clearWorldAndServerEvents();
        ScriptPackManager.INSTANCE.clearWorldDirectory();
    }

    /**
     * Fires when the server has fully loaded.
     * Equivalent to Fabric's SERVER_STARTED lifecycle event.
     *
     * @param event the server load event
     */
    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        Katton.server = MinecraftServer.getServer();
        Katton.globalState = LoadState.SERVER_STARTED;
        ScriptReloadManager.reloadScriptsAsync(MinecraftServer.getServer(), serverOk -> {
            if (serverOk) {
                getServer().getGlobalRegionScheduler().run(this, task ->
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
