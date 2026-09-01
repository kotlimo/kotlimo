package io.kotlimo.console

fun main(args: Array<String>) {
    val code = KotlimoConsole().handle(args)
    kotlin.system.exitProcess(code)
}

class KotlimoConsole {
    fun handle(args: Array<String>): Int {
        if (args.isEmpty() || args[0] in setOf("list", "help", "--help", "-h")) {
            println("Kotlimo Framework 0.1.0")
            println("")
            println("Available commands:")
            println("  new {name}             Create a new Kotlimo application")
            println("")
            println("From an application directory, run Craft through Gradle:")
            println("  ./gradlew run --args='list'")
            return 0
        }
        return when (args[0]) {
            "new" -> NewApplicationCommand().handle(args.drop(1))
            else -> {
                System.err.println("Command [${args[0]}] is not defined.")
                1
            }
        }
    }
}
