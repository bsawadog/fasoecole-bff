package org.afritechinnovations.service.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String localRecipient;

    public EmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String from,
                        @Value("${MAIL_FROM:}") String mailFrom,
                        @Value("${app.mail.local-recipient:}") String localRecipient, Environment environment) {
        this.mailSender = mailSender;
        this.from = from;
        // A mixed local/other environment must retain the real recipients.
        boolean localOnly = environment.acceptsProfiles(Profiles.of("local"))
                && java.util.Arrays.stream(environment.getActiveProfiles()).allMatch("local"::equals);
        this.localRecipient = localOnly ? localRecipient.trim() : "";
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            log.info("Local MAIL_FROM='{}'; resolved app.mail.from='{}'", display(mailFrom), display(from));
            log.info("Local mail recipient override='{}'", display(this.localRecipient));
        }
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "<blank>" : value;
    }

    public void sendText(String recipient, String subject, String body) {
        if (from.isBlank()) {
            throw new MailSendException("MAIL_FROM or SMTP_USERNAME must be configured to send email");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(resolveRecipient(recipient));
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private String resolveRecipient(String recipient) {
        if (localRecipient.isBlank()) return recipient;
        log.info("Local email redirected: original recipient='{}', test recipient='{}'", recipient, localRecipient);
        return localRecipient;
    }

    public void sendPasswordReset(String recipient, String resetUrl, long expirationMinutes) {
        if (from.isBlank()) {
            throw new MailSendException("MAIL_FROM or SMTP_USERNAME must be configured to send email");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(resolveRecipient(recipient));
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
