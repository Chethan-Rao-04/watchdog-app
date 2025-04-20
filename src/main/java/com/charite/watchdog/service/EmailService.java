package com.charite.watchdog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
/**
 * Service for sending email notifications from the Watchdog system.
 * <p>
 * This service handles email configuration and dispatching using Spring's JavaMailSender.
 * </p>
 *
 * @author Chethan Rao
 * @since 1.0
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String mailUsername;
    private final String emailRecipients;

    /**
     * Constructs a new EmailService with the specified dependencies.
     * <p>
     * Initializes the service with mail sender and configuration properties.
     * </p>
     *
     * @param mailSender the Spring mail sender component
     * @param mailUsername username used as email sender address
     * @param emailRecipients comma-separated list of recipient addresses
     */
    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username}")String mailUsername,
                        @Value("${watchdog.email-recipients}") String emailRecipients)
    {
        this.mailSender = mailSender;
        this.mailUsername = mailUsername;
        this.emailRecipients = emailRecipients;
    }

    /**
     * Sends an email with the specified subject and content.
     * <p>
     * Creates and dispatches a simple email message to all configured recipients.
     * </p>
     *
     * @param subject the email subject line
     * @param text the email body content
     */
    public void sendEmail(String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(emailRecipients.split(","));
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}