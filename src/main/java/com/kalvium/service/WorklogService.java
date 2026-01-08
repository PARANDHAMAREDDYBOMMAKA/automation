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

            addStep(automationSteps, "Killing any existing Chrome processes...");
            killAllChromeProcesses();
            Thread.sleep(2000);

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
                    "--no-first-run",
                    "--disable-software-rasterizer",
                    "--disable-crash-reporter",
                    "--ignore-certificate-errors",
                    "--disable-logging",
                    "--log-level=3",
                    "--silent",
                    "--remote-debugging-port=9222",
                    "--disable-features=VizDisplayCompositor,NetworkService",
                    "--disable-web-security",
                    "--enable-unsafe-swiftshader",
                    "--remote-allow-origins=*",
                    "--disable-images",
                    "--blink-settings=imagesEnabled=false",
                    "--disable-plugins",
                    "--disable-accelerated-2d-canvas",
                    "--disable-accelerated-jpeg-decoding",
                    "--disable-accelerated-mjpeg-decode",
                    "--disable-accelerated-video-decode",
                    "--disable-background-timer-throttling",
                    "--disable-backgrounding-occluded-windows",
                    "--disable-renderer-backgrounding",
                    "--disable-ipc-flooding-protection",
                    "--disable-client-side-phishing-detection",
                    "--disable-hang-monitor",
                    "--disable-prompt-on-repost",
                    "--disable-domain-reliability",
                    "--disable-component-update"
            );
            options.setPageLoadStrategy(PageLoadStrategy.NONE);
            options.setAcceptInsecureCerts(true);

            // Critical memory settings
            options.addArguments("--memory-pressure-off");
            options.addArguments("--max-old-space-size=128");
            options.addArguments("--js-flags=--max-old-space-size=128");

            addStep(automationSteps, "Opening Chrome browser...");
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // CRITICAL FIX: Navigate to the domain FIRST before injecting cookies
            addStep(automationSteps, "Navigating to kalvium.community (required for cookies)...");
            driver.get("https://kalvium.community");

            // With PageLoadStrategy.NONE, wait manually
            Thread.sleep(3000);

            // Force stop any pending loads
            try {
                js.executeScript("window.stop();");
            } catch (Exception ignored) {}

            Thread.sleep(500);

            addStep(automationSteps, "Page loaded, current domain: " + driver.getCurrentUrl());

            addStep(automationSteps, "Injecting authentication cookies...");
            injectCookies(driver, config);
            addStep(automationSteps, "Cookies injected successfully");

            addStep(automationSteps, "Navigating to internships page...");
            driver.get("https://kalvium.community/internships");
            Thread.sleep(5000);

            // Force stop loading
            try {
                js.executeScript("window.stop();");
            } catch (Exception ignored) {}

            Thread.sleep(1000);
            captureScreenshot(driver, screenshots, "Internships page loaded", config.getAuthSessionId());

            addStep(automationSteps, "Looking for pending worklog button using optimized xpath...");

            // Use the exact xpath provided by user with fallbacks
            WebElement completeButton = null;
            try {
                // Primary xpath from user
                completeButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//*[@id='radix-:r1p:']/div/div[2]/table/tbody/tr/td[3]/button")));
                addStep(automationSteps, "Found button using primary xpath");
            } catch (Exception e1) {
                try {
                    // Fallback: more flexible xpath for the Complete button
                    completeButton = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//table//tbody//tr//td//button[contains(text(), 'Complete') or @aria-label='Complete']")));
                    addStep(automationSteps, "Found button using flexible xpath");
                } catch (Exception e2) {
                    // Last resort: find any button in table
                    completeButton = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//table//tbody//tr//td[3]//button")));
                    addStep(automationSteps, "Found button using td[3] position");
                }
            }

            addStep(automationSteps, "Clicking Complete button...");
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", completeButton);
            Thread.sleep(500);
            js.executeScript("arguments[0].click();", completeButton);
            Thread.sleep(2000);

            addStep(automationSteps, "Waiting for worklog form to appear...");
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), 'My Worklog') or contains(text(), 'Worklog')]")));
            Thread.sleep(1500);
            captureScreenshot(driver, screenshots, "Worklog form opened", config.getAuthSessionId());

            addStep(automationSteps, "Filling out the form with optimized xpaths...");
            fillFormOptimized(driver, wait, js, config, automationSteps);
            Thread.sleep(1000);
            captureScreenshot(driver, screenshots, "Form filled", config.getAuthSessionId());

            addStep(automationSteps, "Submitting the form...");
            WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(), 'Submit') or @type='submit']")));
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", submitButton);
            Thread.sleep(500);
            js.executeScript("arguments[0].click();", submitButton);
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
                    logger.info("Browser closed");
                } catch (Exception e) {
                    logger.error("Error closing driver: " + e.getMessage());
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }

            killAllChromeProcesses();

            try {
                Runtime.getRuntime().exec(new String[]{"sh", "-c", "rm -rf /tmp/.org.chromium.Chromium.* /tmp/chrome* /tmp/scoped_dir* 2>/dev/null || true"});
                logger.info("Cleaned up Chrome temporary files");
            } catch (Exception cleanupError) {
                logger.warn("Could not clean up temp files: " + cleanupError.getMessage());
            }

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

    @SuppressWarnings("UseSpecificCatch")
    private void killAllChromeProcesses() {
        try {
            Process p1 = Runtime.getRuntime().exec(new String[]{"sh", "-c", "pkill -9 -f 'chrome|chromedriver' || true"});
            p1.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            Thread.sleep(500);
            logger.info("Killed all Chrome processes");
        } catch (Exception e) {
            logger.warn("Error killing Chrome processes: " + e.getMessage());
        }
    }

    private void addStep(List<String> steps, String step) {
        steps.add(step);
        logger.info(step);
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
        // Clear all existing cookies
        driver.manage().deleteAllCookies();

        // Extract just the cookie value without any domain suffix
        // Example: "771ac62d-b6ec-4b1f-9d5f-31d4fdf63aa2.localhost-58590" -> "771ac62d-b6ec-4b1f-9d5f-31d4fdf63aa2"
        String authSessionId = config.getAuthSessionId();
        String keycloakIdentity = config.getKeycloakIdentity();
        String keycloakSession = config.getKeycloakSession();

        // Clean AUTH_SESSION_ID - take everything before the first "."
        if (authSessionId != null && authSessionId.contains(".")) {
            String[] parts = authSessionId.split("\\.", 2);
            authSessionId = parts[0];
            logger.info("Sanitized AUTH_SESSION_ID from {} to {}", config.getAuthSessionId(), authSessionId);
        }

        // Clean KEYCLOAK_IDENTITY if needed
        if (keycloakIdentity != null && keycloakIdentity.contains(".") && keycloakIdentity.split("\\.").length > 5) {
            String[] parts = keycloakIdentity.split("\\.", 2);
            keycloakIdentity = parts[0];
        }

        // Clean KEYCLOAK_SESSION if needed
        if (keycloakSession != null && keycloakSession.contains(".") && keycloakSession.split("\\.").length > 5) {
            String[] parts = keycloakSession.split("\\.", 2);
            keycloakSession = parts[0];
        }

        // Add cookies - domain must match current page domain
        driver.manage().addCookie(new Cookie.Builder("AUTH_SESSION_ID", authSessionId)
                .domain(".kalvium.community").path("/").isSecure(true).build());
        driver.manage().addCookie(new Cookie.Builder("AUTH_SESSION_ID_LEGACY", authSessionId)
                .domain(".kalvium.community").path("/").isSecure(true).build());
        driver.manage().addCookie(new Cookie.Builder("KEYCLOAK_IDENTITY", keycloakIdentity)
                .domain(".kalvium.community").path("/").isSecure(true).build());
        driver.manage().addCookie(new Cookie.Builder("KEYCLOAK_IDENTITY_LEGACY", keycloakIdentity)
                .domain(".kalvium.community").path("/").isSecure(true).build());
        driver.manage().addCookie(new Cookie.Builder("KEYCLOAK_SESSION", keycloakSession)
                .domain(".kalvium.community").path("/").isSecure(true).build());
        driver.manage().addCookie(new Cookie.Builder("KEYCLOAK_SESSION_LEGACY", keycloakSession)
                .domain(".kalvium.community").path("/").isSecure(true).build());

        logger.info("Cookies injected successfully for user");
    }

    @SuppressWarnings("UseSpecificCatch")
    private void fillFormOptimized(WebDriver driver, WebDriverWait wait, JavascriptExecutor js,
                                   AuthConfig config, List<String> automationSteps) throws InterruptedException {

        addStep(automationSteps, "Looking for work status dropdown...");

        // Try using the user-provided xpath first: //*[@id="workType"]
        WebElement dropdown = null;
        try {
            dropdown = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//*[@id='workType']")));
            addStep(automationSteps, "Found dropdown using provided xpath: //*[@id='workType']");
        } catch (Exception e1) {
            try {
                // Fallback to select element
                dropdown = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//select")));
                addStep(automationSteps, "Found dropdown using generic select xpath");
            } catch (Exception e2) {
                addStep(automationSteps, "Warning: Dropdown not found, trying custom dropdown...");
            }
        }

        if (dropdown != null) {
            try {
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", dropdown);
                Thread.sleep(300);

                // Try to select using the user-provided option xpath
                boolean selected = (boolean) js.executeScript(
                    "var select = arguments[0];" +
                    "if (select.tagName === 'SELECT') {" +
                    "  for(var i = 0; i < select.options.length; i++) {" +
                    "    if(select.options[i].text.includes('Working out of the Kalvium environment') || " +
                    "       select.options[i].text.includes('Classroom')) {" +
                    "      select.selectedIndex = i;" +
                    "      select.dispatchEvent(new Event('change', { bubbles: true }));" +
                    "      return true;" +
                    "    }" +
                    "  }" +
                    "}" +
                    "return false;", dropdown);

                if (selected) {
                    addStep(automationSteps, "Selected 'Working out of Kalvium environment' from dropdown");
                } else {
                    addStep(automationSteps, "Could not auto-select, dropdown may require manual interaction");
                }
                Thread.sleep(500);
            } catch (Exception e) {
                addStep(automationSteps, "Error selecting dropdown: " + e.getMessage());
            }
        }

        // If native select didn't work, try custom dropdown
        if (dropdown == null) {
            try {
                // Click on dropdown trigger using user-provided xpath: //*[@id="radix-:r1q:"]/div[2]/form/div[1]/div/button
                WebElement dropdownTrigger = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[contains(@id, 'workType') or contains(text(), 'Select')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", dropdownTrigger);
                Thread.sleep(300);
                js.executeScript("arguments[0].click();", dropdownTrigger);
                Thread.sleep(800);

                // Select the option using user-provided xpath or flexible alternative
                WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//*[contains(text(), 'Working out of the Kalvium environment') and contains(text(), 'Classroom')]")));
                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", option);
                Thread.sleep(300);
                js.executeScript("arguments[0].click();", option);
                Thread.sleep(500);
                addStep(automationSteps, "Selected custom dropdown option");
            } catch (Exception e) {
                addStep(automationSteps, "Warning: Could not interact with custom dropdown - " + e.getMessage());
            }
        }

        // Get form content from config
        String tasksContent = config.getTasksCompleted() != null ? config.getTasksCompleted() : "Completed assigned tasks";
        String challengesContent = config.getChallenges() != null ? config.getChallenges() : "NA";
        String blockersContent = config.getBlockers() != null ? config.getBlockers() : "NA";

        addStep(automationSteps, "Looking for contenteditable worklog field...");

        WebElement worklogField = null;
        try {
            worklogField = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//div[@contenteditable='true']")));
            addStep(automationSteps, "Found contenteditable field");
        } catch (Exception e) {
            addStep(automationSteps, "ERROR: Could not find worklog field - " + e.getMessage());
            throw e;
        }

        addStep(automationSteps, "Updating worklog content...");
        String originalHtml = (String) js.executeScript("return arguments[0].innerHTML;", worklogField);

        // Update the three list sections
        String updatedHtml = updateAllListSections(originalHtml, tasksContent, challengesContent, blockersContent);

        js.executeScript(
            "arguments[0].innerHTML = arguments[1];" +
            "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
            "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
            worklogField, updatedHtml);

        Thread.sleep(500);
        addStep(automationSteps, "Form filled successfully");
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String updateAllListSections(String html, String tasksContent, String challengesContent, String blockersContent) {
        // Remove template phrases
        String[] templatesToRemove = {
            "Add more tasks",
            "Add any obstacles encountered and how you resolved them",
            "Add any blockers faced"
        };

        String updatedHtml = html;
        for (String template : templatesToRemove) {
            updatedHtml = removeListItemContaining(updatedHtml, template);
        }

        // Update each list section
        updatedHtml = updateNthListFirstItem(updatedHtml, 1, tasksContent);
        updatedHtml = updateNthListFirstItem(updatedHtml, 2, challengesContent);
        updatedHtml = updateNthListFirstItem(updatedHtml, 3, blockersContent);

        return updatedHtml;
    }

    private String updateNthListFirstItem(String html, int ulIndex, String newContent) {
        int ulCount = 0;
        int searchStart = 0;
        int targetUlStart = -1;
        int targetUlEnd = -1;

        while (ulCount < ulIndex) {
            int ulStart = html.indexOf("<ul", searchStart);
            if (ulStart == -1) {
                return html;
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

        int firstLiStart = html.indexOf("<li", targetUlStart);
        if (firstLiStart == -1 || firstLiStart > targetUlEnd) {
            return html;
        }

        int firstLiEnd = html.indexOf("</li>", firstLiStart);
        if (firstLiEnd == -1 || firstLiEnd > targetUlEnd) {
            return html;
        }

        StringBuilder result = new StringBuilder();
        result.append(html.substring(0, firstLiStart));
        result.append("<li class=\"list-item\"><p>")
              .append(escapeHtml(newContent.trim()))
              .append("</p></li>");
        result.append(html.substring(firstLiEnd + 5));

        return result.toString();
    }

    private String removeListItemContaining(String html, String text) {
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

            if (liContent.contains(text)) {
                html = html.substring(0, liStart) + html.substring(liEnd + 5);
                continue;
            }

            searchStart = liEnd + 5;
        }

        return html;
    }
}
