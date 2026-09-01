package io.kotlimo.foundation

import io.kotlimo.console.ConsoleKernel
import io.kotlimo.console.KeyGenerateCommand
import io.kotlimo.console.MakeControllerCommand
import io.kotlimo.console.MakeMigrationCommand
import io.kotlimo.console.MakeModelCommand
import io.kotlimo.console.MigrateCommand
import io.kotlimo.console.MigrateRollbackCommand
import io.kotlimo.console.QueueWorkCommand
import io.kotlimo.console.RouteListCommand
import io.kotlimo.console.ScheduleRunCommand
import io.kotlimo.console.ServeCommand
import io.kotlimo.foundation.providers.AuthServiceProvider
import io.kotlimo.foundation.providers.CoreServiceProvider
import io.kotlimo.foundation.providers.DatabaseServiceProvider
import io.kotlimo.foundation.providers.FilesystemServiceProvider
import io.kotlimo.foundation.providers.MailServiceProvider
import io.kotlimo.foundation.providers.QueueServiceProvider
import io.kotlimo.foundation.providers.RoutingServiceProvider
import io.kotlimo.foundation.providers.SchedulingServiceProvider
import io.kotlimo.foundation.providers.SessionServiceProvider
import io.kotlimo.foundation.providers.ViewServiceProvider
import io.kotlimo.scheduling.Schedule

fun Application.withDefaultProviders(): Application {
    register(::CoreServiceProvider)
    register(::SessionServiceProvider)
    register(::AuthServiceProvider)
    register(::FilesystemServiceProvider)
    register(::QueueServiceProvider)
    register(::MailServiceProvider)
    register(::SchedulingServiceProvider)
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
    kernel.register(MakeMigrationCommand())
    kernel.register(MigrateCommand())
    kernel.register(MigrateRollbackCommand())
    kernel.register(KeyGenerateCommand())
    kernel.register(ScheduleRunCommand())
    kernel.register(QueueWorkCommand())
    singleton(ConsoleKernel::class) { kernel }
    return kernel
}

fun Application.schedule(block: Schedule.() -> Unit): Application {
    make(Schedule::class).apply(block)
    return this
}

fun Application.run(args: Array<String>): Int {
    boot()
    val kernel = if (bound(ConsoleKernel::class)) make(ConsoleKernel::class) else withConsole()
    return kernel.handle(args)
}
