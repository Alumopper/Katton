package top.katton.dev

import java.util.UUID

/** A target includes the actual server lifetime, including entering the first single-player world. */
internal class DevTargetSession {
    private var runtime: Any? = null
    private var id = UUID.randomUUID().toString()
    @Synchronized fun token(current: Any?): String {
        if (runtime !== current) { runtime = current; id = UUID.randomUUID().toString() }
        return id
    }
    @Synchronized fun invalidate() { id = UUID.randomUUID().toString() }
}
