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
import com.kalvium.util.XPathLoader;

import io.github.bonigarcia.wdm.WebDriverManager;

@Service
public class WorklogService {

    private static final Logger logger = LoggerFactory.getLogger(WorklogService.class);
    private static final int MAX_NAVIGATION_RETRIES = 3;
    private static final int PAGE_LOAD_TIMEOUT_SECONDS = 45;
    private static final int ELEMENT_WAIT_TIMEOUT_SECONDS = 30;

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
            Thread.sleep(3000);

            addStep(automationSteps, "Setting up ChromeDriver...");
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = createOptimizedChromeOptions();

            addStep(automationSteps, "Opening Chrome browser...");
            driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SECONDS));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(ELEMENT_WAIT_TIMEOUT_SECONDS));
            JavascriptExecutor js = (JavascriptExecutor) driver;

            addStep(automationSteps, "Navigating to kalvium.community with retry logic...");
            navigateWithRetry(driver, js, "https://kalvium.community", automationSteps);

            Thread.sleep(2000);
            addStep(automationSteps, "Page loaded successfully, current URL: " + driver.getCurrentUrl());

            addStep(automationSteps, "Injecting authentication cookies...");
            injectCookies(driver, config);
            addStep(automationSteps, "Cookies injected successfully");

            addStep(automationSteps, "Navigating to internships page...");
            navigateWithRetry(driver, js, "https://kalvium.community/internships", automationSteps);

            Thread.sleep(3000);

            addStep(automationSteps, "Waiting for table to load...");
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath(XPathLoader.get("table.main"))));
                Thread.sleep(2000);
                addStep(automationSteps, "Table found on page");
            } catch (Exception e) {
                addStep(automationSteps, "Warning: Table not found, but continuing...");
            }

            captureScreenshot(driver, screenshots, "Internships page loaded", config.getAuthSessionId());

            addStep(automationSteps, "Looking for pending worklog button using XPath...");

            try {
                List<WebElement> tableRows = driver.findElements(
                    By.xpath(XPathLoader.get("table.rows")));
                addStep(automationSteps, "Found " + tableRows.size() + " row(s) in table");

                if (tableRows.isEmpty()) {
                    addStep(automationSteps, "No rows found in table - possibly no pending worklogs");
                    return "SUCCESS: No pending worklogs found to submit.";
                }
            } catch (Exception e) {
                addStep(automationSteps, "Warning: Could not check table rows - " + e.getMessage());
            }

            WebElement completeButton = findCompleteButton(wait, automationSteps);

            addStep(automationSteps, "Clicking Complete button...");
            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", completeButton);
            Thread.sleep(500);
            js.executeScript("arguments[0].click();", completeButton);
            Thread.sleep(3000);

            addStep(automationSteps, "Waiting for worklog form to appear...");
            try {
                wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath(XPathLoader.get("form.heading.worklog"))));
                Thread.sleep(2000);
                addStep(automationSteps, "Worklog form heading found");
            } catch (Exception e) {
                addStep(automationSteps, "Warning: Worklog heading not found, checking for form elements...");
                wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath(XPathLoader.get("form.main"))));
                Thread.sleep(2000);
                addStep(automationSteps, "Form detected");
            }
            captureScreenshot(driver, screenshots, "Worklog form opened", config.getAuthSessionId());

            addStep(automationSteps, "Filling out the form using XPath locators...");
            fillFormWithXPaths(wait, js, config, automationSteps);
            Thread.sleep(1000);
            captureScreenshot(driver, screenshots, "Form filled", config.getAuthSessionId());

            addStep(automationSteps, "Submitting the form...");
            WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath(XPathLoader.get("button.submit"))));
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

    private ChromeOptions createOptimizedChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-setuid-sandbox",
                "--disable-dev-shm-usage",
                "--disable-blink-features=AutomationControlled",
                "--window-size=1920,1080",
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
                "--disable-web-security",
                "--remote-allow-origins=*",
                "--disable-plugins",
                "--disable-background-timer-throttling",
                "--disable-backgrounding-occluded-windows",
                "--disable-renderer-backgrounding",
                "--disable-client-side-phishing-detection",
                "--disable-hang-monitor",
                "--disable-prompt-on-repost",
                "--disable-domain-reliability",
                "--disable-component-update",
                "--disable-features=TranslateUI,BlinkGenPropertyTrees",
                "--enable-features=NetworkService,NetworkServiceInProcess"
        );
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);
        options.setAcceptInsecureCerts(true);

        return options;
    }

    private void navigateWithRetry(WebDriver driver, JavascriptExecutor js, String url, List<String> automationSteps) throws InterruptedException {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_NAVIGATION_RETRIES) {
            try {
                addStep(automationSteps, "Navigation attempt " + (retryCount + 1) + "/" + MAX_NAVIGATION_RETRIES + " to " + url);

                driver.get(url);

                WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(10));
                shortWait.until(webDriver -> {
                    String readyState = js.executeScript("return document.readyState").toString();
                    return "interactive".equals(readyState) || "complete".equals(readyState);
                });

                addStep(automationSteps, "Navigation successful on attempt " + (retryCount + 1));
                return;

            } catch (Exception e) {
                lastException = e;
                retryCount++;
                addStep(automationSteps, "Navigation attempt " + retryCount + " failed: " + e.getMessage());

                if (retryCount < MAX_NAVIGATION_RETRIES) {
                    addStep(automationSteps, "Waiting 5 seconds before retry...");
                    Thread.sleep(5000);

                    try {
                        js.executeScript("window.stop();");
                    } catch (Exception stopEx) {
                        logger.warn("Could not stop page load: " + stopEx.getMessage());
                    }
                }
            }
        }

        throw new RuntimeException("Failed to navigate to " + url + " after " + MAX_NAVIGATION_RETRIES + " attempts", lastException);
    }

    private WebElement findCompleteButton(WebDriverWait wait, List<String> automationSteps) {
        String[] buttonXPaths = {
            XPathLoader.get("table.complete.button.primary"),
            XPathLoader.get("table.complete.button.text"),
            XPathLoader.get("table.complete.button.position"),
            XPathLoader.get("table.complete.button.first"),
            XPathLoader.get("table.complete.button.any")
        };

        String[] buttonDescriptions = {
            "primary xpath (radix pattern)",
            "text-based xpath",
            "position-based xpath (td[3])",
            "first button xpath",
            "any button xpath"
        };

        for (int i = 0; i < buttonXPaths.length; i++) {
            try {
                WebElement button = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath(buttonXPaths[i])));
                addStep(automationSteps, "Found button using " + buttonDescriptions[i]);
                return button;
            } catch (Exception e) {
                addStep(automationSteps, "Button not found with " + buttonDescriptions[i] + ", trying next...");
            }
        }

        throw new RuntimeException("Could not find Complete button with any XPath strategy");
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
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            WebDriverWait cookieWait = new WebDriverWait(driver, Duration.ofSeconds(10));
            cookieWait.until(webDriver -> {
                String readyState = js.executeScript("return document.readyState").toString();
                return "interactive".equals(readyState) || "complete".equals(readyState);
            });
            logger.info("Page ready for cookie injection (readyState: {})",
                js.executeScript("return document.readyState"));
        } catch (Exception e) {
            logger.warn("Could not verify page readyState, proceeding anyway: {}", e.getMessage());
        }

        driver.manage().deleteAllCookies();

        String authSessionId = config.getAuthSessionId();
        String keycloakIdentity = config.getKeycloakIdentity();
        String keycloakSession = config.getKeycloakSession();

        if (authSessionId != null && authSessionId.contains(".")) {
            String[] parts = authSessionId.split("\\.", 2);
            authSessionId = parts[0];
            logger.info("Sanitized AUTH_SESSION_ID from {} to {}", config.getAuthSessionId(), authSessionId);
        }

        if (keycloakIdentity != null && keycloakIdentity.contains(".") && keycloakIdentity.split("\\.").length > 5) {
            String[] parts = keycloakIdentity.split("\\.", 2);
            keycloakIdentity = parts[0];
        }

        if (keycloakSession != null && keycloakSession.contains(".") && keycloakSession.split("\\.").length > 5) {
            String[] parts = keycloakSession.split("\\.", 2);
            keycloakSession = parts[0];
        }

        addCookieWithRetry(driver, "AUTH_SESSION_ID", authSessionId);
        addCookieWithRetry(driver, "AUTH_SESSION_ID_LEGACY", authSessionId);
        addCookieWithRetry(driver, "KEYCLOAK_IDENTITY", keycloakIdentity);
        addCookieWithRetry(driver, "KEYCLOAK_IDENTITY_LEGACY", keycloakIdentity);
        addCookieWithRetry(driver, "KEYCLOAK_SESSION", keycloakSession);
        addCookieWithRetry(driver, "KEYCLOAK_SESSION_LEGACY", keycloakSession);

        int cookiesSet = driver.manage().getCookies().size();
        logger.info("Cookies injected successfully for user ({} cookies total)", cookiesSet);
    }

    private void addCookieWithRetry(WebDriver driver, String name, String value) {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                driver.manage().addCookie(new Cookie.Builder(name, value)
                    .domain(".kalvium.community").path("/").isSecure(true).build());
                logger.debug("Cookie {} added successfully", name);
                return;
            } catch (Exception e) {
                retryCount++;
                logger.warn("Failed to add cookie {} (attempt {}/{}): {}",
                    name, retryCount, maxRetries, e.getMessage());

                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while adding cookie " + name, ie);
                    }
                } else {
                    throw new RuntimeException("Failed to add cookie " + name + " after " + maxRetries + " attempts", e);
                }
            }
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    private void fillFormWithXPaths(WebDriverWait wait, JavascriptExecutor js,
                                    AuthConfig config, List<String> automationSteps) throws InterruptedException {

        addStep(automationSteps, "Looking for work status dropdown using XPaths...");

        boolean dropdownSelected = selectDropdown(wait, js, automationSteps);

        if (!dropdownSelected) {
            addStep(automationSteps, "WARNING: Dropdown selection failed - editor may not appear!");
        } else {
            addStep(automationSteps, "Dropdown successfully selected, waiting for editor to load...");
        }

        String tasksContent = config.getTasksCompleted() != null ? config.getTasksCompleted() : "Completed assigned tasks";
        String challengesContent = config.getChallenges() != null ? config.getChallenges() : "NA";
        String blockersContent = config.getBlockers() != null ? config.getBlockers() : "NA";

        addStep(automationSteps, "Looking for contenteditable worklog field using XPaths...");

        WebElement worklogField = findWorklogEditor(wait, automationSteps, dropdownSelected);

        addStep(automationSteps, "Updating worklog content...");
        String originalHtml = (String) js.executeScript("return arguments[0].innerHTML;", worklogField);

        String updatedHtml = updateAllListSections(originalHtml, tasksContent, challengesContent, blockersContent);

        js.executeScript(
            "arguments[0].innerHTML = arguments[1];" +
            "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
            "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));",
            worklogField, updatedHtml);

        Thread.sleep(500);
        addStep(automationSteps, "Form filled successfully");
    }

    private boolean selectDropdown(WebDriverWait wait, JavascriptExecutor js, List<String> automationSteps) throws InterruptedException {
        try {
            WebElement selectElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath(XPathLoader.get("dropdown.select.main"))));
            addStep(automationSteps, "Found select element using dropdown.select.main XPath");

            js.executeScript("arguments[0].scrollIntoView({block: 'center'});", selectElement);
            Thread.sleep(500);

            js.executeScript(
                "var select = arguments[0];" +
                "select.selectedIndex = 0;" +
                "select.dispatchEvent(new Event('change', { bubbles: true }));" +
                "select.dispatchEvent(new Event('input', { bubbles: true }));",
                selectElement);

            addStep(automationSteps, "Selected option[0] from dropdown using XPath");
            Thread.sleep(2000);
            return true;
        } catch (Exception e1) {
            addStep(automationSteps, "Select element not found, trying button approach: " + e1.getMessage());

            try {
                WebElement dropdownButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath(XPathLoader.get("dropdown.button.main"))));
                addStep(automationSteps, "Found dropdown button using dropdown.button.main XPath");

                js.executeScript("arguments[0].scrollIntoView({block: 'center'});", dropdownButton);
                Thread.sleep(500);

                js.executeScript("arguments[0].click();", dropdownButton);
                addStep(automationSteps, "Clicked dropdown button");
                Thread.sleep(1000);

                try {
                    WebElement selectElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                            By.xpath(XPathLoader.get("dropdown.select.main"))));
                    js.executeScript(
                        "var select = arguments[0];" +
                        "select.selectedIndex = 0;" +
                        "select.dispatchEvent(new Event('change', { bubbles: true }));" +
                        "select.dispatchEvent(new Event('input', { bubbles: true }));",
                        selectElement);
                    addStep(automationSteps, "Selected option after clicking button");
                    Thread.sleep(2000);
                    return true;
                } catch (Exception e) {
                    WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                            By.xpath(XPathLoader.get("dropdown.option.first"))));
                    js.executeScript("arguments[0].selected = true; arguments[0].parentElement.dispatchEvent(new Event('change', { bubbles: true }));", option);
                    addStep(automationSteps, "Clicked option directly using dropdown.option.first XPath");
                    Thread.sleep(2000);
                    return true;
                }
            } catch (Exception e2) {
                addStep(automationSteps, "All dropdown XPath strategies failed: " + e2.getMessage());
                return false;
            }
        }
    }

    private WebElement findWorklogEditor(WebDriverWait wait, List<String> automationSteps, boolean dropdownSelected) throws InterruptedException {
        Thread.sleep(2000);

        String[] editorXPaths = {
            XPathLoader.get("editor.radix.pattern"),
            XPathLoader.get("editor.contenteditable.main"),
            XPathLoader.get("editor.tiptap"),
            XPathLoader.get("editor.contenteditable.form"),
            XPathLoader.get("editor.any.contenteditable")
        };

        String[] editorDescriptions = {
            "editor.radix.pattern",
            "editor.contenteditable.main",
            "editor.tiptap",
            "editor.contenteditable.form",
            "editor.any.contenteditable"
        };

        for (int i = 0; i < editorXPaths.length; i++) {
            try {
                WebElement editor = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath(editorXPaths[i])));
                addStep(automationSteps, "Found editor using " + editorDescriptions[i] + " XPath");
                return editor;
            } catch (Exception e) {
                addStep(automationSteps, "Editor not found with " + editorDescriptions[i] + ", trying next...");
            }
        }

        String errorMsg = "Could not find worklog field after " +
                         (dropdownSelected ? "dropdown was selected" : "dropdown selection failed");
        addStep(automationSteps, "ERROR: " + errorMsg);
        throw new RuntimeException(errorMsg);
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String updateAllListSections(String html, String tasksContent, String challengesContent, String blockersContent) {
        String[] templatesToRemove = {
            "Add more tasks",
            "Add any obstacles encountered and how you resolved them",
            "Add any blockers faced"
        };

        String updatedHtml = html;
        for (String template : templatesToRemove) {
            updatedHtml = removeListItemContaining(updatedHtml, template);
        }

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
