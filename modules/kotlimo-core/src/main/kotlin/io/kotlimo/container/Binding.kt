package io.kotlimo.container

internal data class Binding(
    val concrete: (Container) -> Any,
    val shared: Boolean = false
)
