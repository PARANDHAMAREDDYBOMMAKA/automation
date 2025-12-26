package com.kalvium.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.kalvium.model.AuthConfig;

import io.github.bonigarcia.wdm.WebDriverManager;

@Service
public class WorklogService {

    private static final Logger logger = LoggerFactory.getLogger(WorklogService.class);

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
                    "--disable-features=VizDisplayCompositor",
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
                    "--js-flags=--max-old-space-size=200,--max-semi-space-size=1"
            );

            addStep(automationSteps, "Opening Chrome browser...");
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            addStep(automationSteps, "Navigating to kalvium.community...");
            driver.get("https://kalvium.community");
            Thread.sleep(2000);

            addStep(automationSteps, "Injecting authentication cookies...");
            injectCookies(driver, config);
            addStep(automationSteps, "Cookies injected successfully");

            addStep(automationSteps, "Navigating to internships page...");
            driver.get("https://kalvium.community/internships");
            Thread.sleep(3000);
            captureScreenshot(driver, screenshots, "Internships page loaded");

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
            captureScreenshot(driver, screenshots, "Worklog form opened");

            addStep(automationSteps, "Filling out the form...");
            fillForm(driver, wait, config, automationSteps);
            captureScreenshot(driver, screenshots, "Form filled");

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
            captureScreenshot(driver, screenshots, "Final confirmation");
            logger.info("Worklog submitted successfully");
            return buildSuccessResponse(automationSteps, screenshots);

        } catch (Exception e) {
            addStep(automationSteps, "ERROR: " + e.getMessage());
            logger.error("Error: " + e.getMessage(), e);
            if (driver != null) {
                try {
                    captureScreenshot(driver, screenshots, "Error state");
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

            automationSteps.clear();
            screenshots.clear();

            try {
                Thread.sleep(1000);
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

    private String updateListItems(String html, String newContent) {
        int ulStart = html.indexOf("<ul");
        if (ulStart == -1) {
            return html;
        }

        int ulTagEnd = html.indexOf(">", ulStart) + 1;
        int ulEnd = html.indexOf("</ul>", ulStart);

        if (ulEnd == -1) {
            return html;
        }

        String ulTag = html.substring(ulStart, ulTagEnd);

        StringBuilder result = new StringBuilder();
        result.append(html.substring(0, ulStart));
        result.append(ulTag);

        String[] lines = newContent.split("\\r?\\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (!trimmedLine.isEmpty()) {
                result.append("<li class=\"list-item\"><p>")
                      .append(escapeHtml(trimmedLine))
                      .append("</p></li>");
            }
        }

        result.append(html.substring(ulEnd));

        return result.toString();
    }

    @SuppressWarnings("UseSpecificCatch")
    private void captureScreenshot(WebDriver driver, List<Screenshot> screenshots, String description) {
        try {
            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            String base64Screenshot = Base64.getEncoder().encodeToString(screenshotBytes);
            screenshots.add(new Screenshot(description, base64Screenshot));
            logger.info("Screenshot captured: " + description);
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

        String tasksContent = config.getTasksCompleted() != null ? config.getTasksCompleted() : "Need to complete the tasks assigned.";
        String challengesContent = config.getChallenges() != null ? config.getChallenges() : "NA";
        String blockersContent = config.getBlockers() != null ? config.getBlockers() : "NA";

        addStep(automationSteps, "Filling 'Tasks completed today' field...");
        WebElement tasksField = wait.until(ExpectedConditions.presenceOfElementLocated(
            By.xpath("//*[contains(text(), '📋 Tasks completed today')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true'] | " +
                     "//*[contains(text(), 'Tasks completed today')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true']")
        ));
        String tasksHtml = (String) js.executeScript("return arguments[0].innerHTML;", tasksField);
        String updatedTasksHtml = updateListItems(tasksHtml, tasksContent);
        js.executeScript("arguments[0].innerHTML = arguments[1]; arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", tasksField, updatedTasksHtml);
        addStep(automationSteps, "Tasks field HTML after update: " + updatedTasksHtml);
        Thread.sleep(500);

        addStep(automationSteps, "Filling 'Challenges encountered' field...");
        WebElement challengesField = wait.until(ExpectedConditions.presenceOfElementLocated(
            By.xpath("//*[contains(text(), '⚡ Challenges encountered')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true'] | " +
                     "//*[contains(text(), 'Challenges encountered')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true']")
        ));
        String challengesHtml = (String) js.executeScript("return arguments[0].innerHTML;", challengesField);
        String updatedChallengesHtml = updateListItems(challengesHtml, challengesContent);
        js.executeScript("arguments[0].innerHTML = arguments[1]; arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", challengesField, updatedChallengesHtml);
        addStep(automationSteps, "Challenges field HTML after update: " + updatedChallengesHtml);
        Thread.sleep(500);

        addStep(automationSteps, "Filling 'Blockers faced' field...");
        WebElement blockersField = wait.until(ExpectedConditions.presenceOfElementLocated(
            By.xpath("//*[contains(text(), '🚧 Blockers faced')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true'] | " +
                     "//*[contains(text(), 'Blockers faced')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true']")
        ));
        String blockersHtml = (String) js.executeScript("return arguments[0].innerHTML;", blockersField);
        String updatedBlockersHtml = updateListItems(blockersHtml, blockersContent);
        js.executeScript("arguments[0].innerHTML = arguments[1]; arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", blockersField, updatedBlockersHtml);
        addStep(automationSteps, "Blockers field HTML after update: " + updatedBlockersHtml);
        Thread.sleep(500);

        addStep(automationSteps, "All form fields filled");
    }

    @SuppressWarnings("UseSpecificCatch")
    private void clearAndFillField(JavascriptExecutor js, WebElement field, String htmlContent) throws InterruptedException {
        try {
            js.executeScript("arguments[0].innerHTML = '';", field);
            js.executeScript("arguments[0].textContent = '';", field);
            js.executeScript("arguments[0].innerText = '';", field);

            js.executeScript(
                "while (arguments[0].firstChild) {" +
                "  arguments[0].removeChild(arguments[0].firstChild);" +
                "}", field);

            Thread.sleep(300);

            js.executeScript("arguments[0].innerHTML = arguments[1];", field, htmlContent);

            js.executeScript(
                "var element = arguments[0];" +
                "element.dispatchEvent(new Event('input', { bubbles: true }));" +
                "element.dispatchEvent(new Event('change', { bubbles: true }));" +
                "element.dispatchEvent(new Event('blur', { bubbles: true }));",
                field);

            Thread.sleep(300);
        } catch (Exception e) {
            logger.warn("Error in clearAndFillField: " + e.getMessage());
            throw e;
        }
    }

    @SuppressWarnings({"UseSpecificCatch", "unused"})
    private void fillFieldByLabel(WebDriver driver, JavascriptExecutor js, String labelText, String htmlContent, List<String> automationSteps) {
        try {
            addStep(automationSteps, "Looking for field with label: " + labelText);

            WebElement field = null;

            String[] xpaths = {
                "//*[contains(text(), '" + labelText + "')]/following::div[@contenteditable='true'][1]",
                "//*[contains(text(), '" + labelText + "')]/following::textarea[1]",
                "//*[contains(text(), '" + labelText + "')]/..//div[@contenteditable='true']",
                "//*[contains(text(), '" + labelText + "')]//following-sibling::div[@contenteditable='true']",
                "//div[contains(., '" + labelText + "')]/following::div[@contenteditable='true'][1]"
            };

            for (String xpath : xpaths) {
                try {
                    field = driver.findElement(By.xpath(xpath));
                    addStep(automationSteps, "Found field using xpath strategy");
                    break;
                } catch (NoSuchElementException e) {
                }
            }

            if (field != null) {
                clearAndFillField(js, field, htmlContent);
                addStep(automationSteps, "Successfully filled field: " + labelText);
            } else {
                addStep(automationSteps, "ERROR: Could not find field for: " + labelText);
            }

        } catch (Exception e) {
            addStep(automationSteps, "ERROR filling field '" + labelText + "': " + e.getMessage());
            logger.warn("Could not fill field '" + labelText + "': " + e.getMessage());
        }
    }

    @SuppressWarnings({"UseSpecificCatch", "unused"})
    private void fillFieldByLabelWithUpdate(WebDriver driver, JavascriptExecutor js, String labelText, String content, List<String> automationSteps) {
        try {
            addStep(automationSteps, "Looking for field with label: " + labelText);

            WebElement field = null;

            String[] xpaths = {
                "//*[contains(text(), '" + labelText + "')]/following::div[@contenteditable='true'][1]",
                "//*[contains(text(), '" + labelText + "')]/following::textarea[1]",
                "//*[contains(text(), '" + labelText + "')]/..//div[@contenteditable='true']",
                "//*[contains(text(), '" + labelText + "')]//following-sibling::div[@contenteditable='true']",
                "//div[contains(., '" + labelText + "')]/following::div[@contenteditable='true'][1]"
            };

            for (String xpath : xpaths) {
                try {
                    field = driver.findElement(By.xpath(xpath));
                    addStep(automationSteps, "Found field using xpath strategy");
                    break;
                } catch (NoSuchElementException e) {
                }
            }

            if (field != null) {
                String existingHtml = (String) js.executeScript("return arguments[0].innerHTML;", field);
                String updatedHtml = updateListItems(existingHtml, content);
                js.executeScript("arguments[0].innerHTML = arguments[1]; arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", field, updatedHtml);
                addStep(automationSteps, "Successfully filled field: " + labelText);
            } else {
                addStep(automationSteps, "ERROR: Could not find field for: " + labelText);
            }

        } catch (Exception e) {
            addStep(automationSteps, "ERROR filling field '" + labelText + "': " + e.getMessage());
            logger.warn("Could not fill field '" + labelText + "': " + e.getMessage());
        }
    }

}
