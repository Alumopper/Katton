package top.katton.engine;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;

/**
 * Startup agent entry point used when the runtime does not provide {@code jdk.attach}.
 *
 * <p>The same Fabric or NeoForge deployment jar can be passed to the JVM with
 * {@code -javaagent:/path/to/katton.jar}. This follows the standard Java agent startup
 * contract documented at
 * https://docs.oracle.com/en/java/javase/25/docs/api/java.instrument/java/lang/instrument/package-summary.html.</p>
 */
public final class KattonAgent {
    private static volatile Instrumentation instrumentation;

    private KattonAgent() {
    }

    /** Called by the JVM before the game main class is loaded. */
    public static void premain(String arguments, Instrumentation installedInstrumentation) {
        instrumentation = installedInstrumentation;
    }

    /** Supports tools that load the Katton jar as an agent into an already running JVM. */
    public static void agentmain(String arguments, Instrumentation installedInstrumentation) {
        instrumentation = installedInstrumentation;
    }

    /** Returns the instrumentation stored in this class-loader copy, if any. */
    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    /**
     * Finds startup instrumentation even when the mod loader defines a second copy of this class.
     * The JVM loads the premain class through the system class loader, while Fabric/NeoForge may
     * load normal mod classes through an isolated loader.
     */
    public static Instrumentation findInstrumentation() {
        Instrumentation local = instrumentation;
        if (local != null) {
            return local;
        }

        try {
            Class<?> systemAgent = Class.forName(
                KattonAgent.class.getName(),
                false,
                ClassLoader.getSystemClassLoader()
            );
            if (systemAgent == KattonAgent.class) {
                return null;
            }
            Method getter = systemAgent.getMethod("getInstrumentation");
            return (Instrumentation) getter.invoke(null);
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return null;
        }
    }

    /** Returns the current Katton jar path when it can be resolved from the code source. */
    public static String agentJarPath() {
        try {
            if (KattonAgent.class.getProtectionDomain() == null
                || KattonAgent.class.getProtectionDomain().getCodeSource() == null) {
                return null;
            }
            URI location = KattonAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location).toAbsolutePath().normalize();
            return path.toString().endsWith(".jar") ? path.toString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
