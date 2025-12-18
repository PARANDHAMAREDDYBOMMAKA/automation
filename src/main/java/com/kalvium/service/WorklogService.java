package com.kalvium.service;

import com.kalvium.model.AuthConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class WorklogService {

    private static final Logger logger = LoggerFactory.getLogger(WorklogService.class);
    private final List<String> automationSteps = new ArrayList<>();
    private final List<String> screenshots = new ArrayList<>();

    public String submitWorklog(AuthConfig config) {
        WebDriver driver = null;
        automationSteps.clear();
        screenshots.clear();

        try {
            if (config == null || config.getAuthSessionId() == null) {
                return "ERROR: No configuration provided.";
            }

            addStep("Setting up ChromeDriver...");
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage",
                    "--disable-blink-features=AutomationControlled", "--start-maximized", "--window-size=1920,1080");

            addStep("Opening Chrome browser...");
            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            addStep("Navigating to kalvium.community...");
            driver.get("https://kalvium.community");
            Thread.sleep(2000);
            takeScreenshot(driver, "Kalvium homepage");

            addStep("Injecting authentication cookies...");
            injectCookies(driver, config);
            addStep("Cookies injected successfully");

            addStep("Navigating to internships page...");
            driver.get("https://kalvium.community/internships");
            Thread.sleep(3000);
            takeScreenshot(driver, "Internships page");

            addStep("Looking for pending worklog...");
            WebElement completeButton = findPendingWorklog(driver, wait);
            addStep("Found pending worklog button");

            addStep("Clicking Complete button...");
            completeButton.click();
            Thread.sleep(3000);
            takeScreenshot(driver, "After clicking Complete");

            addStep("Waiting for worklog form to appear...");
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), 'My Worklog')]")));
            addStep("Form appeared");
            Thread.sleep(2000);
            takeScreenshot(driver, "Worklog form");

            addStep("Filling out the form...");
            fillForm(driver, wait, config);
            takeScreenshot(driver, "Form filled");

            addStep("Submitting the form...");
            WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(), 'Submit')] | //button[@type='submit']")));
            submitButton.click();
            Thread.sleep(3000);
            takeScreenshot(driver, "After submission");

            addStep("Worklog submitted successfully!");
            logger.info("Worklog submitted successfully");
            return buildSuccessResponse();

        } catch (Exception e) {
            addStep("ERROR: " + e.getMessage());
            logger.error("Error: " + e.getMessage(), e);
            if (driver != null) {
                try {
                    takeScreenshot(driver, "Error state");
                } catch (Exception ignored) {}
            }
            return buildErrorResponse(e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
                addStep("Browser closed");
            }
        }
    }

    private void addStep(String step) {
        automationSteps.add(step);
        logger.info(step);
    }

    private void takeScreenshot(WebDriver driver, String description) {
        try {
            TakesScreenshot screenshot = (TakesScreenshot) driver;
            String base64 = screenshot.getScreenshotAs(OutputType.BASE64);
            screenshots.add("data:image/png;base64," + base64);
            addStep("Screenshot: " + description);
        } catch (Exception e) {
            logger.warn("Failed to take screenshot: " + e.getMessage());
        }
    }

    private String buildSuccessResponse() {
        StringBuilder response = new StringBuilder("SUCCESS: Worklog submitted!\n\n");
        response.append("STEPS:\n");
        for (String step : automationSteps) {
            response.append(step).append("\n");
        }
        response.append("\nSCREENSHOTS:").append(screenshots.size());
        for (int i = 0; i < screenshots.size(); i++) {
            response.append("\n[IMG]").append(i).append("[/IMG]").append(screenshots.get(i));
        }
        return response.toString();
    }

    private String buildErrorResponse(String error) {
        StringBuilder response = new StringBuilder("ERROR: ").append(error).append("\n\n");
        response.append("STEPS:\n");
        for (String step : automationSteps) {
            response.append(step).append("\n");
        }
        if (!screenshots.isEmpty()) {
            response.append("\nSCREENSHOTS:").append(screenshots.size());
            for (int i = 0; i < screenshots.size(); i++) {
                response.append("\n[IMG]").append(i).append("[/IMG]").append(screenshots.get(i));
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

    private WebElement findPendingWorklog(WebDriver driver, WebDriverWait wait) {
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//td[contains(text(), '-')]/..//button | //button[contains(text(), 'Complete')]")));
        } catch (Exception e) {
            String today = java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy"));
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//td[contains(text(), '" + today + "')]/..//button")));
        }
    }

    private void fillForm(WebDriver driver, WebDriverWait wait, AuthConfig config) throws InterruptedException {
        WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(30));

        addStep("Looking for dropdown...");
        WebElement dropdown = longWait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//select | //div[contains(@class, 'select')]//select")));
        addStep("Dropdown found, clicking...");
        dropdown.click();
        Thread.sleep(1000);

        addStep("Selecting work status option...");
        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//option[contains(text(), 'Working out of the Kalvium environment')]"))).click();
        Thread.sleep(500);

        addStep("Filling 'Tasks completed' field...");
        driver.findElement(By.xpath("//*[contains(text(), 'Tasks completed today')]/following::textarea[1]"))
                .sendKeys(config.getTasksCompleted() != null ? config.getTasksCompleted() : "Tasks completed");

        addStep("Filling 'Challenges' field...");
        driver.findElement(By.xpath("//*[contains(text(), 'Challenges encountered')]/following::textarea[1]"))
                .sendKeys(config.getChallenges() != null ? config.getChallenges() : "NA");

        addStep("Filling 'Blockers' field...");
        driver.findElement(By.xpath("//*[contains(text(), 'Blockers faced')]/following::textarea[1]"))
                .sendKeys(config.getBlockers() != null ? config.getBlockers() : "NA");

        addStep("All form fields filled");
    }
}
