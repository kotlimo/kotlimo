package io.kotlimo.foundation

import io.kotlimo.console.ConsoleKernel
import io.kotlimo.console.MakeControllerCommand
import io.kotlimo.console.MakeModelCommand
import io.kotlimo.console.RouteListCommand
import io.kotlimo.console.ServeCommand
import io.kotlimo.foundation.providers.CoreServiceProvider
import io.kotlimo.foundation.providers.DatabaseServiceProvider
import io.kotlimo.foundation.providers.RoutingServiceProvider
import io.kotlimo.foundation.providers.ViewServiceProvider

fun Application.withDefaultProviders(): Application {
    register(::CoreServiceProvider)
    register(::RoutingServiceProvider)
    register(::ViewServiceProvider)
    register(::DatabaseServiceProvider)
    return this
}

fun Application.withConsole(): ConsoleKernel {
    val kernel = ConsoleKernel(this)
    kernel.register(ServeCommand())
    kernel.register(RouteListCommand())
    kernel.register(MakeControllerCommand())
    kernel.register(MakeModelCommand())
    singleton(ConsoleKernel::class) { kernel }
    return kernel
}

fun Application.run(args: Array<String>): Int {
    boot()
    val kernel = if (bound(ConsoleKernel::class)) make(ConsoleKernel::class) else withConsole()
    return kernel.handle(args)
}
