package top.katton.engine;

/** Simple already-loaded target for the startup-agent injection verification. */
public final class InjectionProbeTarget {
    public int add(int left, int right) {
        return left + right;
    }
}
