package top.katton.engine;

/** Standalone probe used by Gradle to reproduce FCL's missing-Attach runtime on a desktop JDK. */
public final class InjectionEnvironmentProbe {
    private InjectionEnvironmentProbe() {
    }

    public static void main(String[] arguments) {
        InjectionEnvironment.Snapshot snapshot = InjectionEnvironment.inspect();
        System.out.println("launcherBrand=" + snapshot.launcherBrand());
        System.out.println("fclDetected=" + snapshot.fclDetected());
        System.out.println("androidDetected=" + snapshot.androidDetected());
        System.out.println("java=" + snapshot.javaVendor() + " " + snapshot.javaVersion());
        System.out.println("vm=" + snapshot.vmName());
        System.out.println("os=" + snapshot.osName() + " " + snapshot.osVersion() + " " + snapshot.osArchitecture());
        System.out.println("java.instrument=" + snapshot.instrumentationModulePresent());
        System.out.println("jdk.attach=" + snapshot.attachModulePresent());
        System.out.println("attachProviders=" + snapshot.attachProviderCount());
        System.out.println("startupAgent=" + snapshot.startupAgentLoaded());
        System.out.println("redefine=" + snapshot.redefineClassesSupported());
        System.out.println("retransform=" + snapshot.retransformClassesSupported());
        System.out.println("startupArg=" + snapshot.startupAgentArgument());

        assertExpected("katton.probe.expectFcl", snapshot.fclDetected());
        assertExpected("katton.probe.expectAttach", snapshot.attachModulePresent());
        assertExpected("katton.probe.expectAgent", snapshot.startupAgentLoaded());
        if (Boolean.getBoolean("katton.probe.expectAgent")
            && !Boolean.TRUE.equals(snapshot.redefineClassesSupported())) {
            throw new IllegalStateException("Startup agent loaded but class redefinition is unavailable");
        }
    }

    private static void assertExpected(String property, boolean actual) {
        String expected = System.getProperty(property);
        if (expected != null && Boolean.parseBoolean(expected) != actual) {
            throw new IllegalStateException(property + "=" + expected + ", actual=" + actual);
        }
    }
}
