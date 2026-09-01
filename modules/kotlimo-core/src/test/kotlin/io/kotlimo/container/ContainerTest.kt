package io.kotlimo.container

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Greeting(val name: String)
class UsesGreeting(val greeting: Greeting)
interface Mailer { fun send(): String }
class SmtpMailer : Mailer { override fun send() = "smtp" }

class ContainerTest {
    @Test
    fun `bind returns a new instance each time`() {
        val container = Container()
        container.bind(Greeting::class) { _ -> Greeting("Ada") }
        assertNotSame(container.make(Greeting::class), container.make(Greeting::class))
        assertEquals("Ada", container.make(Greeting::class).name)
    }

    @Test
    fun `singleton returns the same instance`() {
        val container = Container()
        container.singleton(Greeting::class) { _ -> Greeting("Taylor") }
        assertSame(container.make(Greeting::class), container.make(Greeting::class))
    }

    @Test
    fun `instance stores a concrete object`() {
        val container = Container()
        val greeting = Greeting("Grace")
        container.instance(Greeting::class, greeting)
        assertSame(greeting, container.make(Greeting::class))
    }

    @Test
    fun `auto wires constructor dependencies`() {
        val container = Container()
        container.bind(Greeting::class) { _ -> Greeting("Kotlin") }
        val resolved = container.make(UsesGreeting::class)
        assertEquals("Kotlin", resolved.greeting.name)
    }

    @Test
    fun `binds an interface to an implementation`() {
        val container = Container()
        container.bind(Mailer::class, SmtpMailer::class, shared = true)
        assertEquals("smtp", container.make(Mailer::class).send())
    }

    @Test
    fun `extend mutates resolved instances`() {
        val container = Container()
        container.singleton(Greeting::class) { _ -> Greeting("raw") }
        container.extend(Greeting::class) { greeting, _ -> Greeting(greeting.name.uppercase()) }
        assertEquals("RAW", container.make(Greeting::class).name)
    }

    @Test
    fun `unresolvable types throw`() {
        val container = Container()
        assertThrows(BindingResolutionException::class.java) {
            container.make(Mailer::class)
        }
    }

    @Test
    fun `aliases resolve to the original binding`() {
        val container = Container()
        container.singleton(Greeting::class) { _ -> Greeting("alias") }
        container.alias(Greeting::class.qualifiedName!!, "greet")
        assertEquals("alias", (container.make("greet") as Greeting).name)
        assertTrue(container.bound(Greeting::class))
    }
}
