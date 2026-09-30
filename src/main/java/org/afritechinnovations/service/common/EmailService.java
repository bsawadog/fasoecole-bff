package org.afritechinnovations.service.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendPasswordReset(String recipient, String resetUrl, long expirationMinutes) {
        if (from.isBlank()) {
            throw new MailSendException("MAIL_FROM or SMTP_USERNAME must be configured to send email");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Réinitialisation de votre mot de passe FasoÉcole");
        message.setText("""
                Bonjour,

                Une demande de réinitialisation du mot de passe de votre compte FasoÉcole a été reçue.
                Pour choisir un nouveau mot de passe, ouvrez ce lien (valide pendant %d minutes) :

                %s

                Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.
                """.formatted(expirationMinutes, resetUrl));
        mailSender.send(message);
    }
}
