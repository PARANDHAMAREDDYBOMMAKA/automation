package com.kalvium.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${notification.email}")
    private String notificationEmail;

    public void sendSuccessNotification(String userId, String message) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(notificationEmail);
            mailMessage.setSubject("✓ Worklog Submission Success - " + getCurrentTimestamp());
            mailMessage.setText(buildSuccessEmail(userId, message));
            mailMessage.setFrom(notificationEmail);

            mailSender.send(mailMessage);
            logger.info("Success notification email sent to {}", notificationEmail);
        } catch (Exception e) {
            logger.error("Failed to send success notification email: {}", e.getMessage(), e);
        }
    }

    public void sendErrorNotification(String userId, String errorMessage, String steps) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(notificationEmail);
            mailMessage.setSubject("✗ Worklog Submission Failed - " + getCurrentTimestamp());
            mailMessage.setText(buildErrorEmail(userId, errorMessage, steps));
            mailMessage.setFrom(notificationEmail);

            mailSender.send(mailMessage);
            logger.info("Error notification email sent to {}", notificationEmail);
        } catch (Exception e) {
            logger.error("Failed to send error notification email: {}", e.getMessage(), e);
        }
    }

    private String buildSuccessEmail(String userId, String message) {
        StringBuilder email = new StringBuilder();
        email.append("Worklog Submission Successful\n");
        email.append("================================\n\n");
        email.append("User: ").append(userId != null ? userId : "Unknown").append("\n");
        email.append("Timestamp: ").append(getCurrentTimestamp()).append("\n");
        email.append("Status: SUCCESS\n\n");
        email.append("Details:\n");
        email.append(message != null ? message : "Worklog submitted successfully");
        email.append("\n\n");
        email.append("================================\n");
        email.append("Kalvium Worklog Automation System\n");
        return email.toString();
    }

    private String buildErrorEmail(String userId, String errorMessage, String steps) {
        StringBuilder email = new StringBuilder();
        email.append("Worklog Submission Failed\n");
        email.append("================================\n\n");
        email.append("User: ").append(userId != null ? userId : "Unknown").append("\n");
        email.append("Timestamp: ").append(getCurrentTimestamp()).append("\n");
        email.append("Status: FAILED\n\n");
        email.append("Error Message:\n");
        email.append(errorMessage != null ? errorMessage : "Unknown error").append("\n\n");

        if (steps != null && !steps.isEmpty()) {
            email.append("Automation Steps:\n");
            email.append(steps).append("\n\n");
        }

        email.append("================================\n");
        email.append("Please check the logs for more details.\n");
        email.append("Kalvium Worklog Automation System\n");
        return email.toString();
    }

    public void sendDeploymentSummary(int totalUsers, int successCount, int failCount) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(notificationEmail);
            mailMessage.setSubject("Worklog Automation Summary - " + getCurrentTimestamp());
            mailMessage.setText(buildSummaryEmail(totalUsers, successCount, failCount));
            mailMessage.setFrom(notificationEmail);

            mailSender.send(mailMessage);
            logger.info("Deployment summary email sent to {}", notificationEmail);
        } catch (Exception e) {
            logger.error("Failed to send deployment summary email: {}", e.getMessage(), e);
        }
    }

    private String buildSummaryEmail(int totalUsers, int successCount, int failCount) {
        StringBuilder email = new StringBuilder();
        email.append("Worklog Automation Deployment Summary\n");
        email.append("========================================\n\n");
        email.append("Timestamp: ").append(getCurrentTimestamp()).append("\n\n");
        email.append("Total Users Processed: ").append(totalUsers).append("\n");
        email.append("Successful Submissions: ").append(successCount).append(" (")
             .append(totalUsers > 0 ? String.format("%.1f%%", (successCount * 100.0 / totalUsers)) : "0%")
             .append(")\n");
        email.append("Failed Submissions: ").append(failCount).append(" (")
             .append(totalUsers > 0 ? String.format("%.1f%%", (failCount * 100.0 / totalUsers)) : "0%")
             .append(")\n\n");

        if (successCount == totalUsers) {
            email.append("Status: ALL SUCCESSFUL\n");
        } else if (failCount == totalUsers) {
            email.append("Status: ALL FAILED\n");
        } else {
            email.append("Status: PARTIAL SUCCESS\n");
        }

        email.append("\n========================================\n");
        email.append("Kalvium Worklog Automation System\n");
        return email.toString();
    }

    private String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}
