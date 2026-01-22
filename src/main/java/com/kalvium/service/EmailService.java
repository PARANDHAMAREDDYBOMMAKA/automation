package com.kalvium.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.Attachment;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    private Resend resend;

    @Value("${notification.email}")
    private String notificationEmail;

    @Value("${email.enabled:true}")
    private boolean emailEnabled;

    private final String resendApiKey = "re_KLz77iaH_AcP6jMsCfLHt6RhphLbdeCrn";
    private final String fromEmail = "onboarding@resend.dev";

    @PostConstruct
    public void init() {
        if (resendApiKey != null && !resendApiKey.isEmpty()) {
            this.resend = new Resend(resendApiKey);
            logger.info("Resend client initialized successfully");
        } else {
            logger.warn("Resend API key not configured, email notifications will be disabled");
        }
    }

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

        if (resend == null) {
            logger.warn("Resend client not initialized, skipping email notification");
            return;
        }

        try {
            logger.info("Attempting to send success notification email to {}", notificationEmail);

            List<ScreenshotData> screenshots = extractScreenshots(message);
            String cleanMessage = removeScreenshotsFromMessage(message);

            String subject = "Worklog Submission Success - " + getCurrentTimestamp();
            String htmlBody = buildSuccessEmailHtml(userId, cleanMessage);

            sendEmail(subject, htmlBody, screenshots);

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

        if (resend == null) {
            logger.warn("Resend client not initialized, skipping email notification");
            return;
        }

        try {
            logger.info("Attempting to send error notification email to {}", notificationEmail);

            List<ScreenshotData> screenshots = extractScreenshots(steps);
            String cleanSteps = removeScreenshotsFromMessage(steps);

            String subject = "Worklog Submission Failed - " + getCurrentTimestamp();
            String htmlBody = buildErrorEmailHtml(userId, errorMessage, cleanSteps);

            sendEmail(subject, htmlBody, screenshots);

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

    public void sendDeploymentSummary(int totalUsers, int successCount, int failCount) {
        if (!emailEnabled) {
            logger.info("Email notifications disabled, skipping deployment summary");
            return;
        }

        if (resend == null) {
            logger.warn("Resend client not initialized, skipping email notification");
            return;
        }

        try {
            logger.info("Attempting to send deployment summary email to {}", notificationEmail);

            String subject = "Worklog Automation Summary - " + getCurrentTimestamp();
            String htmlBody = buildSummaryEmailHtml(totalUsers, successCount, failCount);

            sendEmail(subject, htmlBody, new ArrayList<>());

            logger.info("Deployment summary email sent successfully to {}", notificationEmail);
        } catch (Exception e) {
            logger.error("Failed to send deployment summary email to {}: {} - {}",
                    notificationEmail, e.getClass().getSimpleName(), e.getMessage());
            if (e.getCause() != null) {
                logger.error("Root cause: {} - {}", e.getCause().getClass().getSimpleName(), e.getCause().getMessage());
            }
        }
    }

    private void sendEmail(String subject, String htmlBody, List<ScreenshotData> screenshots) throws ResendException {
        CreateEmailOptions.Builder requestBuilder = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(notificationEmail)
                .subject(subject)
                .html(htmlBody);

        if (!screenshots.isEmpty()) {
            List<Attachment> attachments = new ArrayList<>();
            for (int i = 0; i < screenshots.size(); i++) {
                ScreenshotData ss = screenshots.get(i);
                String filename = "screenshot_" + (i + 1) + "_" + sanitizeFilename(ss.description) + ".png";
                String base64Content = Base64.getEncoder().encodeToString(ss.imageBytes);

                Attachment attachment = Attachment.builder()
                        .fileName(filename)
                        .content(base64Content)
                        .build();
                attachments.add(attachment);
                logger.info("Attached screenshot: {}", filename);
            }
            requestBuilder.attachments(attachments);
        }

        CreateEmailOptions request = requestBuilder.build();
        CreateEmailResponse response = resend.emails().send(request);
        logger.info("Email sent successfully via Resend. Email ID: {}", response.getId());
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

    private String buildSuccessEmailHtml(String userId, String message) {
        String escapedMessage = escapeHtml(message != null ? message : "Worklog submitted successfully");
        String escapedUserId = escapeHtml(userId != null ? userId : "Unknown");

        return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                "<div style=\"background-color: #4CAF50; color: white; padding: 20px; text-align: center;\">" +
                "<h1 style=\"margin: 0;\">Worklog Submission Successful</h1>" +
                "</div>" +
                "<div style=\"padding: 20px; background-color: #f9f9f9;\">" +
                "<p><strong>User:</strong> " + escapedUserId + "</p>" +
                "<p><strong>Timestamp:</strong> " + getCurrentTimestamp() + "</p>" +
                "<p><strong>Status:</strong> <span style=\"color: #4CAF50; font-weight: bold;\">SUCCESS</span></p>" +
                "<hr style=\"border: 1px solid #ddd;\">" +
                "<h3>Details:</h3>" +
                "<pre style=\"background-color: #fff; padding: 15px; border-radius: 5px; overflow-x: auto; white-space: pre-wrap;\">" + escapedMessage + "</pre>" +
                "</div>" +
                "<div style=\"background-color: #333; color: white; padding: 10px; text-align: center; font-size: 12px;\">" +
                "Kalvium Worklog Automation System" +
                "</div>" +
                "</div>";
    }

    private String buildErrorEmailHtml(String userId, String errorMessage, String steps) {
        String escapedUserId = escapeHtml(userId != null ? userId : "Unknown");
        String escapedError = escapeHtml(errorMessage != null ? errorMessage : "Unknown error");

        String stepsHtml = "";
        if (steps != null && !steps.isEmpty()) {
            stepsHtml = "<h3>Automation Steps:</h3>" +
                    "<pre style=\"background-color: #fff; padding: 15px; border-radius: 5px; overflow-x: auto; white-space: pre-wrap;\">" +
                    escapeHtml(steps) + "</pre>";
        }

        return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                "<div style=\"background-color: #f44336; color: white; padding: 20px; text-align: center;\">" +
                "<h1 style=\"margin: 0;\">Worklog Submission Failed</h1>" +
                "</div>" +
                "<div style=\"padding: 20px; background-color: #f9f9f9;\">" +
                "<p><strong>User:</strong> " + escapedUserId + "</p>" +
                "<p><strong>Timestamp:</strong> " + getCurrentTimestamp() + "</p>" +
                "<p><strong>Status:</strong> <span style=\"color: #f44336; font-weight: bold;\">FAILED</span></p>" +
                "<hr style=\"border: 1px solid #ddd;\">" +
                "<h3>Error Message:</h3>" +
                "<pre style=\"background-color: #ffebee; padding: 15px; border-radius: 5px; color: #c62828; overflow-x: auto; white-space: pre-wrap;\">" + escapedError + "</pre>" +
                stepsHtml +
                "</div>" +
                "<div style=\"background-color: #333; color: white; padding: 10px; text-align: center; font-size: 12px;\">" +
                "Please check the logs for more details.<br>" +
                "Kalvium Worklog Automation System" +
                "</div>" +
                "</div>";
    }

    private String buildSummaryEmailHtml(int totalUsers, int successCount, int failCount) {
        String statusColor = successCount == totalUsers ? "#4CAF50" : (failCount == totalUsers ? "#f44336" : "#FF9800");
        String statusText = successCount == totalUsers ? "ALL SUCCESSFUL" : (failCount == totalUsers ? "ALL FAILED" : "PARTIAL SUCCESS");

        double successPercent = totalUsers > 0 ? (successCount * 100.0 / totalUsers) : 0;
        double failPercent = totalUsers > 0 ? (failCount * 100.0 / totalUsers) : 0;

        return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;\">" +
                "<div style=\"background-color: #2196F3; color: white; padding: 20px; text-align: center;\">" +
                "<h1 style=\"margin: 0;\">Worklog Automation Summary</h1>" +
                "</div>" +
                "<div style=\"padding: 20px; background-color: #f9f9f9;\">" +
                "<p><strong>Timestamp:</strong> " + getCurrentTimestamp() + "</p>" +
                "<hr style=\"border: 1px solid #ddd;\">" +
                "<table style=\"width: 100%; text-align: center; margin: 20px 0;\">" +
                "<tr>" +
                "<td><h2 style=\"margin: 0; color: #333;\">" + totalUsers + "</h2><p style=\"margin: 5px 0; color: #666;\">Total Users</p></td>" +
                "<td><h2 style=\"margin: 0; color: #4CAF50;\">" + successCount + "</h2><p style=\"margin: 5px 0; color: #666;\">Successful (" + String.format("%.1f", successPercent) + "%)</p></td>" +
                "<td><h2 style=\"margin: 0; color: #f44336;\">" + failCount + "</h2><p style=\"margin: 5px 0; color: #666;\">Failed (" + String.format("%.1f", failPercent) + "%)</p></td>" +
                "</tr>" +
                "</table>" +
                "<div style=\"text-align: center; padding: 15px; background-color: " + statusColor + "; color: white; border-radius: 5px;\">" +
                "<strong>Status: " + statusText + "</strong>" +
                "</div>" +
                "</div>" +
                "<div style=\"background-color: #333; color: white; padding: 10px; text-align: center; font-size: 12px;\">" +
                "Kalvium Worklog Automation System" +
                "</div>" +
                "</div>";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}
