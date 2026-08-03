package com.expensetracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import com.expensetracker.model.Group;
import com.expensetracker.model.User;
import org.springframework.beans.factory.annotation.Value;

import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:localhost}")
    private String mailHost;
    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendGroupInvite(String toEmail, Group group, String token, User invitedBy) {

        String inviteLink = "https://expenseservice.onrender.com/invite/" + token;
        String subject = invitedBy.getName() + " invited you to split expenses in " + group.getName();

        if (mailHost.equals("localhost")) {
            log.info("DEV MODE: Would send email to {} - Subject: {}", toEmail, subject);
            return; // Don't actually send
        }


        String htmlContent = """
            <h2>You're invited!</h2>
            <p>%s invited you to join <strong>%s</strong> on Expense Tracker.</p>
            <p>
                <a href="%s" style="background-color: #007bff; color: white; padding: 10px 20px; 
                   text-decoration: none; border-radius: 5px;">Accept Invite</a>
            </p>
            <p>This link expires in 30 days.</p>
            """.formatted(invitedBy.getName(), group.getName(), inviteLink);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText("Click the link to join: " + inviteLink); // Fallback

            // For HTML, use MimeMessage instead
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true = HTML

            mailSender.send(mimeMessage);
            log.info("Invite email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send invite email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Email send failed", e);
        }
    }
}