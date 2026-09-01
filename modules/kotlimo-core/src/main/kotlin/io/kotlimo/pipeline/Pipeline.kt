package io.kotlimo.pipeline

/**
 * Laravel-style pipeline: send a passable through a stack of pipes, then to a destination.
 */
class Pipeline<TPass, TReturn> {
    private var passable: TPass? = null
    private var pipes: List<(TPass, (TPass) -> TReturn) -> TReturn> = emptyList()

    fun send(passable: TPass): Pipeline<TPass, TReturn> {
        this.passable = passable
        return this
    }

    fun through(pipes: List<(TPass, (TPass) -> TReturn) -> TReturn>): Pipeline<TPass, TReturn> {
        this.pipes = pipes
        return this
    }

    fun then(destination: (TPass) -> TReturn): TReturn {
        val carry = passable ?: throw IllegalStateException("Pipeline.send() must be called first")
        val stack = pipes.foldRight(destination) { pipe, next ->
            { passable: TPass -> pipe(passable, next) }
        }
        return stack(carry)
    }
}
