package katton.examples.consumer

import katton.examples.shared.SharedCounter
import top.katton.api.ServerPhase
import top.katton.api.ServerScriptEntrypoint

@ServerScriptEntrypoint(phase = ServerPhase.READY)
fun ready() {
    println("Katton dependency smoke: shared counter = ${SharedCounter.next()}")
}
