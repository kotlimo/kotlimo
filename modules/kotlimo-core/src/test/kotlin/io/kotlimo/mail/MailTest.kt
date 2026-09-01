package io.kotlimo.mail

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MailTest {
    @Test
    fun `array mailer records messages`() {
        val mailer = ArrayMailer()
        mailer.send(MailMessage().to("ada@example.com").subject("Hello").html("<p>Hi</p>"))
        assertEquals(1, mailer.messages.size)
        assertEquals("Hello", mailer.messages.single().subject)
        assertEquals(listOf("ada@example.com"), mailer.messages.single().to)
    }

    @Test
    fun `log mailer writes a line`() {
        val lines = mutableListOf<String>()
        LogMailer { lines += it }.send(MailMessage().to("ada@example.com").subject("Ping").text("body"))
        assertTrue(lines.single().contains("ada@example.com"))
        assertTrue(lines.single().contains("Ping"))
    }
}
