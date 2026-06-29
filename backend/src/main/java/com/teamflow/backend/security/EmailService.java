package com.teamflow.backend.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails. The {@link JavaMailSender} is resolved lazily so the application
 * still starts when no SMTP server is configured; in that case the code is logged instead of
 * sent, which is convenient for local development.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String mailHost;
    private final String fromAddress;

    public EmailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String mailHost,
            @Value("${spring.mail.username:no-reply@teamflow.app}") String fromAddress) {
        this.mailSenderProvider = mailSenderProvider;
        this.mailHost = mailHost;
        this.fromAddress = fromAddress;
    }

    public void sendVerificationCode(String to, String code) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || mailHost == null || mailHost.isBlank()) {
            log.warn("Mail is not configured; verification code for {} is {}", to, code);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("Your TeamFlow verification code");
        message.setText("Your TeamFlow verification code is %s. It expires in 15 minutes."
                .formatted(code));
        mailSender.send(message);
    }

    public void sendPasswordResetLink(String to, String resetLink) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || mailHost == null || mailHost.isBlank()) {
            log.warn("Mail is not configured; password reset link for {} is {}", to, resetLink);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject("Reset your TeamFlow password");
        message.setText("Reset your TeamFlow password using this link: %s. It expires in 30 minutes. "
                .formatted(resetLink) + "If you did not request this, you can ignore this email.");
        mailSender.send(message);
    }
}
