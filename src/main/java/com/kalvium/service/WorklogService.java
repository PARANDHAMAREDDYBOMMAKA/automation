package com.kalvium.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kalvium.model.AuthConfig;

import io.github.bonigarcia.wdm.WebDriverManager;

@Service
public class WorklogService {

    private static final Logger logger = LoggerFactory.getLogger(WorklogService.class);

    @Autowired
    private SupabaseConfigStorageService supabaseStorage;

    private static class Screenshot {
        String description;
        String base64Data;

        Screenshot(String description, String base64Data) {
            this.description = description;
            this.base64Data = base64Data;
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    public String submitWorklog(AuthConfig config) {
        WebDriver driver = null;
        List<String> automationSteps = new ArrayList<>();
        List<Screenshot> screenshots = new ArrayList<>();

        try {
            if (config == null || config.getAuthSessionId() == null) {
                return "ERROR: No configuration provided.";
            }

            addStep(automationSteps, "Setting up ChromeDriver...");
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments(
                    "--headless=new",
                    "--no-sandbox",
                    "--disable-setuid-sandbox",
                    "--disable-dev-shm-usage",
                    "--disable-blink-features=AutomationControlled",
                    "--window-size=1280,720",
                    "--disable-gpu",
                    "--disable-extensions",
                    "--disable-background-networking",
                    "--disable-default-apps",
                    "--disable-sync",
                    "--metrics-recording-only",
                    "--mute-audio",
                    "--no-first-run",
                    "--safebrowsing-disable-auto-update",
                    "--disable-client-side-phishing-detection",
                    "--disable-component-extensions-with-background-pages",
                    "--disable-features=VizDisplayCompositor,site-per-process",
                    "--disable-software-rasterizer",
                    "--disable-dev-tools",
                    "--disable-animations",
                    "--disable-smooth-scrolling",
                    "--disable-background-timer-throttling",
                    "--disable-renderer-backgrounding",
                    "--disable-backgrounding-occluded-windows",
                    "--disable-ipc-flooding-protection",
                    "--disable-breakpad",
                    "--disable-component-update",
                    "--no-pings",
                    "--media-cache-size=1",
                    "--disk-cache-size=1",
                    "--aggressive-cache-discard",
                    "--disable-cache",
                    "--disable-application-cache",
                    "--disable-offline-load-stale-cache",
                    "--js-flags=--max-old-space-size=200,--max-semi-space-size=1",
                    "--remote-debugging-port=9222",
                    "--disable-web-security",
                    "--ignore-certificate-errors",
                    "--disable-features=IsolateOrigins",
                    "--disable-site-isolation-trials",
                    "--single-process"
            );
            options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

            addStep(automationSteps, "Opening Chrome browser...");
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(180));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(60));
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

            addStep(automationSteps, "Navigating to kalvium.community...");
            driver.get("https://kalvium.community");
            Thread.sleep(2000);

            addStep(automationSteps, "Injecting authentication cookies...");
            injectCookies(driver, config);
            addStep(automationSteps, "Cookies injected successfully");

            addStep(automationSteps, "Navigating to internships page...");
            driver.get("https://kalvium.community/internships");
            Thread.sleep(3000);
            captureScreenshot(driver, screenshots, "Internships page loaded", config.getAuthSessionId());

            addStep(automationSteps, "Checking Pending worklogs section...");
            JavascriptExecutor js = (JavascriptExecutor) driver;
            WebElement worklogsSection = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), 'My Worklogs')]")));
            js.executeScript("arguments[0].scrollIntoView(true);", worklogsSection);
            Thread.sleep(1000);

            boolean expanded = false;
            try {
                WebElement pendingSection = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//*[text()='Pending']/parent::*")));
                addStep(automationSteps, "Found Pending section parent element");

                try {
                    WebElement table = driver.findElement(By.xpath("//*[text()='Date' and following-sibling::*[text()='Submit By']]"));
                    if (table.isDisplayed()) {
                        expanded = true;
                        addStep(automationSteps, "Pending section already expanded");
                    }
                } catch (Exception ignored) {}

                if (!expanded) {
                    addStep(automationSteps, "Attempting to expand Pending section...");
                    try {
                        js.executeScript("arguments[0].click();", pendingSection);
                        Thread.sleep(1500);
                        addStep(automationSteps, "Clicked Pending parent");
                    } catch (Exception e1) {
                        WebElement pendingText = driver.findElement(By.xpath("//*[text()='Pending']"));
                        js.executeScript("arguments[0].click();", pendingText);
                        Thread.sleep(1500);
                        addStep(automationSteps, "Clicked Pending text");
                    }
                }
            } catch (Exception e) {
                addStep(automationSteps, "Error expanding Pending section: " + e.getMessage());
            }

            addStep(automationSteps, "Looking for pending worklog...");
            WebElement completeButton = findPendingWorklog(driver, wait);
            addStep(automationSteps, "Found pending worklog button");

            addStep(automationSteps, "Clicking Complete button...");
            completeButton.click();
            Thread.sleep(3000);

            addStep(automationSteps, "Waiting for worklog form to appear...");
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), 'My Worklog')]")));
            addStep(automationSteps, "Form appeared");
            Thread.sleep(2000);
            captureScreenshot(driver, screenshots, "Worklog form opened", config.getAuthSessionId());

            addStep(automationSteps, "Filling out the form...");
            fillForm(driver, wait, config, automationSteps);
            captureScreenshot(driver, screenshots, "Form filled", config.getAuthSessionId());

            addStep(automationSteps, "Submitting the form...");
            Thread.sleep(2000);
            WebElement submitButton;
            try {
                submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[contains(text(), 'Submit')]")));
            } catch (Exception e1) {
                try {
                    submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[@type='submit']")));
                } catch (Exception e2) {
                    // Try finding any button in the form
                    submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[contains(@class, 'submit') or contains(@class, 'btn')]")));
                }
            }
            addStep(automationSteps, "Found submit button, clicking...");
            submitButton.click();
            Thread.sleep(3000);

            addStep(automationSteps, "Worklog submitted successfully!");
            captureScreenshot(driver, screenshots, "Final confirmation", config.getAuthSessionId());
            logger.info("Worklog submitted successfully");
            return buildSuccessResponse(automationSteps, screenshots);

        } catch (Exception e) {
            addStep(automationSteps, "ERROR: " + e.getMessage());
            logger.error("Error: " + e.getMessage(), e);
            if (driver != null) {
                try {
                    String authId = (config != null) ? config.getAuthSessionId() : null;
                    captureScreenshot(driver, screenshots, "Error state", authId);
                } catch (Exception screenshotError) {
                    logger.warn("Failed to capture error screenshot: " + screenshotError.getMessage());
                }
            }
            return buildErrorResponse(automationSteps, screenshots, e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    addStep(automationSteps, "Browser closed");
                } catch (Exception e) {
                    logger.error("Error closing driver: " + e.getMessage());
                    try {
                        Runtime.getRuntime().exec("pkill -f chrome");
                        logger.warn("Forcefully killed Chrome processes");
                    } catch (Exception killError) {
                        logger.error("Could not kill Chrome processes: " + killError.getMessage());
                    }
                }
            }

            // Clean up Chrome temp files to free memory
            try {
                Runtime.getRuntime().exec(new String[]{"sh", "-c", "rm -rf /tmp/.org.chromium.Chromium.* /tmp/chrome* 2>/dev/null || true"});
                logger.info("Cleaned up Chrome temporary files");
            } catch (Exception cleanupError) {
                logger.warn("Could not clean up temp files: " + cleanupError.getMessage());
            }

            // Force garbage collection to free memory
            System.gc();

            automationSteps.clear();
            screenshots.clear();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void addStep(List<String> steps, String step) {
        steps.add(step);
        logger.info(step);
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String updateAllListSections(String html, String tasksContent, String challengesContent, String blockersContent) {
        // Define template phrases to remove for each section
        String[] templatesToRemove = {
            "Add more tasks",
            "Add any obstacles encountered and how you resolved them",
            "Add any blockers faced"
        };

        // Remove all template list items first
        String updatedHtml = html;
        for (String template : templatesToRemove) {
            updatedHtml = removeListItemContaining(updatedHtml, template);
        }

        // Now update each <ul> section separately
        // We need to find and update ul[1], ul[2], and ul[3]
        updatedHtml = updateNthListFirstItem(updatedHtml, 1, tasksContent);
        updatedHtml = updateNthListFirstItem(updatedHtml, 2, challengesContent);
        updatedHtml = updateNthListFirstItem(updatedHtml, 3, blockersContent);

        return updatedHtml;
    }

    private String updateNthListFirstItem(String html, int ulIndex, String newContent) {
        // Find the nth <ul> element
        int ulCount = 0;
        int searchStart = 0;
        int targetUlStart = -1;
        int targetUlEnd = -1;

        while (ulCount < ulIndex) {
            int ulStart = html.indexOf("<ul", searchStart);
            if (ulStart == -1) {
                return html; // Couldn't find the nth <ul>
            }

            ulCount++;
            if (ulCount == ulIndex) {
                targetUlStart = ulStart;
                targetUlEnd = html.indexOf("</ul>", ulStart);
                break;
            }

            searchStart = ulStart + 3;
        }

        if (targetUlStart == -1 || targetUlEnd == -1) {
            return html;
        }

        // Find the first <li> within this <ul>
        int firstLiStart = html.indexOf("<li", targetUlStart);
        if (firstLiStart == -1 || firstLiStart > targetUlEnd) {
            return html; // No <li> found in this <ul>
        }

        int firstLiEnd = html.indexOf("</li>", firstLiStart);
        if (firstLiEnd == -1 || firstLiEnd > targetUlEnd) {
            return html;
        }

        // Replace the content of this first <li>
        StringBuilder result = new StringBuilder();
        result.append(html.substring(0, firstLiStart));
        result.append("<li class=\"list-item\"><p>")
              .append(escapeHtml(newContent.trim()))
              .append("</p></li>");
        result.append(html.substring(firstLiEnd + 5));

        return result.toString();
    }

    private String removeListItemContaining(String html, String text) {
        // Look for <li> tags containing the specified text
        int searchStart = 0;
        while (true) {
            int liStart = html.indexOf("<li", searchStart);
            if (liStart == -1) {
                break;
            }

            int liEnd = html.indexOf("</li>", liStart);
            if (liEnd == -1) {
                break;
            }

            String liContent = html.substring(liStart, liEnd + 5);

            // Check if this <li> contains the text to remove
            if (liContent.contains(text)) {
                // Remove this entire <li> element
                html = html.substring(0, liStart) + html.substring(liEnd + 5);
                // Don't increment searchStart since we removed content
                continue;
            }

            searchStart = liEnd + 5;
        }

        return html;
    }

    @SuppressWarnings({"UseSpecificCatch", "unused"})
    private void captureScreenshot(WebDriver driver, List<Screenshot> screenshots, String description) {
        captureScreenshot(driver, screenshots, description, null);
    }

    @SuppressWarnings("UseSpecificCatch")
    private void captureScreenshot(WebDriver driver, List<Screenshot> screenshots, String description, String authSessionId) {
        try {
            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            String base64Screenshot = Base64.getEncoder().encodeToString(screenshotBytes);
            screenshots.add(new Screenshot(description, base64Screenshot));
            logger.info("Screenshot captured: " + description);

            if (authSessionId != null && supabaseStorage != null) {
                try {
                    supabaseStorage.saveScreenshot(authSessionId, description, screenshotBytes);
                } catch (Exception dbError) {
                    logger.warn("Failed to save screenshot to database: " + dbError.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to capture screenshot '" + description + "': " + e.getMessage());
        }
    }

    private String buildSuccessResponse(List<String> automationSteps, List<Screenshot> screenshots) {
        StringBuilder response = new StringBuilder("SUCCESS: Worklog submitted!\n\n");
        response.append("STEPS:\n");
        for (String step : automationSteps) {
            response.append(step).append("\n");
        }

        if (!screenshots.isEmpty()) {
            response.append("\nSCREENSHOTS:\n");
            for (int i = 0; i < screenshots.size(); i++) {
                Screenshot ss = screenshots.get(i);
                response.append(String.format("[SCREENSHOT_%d]%s|%s\n",
                    i, ss.description, ss.base64Data));
            }
        }
        return response.toString();
    }

    private String buildErrorResponse(List<String> automationSteps, List<Screenshot> screenshots, String error) {
        StringBuilder response = new StringBuilder("ERROR: ").append(error).append("\n\n");
        response.append("STEPS:\n");
        for (String step : automationSteps) {
            response.append(step).append("\n");
        }

        if (!screenshots.isEmpty()) {
            response.append("\nSCREENSHOTS:\n");
            for (int i = 0; i < screenshots.size(); i++) {
                Screenshot ss = screenshots.get(i);
                response.append(String.format("[SCREENSHOT_%d]%s|%s\n",
                    i, ss.description, ss.base64Data));
            }
        }
        return response.toString();
    }

    private void injectCookies(WebDriver driver, AuthConfig config) {
        // Clear all existing cookies to prevent user session conflicts
        driver.manage().deleteAllCookies();

        driver.manage().addCookie(new Cookie.Builder("AUTH_SESSION_ID", config.getAuthSessionId())
                .domain("kalvium.community").path("/").isSecure(true).build());
        driver.manage().addCookie(new Cookie.Builder("AUTH_SESSION_ID_LEGACY", config.getAuthSessionId())
                .domain("kalvium.community").path("/").isSecure(true).build());
        driver.manage().addCookie(new Cookie.Builder("KEYCLOAK_IDENTITY", config.getKeycloakIdentity())
                .domain("kalvium.community").path("/").isSecure(true).build());
        driver.manage().addCookie(new Cookie.Builder("KEYCLOAK_IDENTITY_LEGACY", config.getKeycloakIdentity())
                .domain("kalvium.community").path("/").isSecure(true).build());
        driver.manage().addCookie(new Cookie.Builder("KEYCLOAK_SESSION", config.getKeycloakSession())
                .domain("kalvium.community").path("/").isSecure(true).build());
        driver.manage().addCookie(new Cookie.Builder("KEYCLOAK_SESSION_LEGACY", config.getKeycloakSession())
                .domain("kalvium.community").path("/").isSecure(true).build());
    }

    @SuppressWarnings({"UseSpecificCatch", "unused"})
    private WebElement findPendingWorklog(WebDriver driver, WebDriverWait wait) {
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//*[text()='Pending']/following::*[text()='Complete'][1]")));
        } catch (Exception e1) {
            try {
                String today = java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
                return wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//table//td[contains(text(), '" + today + "')]/..//td[text()='Complete']")));
            } catch (Exception e2) {
                return wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//tr//td[text()='Complete'] | //tr//*[text()='Complete']")));
            }
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    private void fillForm(WebDriver driver, WebDriverWait wait, AuthConfig config, List<String> automationSteps) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        addStep(automationSteps, "Looking for work status dropdown...");
        boolean dropdownSelected = false;

        try {
            WebElement dropdown = driver.findElement(By.xpath("//select"));
            js.executeScript("arguments[0].scrollIntoView(true);", dropdown);
            Thread.sleep(500);

            js.executeScript(
                "var select = arguments[0];" +
                "for(var i = 0; i < select.options.length; i++) {" +
                "  if(select.options[i].text.includes('Working out of the Kalvium environment')) {" +
                "    select.selectedIndex = i;" +
                "    select.dispatchEvent(new Event('change', { bubbles: true }));" +
                "    break;" +
                "  }" +
                "}", dropdown);
            Thread.sleep(500);
            dropdownSelected = true;
            addStep(automationSteps, "Selected dropdown using native select");
        } catch (Exception e1) {
            addStep(automationSteps, "Native select not found, trying custom dropdown...");
        }

        if (!dropdownSelected) {
            try {
                WebElement dropdownContainer = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//*[contains(text(), 'What is your work status')]/following::*[contains(text(), 'Select your response') or contains(@class, 'select')]")));
                js.executeScript("arguments[0].scrollIntoView(true);", dropdownContainer);
                Thread.sleep(300);
                js.executeScript("arguments[0].click();", dropdownContainer);
                Thread.sleep(1000);

                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//*[text()='Working out of the Kalvium environment (Classroom)']")));
                js.executeScript("arguments[0].scrollIntoView(true);", option);
                Thread.sleep(300);
                js.executeScript("arguments[0].click();", option);
                Thread.sleep(500);
                addStep(automationSteps, "Selected: Working out of the Kalvium environment (Classroom)");
            } catch (Exception e2) {
                addStep(automationSteps, "ERROR: Could not select dropdown - form may fail validation");
            }
        }

        String tasksContent = config.getTasksCompleted() != null ? config.getTasksCompleted() : "Need to complete the tasks completed.";
        String challengesContent = config.getChallenges() != null ? config.getChallenges() : "NA";
        String blockersContent = config.getBlockers() != null ? config.getBlockers() : "NA";

        addStep(automationSteps, "Looking for the contenteditable worklog field...");

        // Find the single contenteditable div containing all three sections
        WebElement worklogField = null;
        try {
            // Try multiple strategies to find the contenteditable div
            worklogField = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//div[@contenteditable='true' and contains(@class, 'tiptap')]")
            ));
            addStep(automationSteps, "Found worklog field using tiptap class");
        } catch (Exception e1) {
            try {
                worklogField = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), '📋 Tasks completed today')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true']")
                ));
                addStep(automationSteps, "Found worklog field using Tasks heading");
            } catch (Exception e2) {
                try {
                    worklogField = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//form//div[@contenteditable='true']")
                    ));
                    addStep(automationSteps, "Found worklog field using form contenteditable");
                } catch (Exception e3) {
                    addStep(automationSteps, "ERROR: Could not find contenteditable worklog field");
                    throw e3;
                }
            }
        }

        addStep(automationSteps, "Updating all worklog sections (Tasks, Challenges, Blockers)...");
        String originalHtml = (String) js.executeScript("return arguments[0].innerHTML;", worklogField);
        addStep(automationSteps, "Original HTML length: " + originalHtml.length());

        String updatedHtml = updateAllListSections(originalHtml, tasksContent, challengesContent, blockersContent);
        addStep(automationSteps, "Updated HTML length: " + updatedHtml.length());

        js.executeScript("arguments[0].innerHTML = arguments[1]; arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", worklogField, updatedHtml);
        Thread.sleep(1000);

        addStep(automationSteps, "All form fields filled successfully");
    }

}
