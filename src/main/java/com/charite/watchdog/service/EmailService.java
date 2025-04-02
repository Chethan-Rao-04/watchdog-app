package com.charite.watchdog.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

/**
 * Service class for sending email notifications.
 * Handles the configuration and dispatch of email messages using Spring's JavaMailSender.
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String recipients;
    private final String sender;

    /**
     * Constructs a new EmailService with the specified mail sender and email configuration.
     *
     * @param mailSender the JavaMailSender instance for sending emails
     * @param recipients comma-separated list of email recipients from configuration
     * @param sender the sender email address from configuration
     */
    public EmailService(JavaMailSender mailSender,
                        @Value("${watchdog.email.recipients}") String recipients,
                        @Value("${watchdog.email.sender}") String sender) {
        this.mailSender = mailSender;
        this.recipients = recipients;
        this.sender = sender;
    }

    /**
     * Sends an email with the specified subject and text to configured recipients.
     *
     * @param subject the subject line of the email
     * @param text the body text of the email
     */
    public void sendEmail(String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(recipients.split(","));
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}