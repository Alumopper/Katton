package top.katton;

public enum LoadState {
    INIT(0x00000),
    SERVER_STARTED(0x10001),
    SERVER_STOPPED(0x10005),
    END_DATA_PACK_RELOAD(0x30005);

    // 前面位数表示阶段，后面的位数表示该阶段的顺序，这个顺序值可以用于比较
    private final int order;
    LoadState(int order) {
        this.order = order;
    }

    public boolean before(LoadState other) {
        return this.order < other.order;
    }

    public boolean orBefore(LoadState other) {
        return this.order <= other.order;
    }

    public boolean after(LoadState other) {
        return this.order > other.order;
    }

    public boolean orAfter(LoadState other) {
        return this.order >= other.order;
    }
}
