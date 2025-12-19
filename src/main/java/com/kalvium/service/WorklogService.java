package com.kalvium.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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

    public String submitWorklog(AuthConfig config) {
        WebDriver driver = null;
        // Use local variables to prevent memory leaks across concurrent requests
        List<String> automationSteps = new ArrayList<>();

        try {
            if (config == null || config.getAuthSessionId() == null) {
                return "ERROR: No configuration provided.";
            }

            addStep(automationSteps, "Setting up ChromeDriver...");
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            // Running in headless mode for production with aggressive memory optimizations
            options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                    "--disable-blink-features=AutomationControlled", "--window-size=1280,720",
                    "--disable-gpu", "--disable-extensions",
                    "--disable-background-networking", "--disable-default-apps",
                    "--disable-sync", "--metrics-recording-only",
                    "--mute-audio", "--no-first-run",
                    "--safebrowsing-disable-auto-update",
                    "--disable-client-side-phishing-detection",
                    "--disable-component-extensions-with-background-pages",
                    "--disable-features=VizDisplayCompositor",
                    "--js-flags=--max-old-space-size=256");

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

            addStep(automationSteps, "Checking Pending worklogs section...");
            // Scroll to My Worklogs section first
            JavascriptExecutor js = (JavascriptExecutor) driver;
            WebElement worklogsSection = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), 'My Worklogs')]")));
            js.executeScript("arguments[0].scrollIntoView(true);", worklogsSection);
            Thread.sleep(1000);

            // Try to find and expand Pending section - try multiple approaches
            boolean expanded = false;
            try {
                // Approach 1: Click parent element containing Pending text
                WebElement pendingSection = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//*[text()='Pending']/parent::*")));
                addStep(automationSteps, "Found Pending section parent element");

                // Check if expanded by looking for visible table
                try {
                    WebElement table = driver.findElement(By.xpath("//*[text()='Date' and following-sibling::*[text()='Submit By']]"));
                    if (table.isDisplayed()) {
                        expanded = true;
                        addStep(automationSteps, "Pending section already expanded");
                    }
                } catch (Exception ignored) {}

                if (!expanded) {
                    addStep(automationSteps, "Attempting to expand Pending section...");
                    // Try clicking the parent
                    try {
                        js.executeScript("arguments[0].click();", pendingSection);
                        Thread.sleep(1500);
                        addStep(automationSteps, "Clicked Pending parent");
                    } catch (Exception e1) {
                        // Try clicking the text itself
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

            addStep(automationSteps, "Filling out the form...");
            fillForm(driver, wait, config, automationSteps);

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
            logger.info("Worklog submitted successfully");
            return buildSuccessResponse(automationSteps);

        } catch (Exception e) {
            addStep(automationSteps, "ERROR: " + e.getMessage());
            logger.error("Error: " + e.getMessage(), e);
            return buildErrorResponse(automationSteps, e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    addStep(automationSteps, "Browser closed");
                } catch (Exception e) {
                    logger.error("Error closing driver: " + e.getMessage());
                }
            }
            // Clear the list to help GC - list is local so will be GC'd anyway
            automationSteps.clear();
        }
    }

    private void addStep(List<String> steps, String step) {
        steps.add(step);
        logger.info(step);
    }

    private String buildSuccessResponse(List<String> automationSteps) {
        StringBuilder response = new StringBuilder("SUCCESS: Worklog submitted!\n\n");
        response.append("STEPS:\n");
        for (String step : automationSteps) {
            response.append(step).append("\n");
        }
        return response.toString();
    }

    private String buildErrorResponse(List<String> automationSteps, String error) {
        StringBuilder response = new StringBuilder("ERROR: ").append(error).append("\n\n");
        response.append("STEPS:\n");
        for (String step : automationSteps) {
            response.append(step).append("\n");
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

    private WebElement findPendingWorklog(WebDriver driver, WebDriverWait wait) {
        try {
            // Look for "Complete" text/button that appears AFTER "Pending" header but BEFORE "Completed" header
            // This ensures we're in the Pending section, not Completed section
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//*[text()='Pending']/following::*[text()='Complete'][1]")));
        } catch (Exception e1) {
            try {
                // Try finding in table structure under Pending
                String today = java.time.LocalDate.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
                return wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//table//td[contains(text(), '" + today + "')]/..//td[text()='Complete']")));
            } catch (Exception e2) {
                // Try a more general approach - find Complete in a table row
                return wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//tr//td[text()='Complete'] | //tr//*[text()='Complete']")));
            }
        }
    }

    private void fillForm(WebDriver driver, WebDriverWait wait, AuthConfig config, List<String> automationSteps) throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Handle dropdown - use JavaScript and multiple approaches
        addStep(automationSteps, "Looking for work status dropdown...");
        boolean dropdownSelected = false;

        // Approach 1: Try native select element
        try {
            WebElement dropdown = driver.findElement(By.xpath("//select"));
            js.executeScript("arguments[0].scrollIntoView(true);", dropdown);
            Thread.sleep(500);

            // Use JavaScript to select the option
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

        // Approach 2: Try custom dropdown
        if (!dropdownSelected) {
            try {
                // Find the dropdown container
                WebElement dropdownContainer = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//*[contains(text(), 'What is your work status')]/following::*[contains(text(), 'Select your response') or contains(@class, 'select')]")));
                js.executeScript("arguments[0].scrollIntoView(true);", dropdownContainer);
                Thread.sleep(300);
                js.executeScript("arguments[0].click();", dropdownContainer);
                Thread.sleep(1000);

                // Find and click the option
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

        // Handle rich text editor fields - find all contenteditable divs in order
        addStep(automationSteps, "Finding all text input fields...");
        List<WebElement> textFields = driver.findElements(By.xpath("//div[@contenteditable='true']"));
        addStep(automationSteps, "Found " + textFields.size() + " contenteditable fields");

        if (textFields.size() >= 3) {
            addStep(automationSteps, "Filling 'Tasks completed' field (field 1/3)...");
            WebElement tasksField = textFields.get(0);
            js.executeScript("arguments[0].innerHTML = '';", tasksField);
            Thread.sleep(200);
            String tasksHtml = "<p><strong>Tasks completed today</strong></p><ul><li>" +
                    (config.getTasksCompleted() != null ? config.getTasksCompleted() : "Need to complete the tasks assigned.") +
                    "</li></ul>";
            js.executeScript("arguments[0].innerHTML = arguments[1];", tasksField, tasksHtml);
            Thread.sleep(300);

            addStep(automationSteps, "Filling 'Challenges' field (field 2/3)...");
            WebElement challengesField = textFields.get(1);
            js.executeScript("arguments[0].innerHTML = '';", challengesField);
            Thread.sleep(200);
            String challengesHtml = "<p><strong>Challenges encountered and how you overcame them</strong></p><ul><li>" +
                    (config.getChallenges() != null ? config.getChallenges() : "NA") +
                    "</li></ul>";
            js.executeScript("arguments[0].innerHTML = arguments[1];", challengesField, challengesHtml);
            Thread.sleep(300);

            addStep(automationSteps, "Filling 'Blockers' field (field 3/3)...");
            WebElement blockersField = textFields.get(2);
            js.executeScript("arguments[0].innerHTML = '';", blockersField);
            Thread.sleep(200);
            String blockersHtml = "<p><strong>Blockers faced (challenges that you couldn't overcome)</strong></p><ul><li>" +
                    (config.getBlockers() != null ? config.getBlockers() : "NA") +
                    "</li></ul>";
            js.executeScript("arguments[0].innerHTML = arguments[1];", blockersField, blockersHtml);
            Thread.sleep(300);
        } else {
            addStep(automationSteps, "WARNING: Expected 3 fields but found " + textFields.size());
            // Fallback to old method if we don't find exactly 3 fields
            String tasksHtml = "<p><strong>Tasks completed today</strong></p><ul><li>" +
                    (config.getTasksCompleted() != null ? config.getTasksCompleted() : "Need to complete the tasks assigned.") +
                    "</li></ul>";
            String challengesHtml = "<p><strong>Challenges encountered and how you overcame them</strong></p><ul><li>" +
                    (config.getChallenges() != null ? config.getChallenges() : "NA") +
                    "</li></ul>";
            String blockersHtml = "<p><strong>Blockers faced (challenges that you couldn't overcome)</strong></p><ul><li>" +
                    (config.getBlockers() != null ? config.getBlockers() : "NA") +
                    "</li></ul>";

            fillRichTextField(driver, "Tasks", tasksHtml);
            fillRichTextField(driver, "Challenges", challengesHtml);
            fillRichTextField(driver, "Blockers", blockersHtml);
        }

        addStep(automationSteps, "All form fields filled");
    }

    @SuppressWarnings("UseSpecificCatch")
    private void fillRichTextField(WebDriver driver, String fieldLabel, String text) {
        try {
            WebElement field;

            // Try 1: Find contenteditable div
            try {
                field = driver.findElement(By.xpath(
                        "//*[contains(text(), '" + fieldLabel + "')]/following::*[@contenteditable='true'][1]"));
            } catch (NoSuchElementException e1) {
                // Try 2: Find textarea
                try {
                    field = driver.findElement(By.xpath(
                            "//*[contains(text(), '" + fieldLabel + "')]/following::textarea[1]"));
                } catch (NoSuchElementException e2) {
                    // Try 3: Find any input-like element
                    field = driver.findElement(By.xpath(
                            "//*[contains(text(), '" + fieldLabel + "')]/following::*[self::textarea or self::input or @contenteditable][1]"));
                }
            }

            // Clear existing content
            try {
                field.clear();
            } catch (Exception ignored) {
                // Some rich text editors don't support clear()
            }

            // Use JavaScript to set innerHTML for rich text editors
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // Clear any existing content first
            js.executeScript("arguments[0].innerHTML = '';", field);
            Thread.sleep(200);

            // Set the new content (text already contains HTML formatting)
            js.executeScript("arguments[0].innerHTML = arguments[1];", field, text);

        } catch (Exception e) {
            logger.warn("Could not fill field '" + fieldLabel + "': " + e.getMessage());
        }
    }
}
