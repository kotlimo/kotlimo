package io.kotlimo.mail

import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties

fun interface MailTransport {
    fun deliver(message: MailMessage)
}

class SmtpMailer(private val transport: MailTransport) : Mailer {
    override fun send(message: MailMessage) = transport.deliver(message)
}

class JakartaSmtpTransport(
    private val host: String,
    private val port: Int,
    private val username: String = "",
    private val password: String = "",
    private val from: String
) : MailTransport {
    override fun deliver(message: MailMessage) {
        val props = Properties()
        props["mail.smtp.host"] = host
        props["mail.smtp.port"] = port.toString()
        val auth = username.isNotBlank()
        props["mail.smtp.auth"] = auth.toString()
        val session = if (auth) {
            Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(username, password)
            })
        } else {
            Session.getInstance(props)
        }
        val mime = MimeMessage(session)
        mime.setFrom(InternetAddress(message.from ?: from))
        message.to.forEach { mime.addRecipient(jakarta.mail.Message.RecipientType.TO, InternetAddress(it)) }
        mime.subject = message.subject
        mime.setText(message.body(), "UTF-8")
        Transport.send(mime)
    }
}
