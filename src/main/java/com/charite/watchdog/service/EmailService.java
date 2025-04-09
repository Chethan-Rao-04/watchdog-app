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
    private final String mailUsername;
    private final String emailRecipients;

    /**
     * Constructs a new EmailService with the specified mail sender and email configuration.
     */
    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username}")String mailUsername,
                        @Value("${watchdog.email-recipients}") String emailRecipients
                         )
    {
        this.mailSender = mailSender;
        this.mailUsername = mailUsername;
        this.emailRecipients = emailRecipients;
    }

    /**
     * Sends an email with the specified subject and text to configured recipients.
     *
     * @param subject the subject line of the email
     * @param text the body text of the email
     */
    public void sendEmail(String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername); // Use mailUsername as sender
        message.setTo(emailRecipients.split(","));
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}