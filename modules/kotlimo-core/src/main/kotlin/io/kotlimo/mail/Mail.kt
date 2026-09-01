package io.kotlimo.mail

class MailMessage {
    var from: String? = null
    var to: MutableList<String> = mutableListOf()
    var cc: MutableList<String> = mutableListOf()
    var bcc: MutableList<String> = mutableListOf()
    var subject: String = ""
    var html: String = ""
    var text: String = ""

    fun to(address: String): MailMessage {
        to += address
        return this
    }

    fun subject(value: String): MailMessage {
        subject = value
        return this
    }

    fun html(value: String): MailMessage {
        html = value
        return this
    }

    fun text(value: String): MailMessage {
        text = value
        return this
    }

    fun body(): String = html.ifBlank { text }
}

interface Mailer {
    fun send(message: MailMessage)
}

class LogMailer(private val log: (String) -> Unit = { println(it) }) : Mailer {
    override fun send(message: MailMessage) {
        log("Mail to=${message.to.joinToString()} subject=${message.subject} body=${message.body()}")
    }
}

class ArrayMailer : Mailer {
    val messages = mutableListOf<MailMessage>()

    override fun send(message: MailMessage) {
        messages += message
    }

    fun flush() = messages.clear()
}
