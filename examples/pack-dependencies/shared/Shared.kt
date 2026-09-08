package katton.examples.shared

/** This state belongs to one active shared-behavior pack instance. */
object SharedCounter {
    private var value = 0
    @Synchronized fun next(): Int = ++value
}
