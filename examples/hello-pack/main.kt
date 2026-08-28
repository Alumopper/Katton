import top.katton.api.ServerPhase
import top.katton.api.ServerReadyContext
import top.katton.api.ServerScriptEntrypoint

@ServerScriptEntrypoint(phase = ServerPhase.READY)
fun main(context: ServerReadyContext) {
    println("Hello from Katton pack '${context.packId}' on ${context.platform}")
}
