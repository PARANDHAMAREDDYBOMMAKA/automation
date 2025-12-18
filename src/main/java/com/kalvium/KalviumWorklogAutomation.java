package com.kalvium;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.io.FileHandler;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class KalviumWorklogAutomation {

    private static final String CONFIG_FILE = "src/main/resources/config.properties";
    private static Properties config;

    @SuppressWarnings({"UseSpecificCatch", "CallToPrintStackTrace"})
    public static void main(String[] args) {
        WebDriver driver = null;

        try {
            // Load configuration
            loadConfig();

            // Setup WebDriver
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();

            // Uncomment the line below to run in headless mode (no browser window)
            // options.addArguments("--headless");

            options.addArguments("--start-maximized");
            options.addArguments("--disable-blink-features=AutomationControlled");

            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            System.out.println("Starting Kalvium Worklog Automation...");

            // Step 1: Navigate to Kalvium login page
            System.out.println("Navigating to Kalvium...");
            driver.get("https://kalvium.community/internships");

            // Wait for page to load
            Thread.sleep(5000);

            // Step 2: Check if already logged in, if not, attempt automated login
            if (isLoginRequired(driver)) {
                System.out.println("Login required. Attempting automated login...");
                System.out.println("Note: This may fail if 2FA is required.");
                performLogin(driver, wait);
            } else {
                System.out.println("✓ Already logged in!");
            }

            // Step 3: Navigate to internships page (if not already there)
            if (!driver.getCurrentUrl().contains("internships")) {
                System.out.println("Navigating to internships page...");
                driver.get("https://kalvium.community/internships");
                Thread.sleep(3000);
            }

            // Step 4: Click on the pending worklog item
            System.out.println("Looking for pending worklog...");
            System.out.println("Current URL: " + driver.getCurrentUrl());

            // Take screenshot for debugging
            try {
                File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                FileHandler.copy(screenshot, new File("debug_screenshot.png"));
                System.out.println("Screenshot saved to debug_screenshot.png");
            } catch (Exception e) {
                System.out.println("Failed to take screenshot: " + e.getMessage());
            }

            // Try multiple selectors for the pending section
            WebElement completeButton = null;

            try {
                // Try finding the date row with "-" status (pending)
                completeButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//td[contains(text(), '-')]/..//button | " +
                             "//tr[contains(., '-')]//button | " +
                             "//*[contains(@class, 'pending')]//button | " +
                             "//button[contains(text(), 'Complete')] | " +
                             "//button[contains(text(), 'complete')]")
                ));
                System.out.println("Found pending worklog button!");
            } catch (Exception e) {
                // If that doesn't work, try looking for today's date
                String todayDate = java.time.LocalDate.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")
                );
                System.out.println("Looking for today's date: " + todayDate);

                completeButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//td[contains(text(), '" + todayDate + "')]/..//button | " +
                             "//tr[contains(., '" + todayDate + "')]//button")
                ));
            }

            System.out.println("Clicking on pending worklog...");
            completeButton.click();
            Thread.sleep(2000);

            // Step 5: Wait for the popover/modal to appear
            System.out.println("Waiting for worklog form to appear...");
            @SuppressWarnings("unused")
            WebElement worklogPanel = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[contains(text(), 'My Worklog')]")
            ));

            // Step 6: Fill the dropdown - "Working out of the Kalvium environment (Classroom)"
            System.out.println("Selecting work status...");
            WebElement dropdown = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//select | //div[contains(@class, 'select')] | " +
                         "//*[contains(text(), 'Your work status')]/following-sibling::*//select")
            ));

            dropdown.click();
            Thread.sleep(500);

            WebElement classroomOption = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//option[contains(text(), 'Working out of the Kalvium environment (Classroom)')] | " +
                         "//*[contains(text(), 'Working out of the Kalvium environment (Classroom)')]")
            ));
            classroomOption.click();
            Thread.sleep(500);

            // Step 7: Fill the text areas
            System.out.println("Filling form fields...");

            // Tasks completed today
            WebElement tasksField = driver.findElement(
                By.xpath("//*[contains(text(), 'Tasks completed today')]/following::textarea[1] | " +
                         "//*[contains(text(), '📋')]/following::textarea[1]")
            );
            tasksField.clear();
            tasksField.sendKeys(config.getProperty("tasks.completed", "Need to complete the tasks assigned."));

            // Challenges encountered
            WebElement challengesField = driver.findElement(
                By.xpath("//*[contains(text(), 'Challenges encountered')]/following::textarea[1] | " +
                         "//*[contains(text(), '⚡')]/following::textarea[1]")
            );
            challengesField.clear();
            challengesField.sendKeys(config.getProperty("challenges", "NA"));

            // Blockers faced
            WebElement blockersField = driver.findElement(
                By.xpath("//*[contains(text(), 'Blockers faced')]/following::textarea[1] | " +
                         "//*[contains(text(), '🚧')]/following::textarea[1]")
            );
            blockersField.clear();
            blockersField.sendKeys(config.getProperty("blockers", "NA"));

            System.out.println("Form filled successfully!");

            // Step 8: Submit the form
            System.out.println("Submitting the form...");
            WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(), 'Submit')] | " +
                         "//button[@type='submit'] | " +
                         "//button[contains(@class, 'submit')]")
            ));
            submitButton.click();

     
            Thread.sleep(3000);

            System.out.println("✓ Worklog submitted successfully!");

        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Wait a few seconds before closing to see the result
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (driver != null) {
                driver.quit();
                System.out.println("Browser closed.");
            }
        }
    }

    @SuppressWarnings("ConvertToTryWithResources")
    private static void loadConfig() throws IOException {
        config = new Properties();
        FileInputStream fis = new FileInputStream(CONFIG_FILE);
        config.load(fis);
        fis.close();
    }

    private static boolean isLoginRequired(WebDriver driver) {
        try {
            // Check if "Continue with Google" button exists (indicates login page)
            driver.findElement(By.xpath("//button[contains(text(), 'Continue with Google')] | " +
                                        "//*[contains(text(), 'Continue with Google')]"));
            return true;
        } catch (Exception e) {
            // If no login button found, check if we can see the internship content
            try {
                driver.findElement(By.xpath("//*[contains(text(), 'My Internship')] | " +
                                           "//*[contains(text(), 'My Organization')]"));
                return false; // Successfully logged in
            } catch (Exception ex) {
                return true; // Neither login button nor internship content found
            }
        }
    }

    private static void performLogin(WebDriver driver, WebDriverWait wait) throws InterruptedException {
        // Step 1: Click "Continue with Google" button
        System.out.println("Looking for 'Continue with Google' button...");
        WebElement googleButton = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//button[contains(text(), 'Continue with Google')] | " +
                     "//button[contains(text(), 'Google')] | " +
                     "//button[contains(@class, 'google')] | " +
                     "//*[contains(text(), 'Sign in with Google')]")
        ));
        googleButton.click();
        System.out.println("Clicked 'Continue with Google'");

        Thread.sleep(3000);

        // Step 2: Handle Google login popup/redirect
        // Switch to Google login window if it's a popup
        String mainWindow = driver.getWindowHandle();
        if (driver.getWindowHandles().size() > 1) {
            for (String windowHandle : driver.getWindowHandles()) {
                if (!windowHandle.equals(mainWindow)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }
        }

        // Step 3: Enter Google email
        System.out.println("Entering Google email...");
        WebElement googleEmailField = wait.until(ExpectedConditions.presenceOfElementLocated(
            By.xpath("//input[@type='email'] | //input[@id='identifierId'] | //input[@name='identifier']")
        ));
        googleEmailField.clear();
        googleEmailField.sendKeys(config.getProperty("google.email"));

        // Click Next button
        WebElement nextButton = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//button[contains(., 'Next')] | //*[@id='identifierNext'] | //button[@type='button']")
        ));
        nextButton.click();
        Thread.sleep(3000);

        // Step 4: Enter Google password
        System.out.println("Entering Google password...");
        // Wait for password field to be both visible and interactable
        WebElement googlePasswordField = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//input[@type='password'] | //input[@name='password'] | //input[@name='Passwd']")
        ));

        // Click on the field first to ensure focus
        googlePasswordField.click();
        Thread.sleep(500);

        // Send password without clearing (as field should be empty)
        googlePasswordField.sendKeys(config.getProperty("google.password"));
        Thread.sleep(1000);

        // Click Next/Sign in button
        WebElement signInButton = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//button[contains(., 'Next')] | //*[@id='passwordNext'] | " +
                     "//button[contains(., 'Sign in')] | //button[@type='button']")
        ));
        signInButton.click();

        System.out.println("Waiting for authentication to complete...");
        Thread.sleep(5000);

        // Switch back to main window if we were in a popup
        if (driver.getWindowHandles().size() > 1) {
            driver.switchTo().window(mainWindow);
        }

        // Wait for redirect back to Kalvium
        Thread.sleep(3000);

        System.out.println("Login completed!");
    }
}
