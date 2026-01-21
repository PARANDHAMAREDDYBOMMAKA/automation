package com.kalvium.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final Pattern SCREENSHOT_PATTERN = Pattern.compile("\\[SCREENSHOT_(\\d+)]([^|]+)\\|([A-Za-z0-9+/=]+)");

    @Autowired
    private JavaMailSender mailSender;

    @Value("${notification.email}")
    private String notificationEmail;

    @Value("${email.enabled:true}")
    private boolean emailEnabled;

    private static class ScreenshotData {
        String description;
        byte[] imageBytes;

        ScreenshotData(String description, byte[] imageBytes) {
            this.description = description;
            this.imageBytes = imageBytes;
        }
    }

    public void sendSuccessNotification(String userId, String message) {
        if (!emailEnabled) {
            logger.info("Email notifications disabled, skipping success notification for user {}", userId);
            return;
        }
        try {
            logger.info("Attempting to send success notification email to {}", notificationEmail);

            List<ScreenshotData> screenshots = extractScreenshots(message);
            String cleanMessage = removeScreenshotsFromMessage(message);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(notificationEmail);
            helper.setSubject("Worklog Submission Success - " + getCurrentTimestamp());
            helper.setText(buildSuccessEmail(userId, cleanMessage));
            helper.setFrom(notificationEmail);

            for (int i = 0; i < screenshots.size(); i++) {
                ScreenshotData ss = screenshots.get(i);
                String filename = "screenshot_" + (i + 1) + "_" + sanitizeFilename(ss.description) + ".png";
                helper.addAttachment(filename, new ByteArrayResource(ss.imageBytes), "image/png");
                logger.info("Attached screenshot: {}", filename);
            }

            mailSender.send(mimeMessage);
            logger.info("Success notification email sent successfully to {} with {} screenshots",
                notificationEmail, screenshots.size());
        } catch (Exception e) {
            logger.error("Failed to send success notification email to {}: {} - {}",
                notificationEmail, e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                logger.error("Root cause: {} - {}", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
            }
        }
    }

    public void sendErrorNotification(String userId, String errorMessage, String steps) {
        if (!emailEnabled) {
            logger.info("Email notifications disabled, skipping error notification for user {}", userId);
            return;
        }
        try {
            logger.info("Attempting to send error notification email to {}", notificationEmail);

            List<ScreenshotData> screenshots = extractScreenshots(steps);
            String cleanSteps = removeScreenshotsFromMessage(steps);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(notificationEmail);
            helper.setSubject("Worklog Submission Failed - " + getCurrentTimestamp());
            helper.setText(buildErrorEmail(userId, errorMessage, cleanSteps));
            helper.setFrom(notificationEmail);

            for (int i = 0; i < screenshots.size(); i++) {
                ScreenshotData ss = screenshots.get(i);
                String filename = "screenshot_" + (i + 1) + "_" + sanitizeFilename(ss.description) + ".png";
                helper.addAttachment(filename, new ByteArrayResource(ss.imageBytes), "image/png");
                logger.info("Attached screenshot: {}", filename);
            }

            mailSender.send(mimeMessage);
            logger.info("Error notification email sent successfully to {} with {} screenshots",
                notificationEmail, screenshots.size());
        } catch (Exception e) {
            logger.error("Failed to send error notification email to {}: {} - {}",
                notificationEmail, e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                logger.error("Root cause: {} - {}", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
            }
        }
    }

    private List<ScreenshotData> extractScreenshots(String message) {
        List<ScreenshotData> screenshots = new ArrayList<>();
        if (message == null) {
            return screenshots;
        }

        Matcher matcher = SCREENSHOT_PATTERN.matcher(message);
        while (matcher.find()) {
            try {
                String description = matcher.group(2).trim();
                String base64Data = matcher.group(3);
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                screenshots.add(new ScreenshotData(description, imageBytes));
                logger.debug("Extracted screenshot: {}", description);
            } catch (Exception e) {
                logger.warn("Failed to decode screenshot: {}", e.getMessage());
            }
        }

        return screenshots;
    }

    private String removeScreenshotsFromMessage(String message) {
        if (message == null) {
            return "";
        }
        String cleaned = SCREENSHOT_PATTERN.matcher(message).replaceAll("");
        cleaned = cleaned.replaceAll("\\nSCREENSHOTS:\\n*", "");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
    }

    private String sanitizeFilename(String name) {
        return name.toLowerCase()
                   .replaceAll("[^a-z0-9]", "_")
                   .replaceAll("_+", "_")
                   .replaceAll("^_|_$", "");
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
        if (!emailEnabled) {
            logger.info("Email notifications disabled, skipping deployment summary");
            return;
        }
        try {
            logger.info("Attempting to send deployment summary email to {}", notificationEmail);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            helper.setTo(notificationEmail);
            helper.setSubject("Worklog Automation Summary - " + getCurrentTimestamp());
            helper.setText(buildSummaryEmail(totalUsers, successCount, failCount));
            helper.setFrom(notificationEmail);

            mailSender.send(mimeMessage);
            logger.info("Deployment summary email sent successfully to {}", notificationEmail);
        } catch (Exception e) {
            logger.error("Failed to send deployment summary email to {}: {} - {}",
                notificationEmail, e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                logger.error("Root cause: {} - {}", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
            }
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
