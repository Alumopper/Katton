package top.katton.engine;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

/** Collects read-only JVM facts needed to diagnose runtime injection support. */
public final class InjectionEnvironment {
    public static final String DYNAMIC_ATTACH_PROPERTY = "katton.injection.dynamicAttach";

    private InjectionEnvironment() {
    }

    /** Immutable snapshot of the JVM features relevant to class redefinition. */
    public record Snapshot(
        String javaVersion,
        String javaVendor,
        String vmName,
        String javaHome,
        String osName,
        String osVersion,
        String osArchitecture,
        String launcherBrand,
        String launcherVersion,
        boolean fclDetected,
        boolean androidDetected,
        boolean instrumentationModulePresent,
        boolean attachModulePresent,
        int attachProviderCount,
        boolean startupAgentLoaded,
        Boolean redefineClassesSupported,
        Boolean retransformClassesSupported,
        boolean dynamicAttachAllowed,
        String startupAgentArgument,
        List<String> relevantJvmArguments
    ) {
    }

    /**
     * Captures environment facts without attempting dynamic attachment.
     *
     * <p>FCL's Java 25 image includes {@code java.instrument} but omits {@code jdk.attach};
     * see its official image module list:
     * https://github.com/FCL-Team/Android-OpenJDK-Build/blob/7a0266e745d9b4acf400afa189b58e672900f710/remove_jdk_debug_info.sh#L24-L33</p>
     */
    public static Snapshot inspect() {
        String launcherBrand = property("minecraft.launcher.brand");
        String launcherVersion = property("minecraft.launcher.version");
        String osVersion = property("os.version");
        String normalizedBrand = launcherBrand.toLowerCase(Locale.ROOT);
        boolean fclDetected = normalizedBrand.contains("fold craft launcher")
            || normalizedBrand.equals("fcl")
            || System.getenv("FCL_VERSION_CODE") != null;
        boolean androidDetected = osVersion.toLowerCase(Locale.ROOT).contains("android")
            || System.getenv("ANDROID_ROOT") != null;

        Instrumentation startup = KattonAgent.findInstrumentation();
        String agentPath = KattonAgent.agentJarPath();
        String agentArgument = "-javaagent:" + (agentPath == null ? "<katton-mod-jar>" : agentPath);

        return new Snapshot(
            property("java.version"),
            property("java.vendor"),
            property("java.vm.name"),
            property("java.home"),
            property("os.name"),
            osVersion,
            property("os.arch"),
            launcherBrand,
            launcherVersion,
            fclDetected,
            androidDetected,
            ModuleLayer.boot().findModule("java.instrument").isPresent(),
            attachApiPresent(),
            attachProviderCount(),
            startup != null,
            startup == null ? null : startup.isRedefineClassesSupported(),
            startup == null ? null : startup.isRetransformClassesSupported(),
            !"false".equalsIgnoreCase(System.getProperty(DYNAMIC_ATTACH_PROPERTY, "true")),
            agentArgument,
            relevantJvmArguments()
        );
    }

    private static boolean attachApiPresent() {
        try {
            Class.forName("com.sun.tools.attach.VirtualMachine", false, ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return false;
        }
    }

    private static int attachProviderCount() {
        try {
            Class<?> providerType = Class.forName(
                "com.sun.tools.attach.spi.AttachProvider",
                false,
                ClassLoader.getSystemClassLoader()
            );
            Method providers = providerType.getMethod("providers");
            Object result = providers.invoke(null);
            return result instanceof List<?> list ? list.size() : -1;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return -1;
        }
    }

    private static List<String> relevantJvmArguments() {
        try {
            return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(argument -> {
                    String normalized = argument.toLowerCase(Locale.ROOT);
                    return normalized.contains("attach")
                        || normalized.contains("javaagent")
                        || normalized.contains("jdwp")
                        || normalized.contains("katton.injection");
                })
                .toList();
        } catch (LinkageError | SecurityException ignored) {
            return List.of();
        }
    }

    private static String property(String name) {
        return System.getProperty(name, "unknown");
    }
}
