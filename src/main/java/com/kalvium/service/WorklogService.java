package com.kalvium.service;

import com.kalvium.model.AuthConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
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

import java.time.Duration;

@Service
public class WorklogService {

    private static final Logger logger = LoggerFactory.getLogger(WorklogService.class);

    @Autowired
    private ConfigStorageService configStorage;

    public String submitWorklog() {
        WebDriver driver = null;

        try {
            AuthConfig config = configStorage.loadConfig();
            if (config == null) {
                return "ERROR: No configuration found. Please configure cookies first.";
            }

            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless", "--no-sandbox", "--disable-dev-shm-usage",
                    "--disable-blink-features=AutomationControlled", "--start-maximized");

            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            logger.info("Starting automation...");

            driver.get("https://kalvium.community");
            Thread.sleep(2000);

            injectCookies(driver, config);

            driver.get("https://kalvium.community/internships");
            Thread.sleep(3000);

            WebElement completeButton = findPendingWorklog(driver, wait);
            completeButton.click();
            Thread.sleep(2000);

            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//*[contains(text(), 'My Worklog')]")));

            fillForm(driver, wait, config);

            WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(), 'Submit')] | //button[@type='submit']")));
            submitButton.click();
            Thread.sleep(3000);

            logger.info("Worklog submitted successfully");
            return "SUCCESS: Worklog submitted!";

        } catch (Exception e) {
            logger.error("Error: " + e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
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
        WebElement dropdown = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//select")));
        dropdown.click();
        Thread.sleep(500);

        wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//option[contains(text(), 'Working out of the Kalvium environment')]"))).click();
        Thread.sleep(500);

        driver.findElement(By.xpath("//*[contains(text(), 'Tasks completed today')]/following::textarea[1]"))
                .sendKeys(config.getTasksCompleted() != null ? config.getTasksCompleted() : "Tasks completed");

        driver.findElement(By.xpath("//*[contains(text(), 'Challenges encountered')]/following::textarea[1]"))
                .sendKeys(config.getChallenges() != null ? config.getChallenges() : "NA");

        driver.findElement(By.xpath("//*[contains(text(), 'Blockers faced')]/following::textarea[1]"))
                .sendKeys(config.getBlockers() != null ? config.getBlockers() : "NA");
    }
}
