package com.kalvium;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Properties;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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

    private static String updateListItems(String html, String newContent) {
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

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    @SuppressWarnings({"UseSpecificCatch", "CallToPrintStackTrace"})
    public static void main(String[] args) {
        WebDriver driver = null;

        try {
            loadConfig();

            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();

            options.addArguments("--start-maximized");
            options.addArguments("--disable-blink-features=AutomationControlled");

            driver = new ChromeDriver(options);
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

            System.out.println("Starting Kalvium Worklog Automation...");

            System.out.println("Navigating to Kalvium...");
            driver.get("https://kalvium.community/internships");

            Thread.sleep(5000);

            if (isLoginRequired(driver)) {
                System.out.println("Login required. Attempting automated login...");
                System.out.println("Note: This may fail if 2FA is required.");
                performLogin(driver, wait);
            } else {
                System.out.println("✓ Already logged in!");
            }

            if (!driver.getCurrentUrl().contains("internships")) {
                System.out.println("Navigating to internships page...");
                driver.get("https://kalvium.community/internships");
                Thread.sleep(3000);
            }

            System.out.println("Looking for pending worklog...");
            System.out.println("Current URL: " + driver.getCurrentUrl());

            try {
                File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                FileHandler.copy(screenshot, new File("debug_screenshot.png"));
                System.out.println("Screenshot saved to debug_screenshot.png");
            } catch (Exception e) {
                System.out.println("Failed to take screenshot: " + e.getMessage());
            }

            WebElement completeButton;

            try {
                completeButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//td[contains(text(), '-')]/..//button | " +
                             "//tr[contains(., '-')]//button | " +
                             "//*[contains(@class, 'pending')]//button | " +
                             "//button[contains(text(), 'Complete')] | " +
                             "//button[contains(text(), 'complete')]")
                ));
                System.out.println("Found pending worklog button!");
            } catch (Exception e) {
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

            System.out.println("Waiting for worklog form to appear...");
            @SuppressWarnings("unused")
            WebElement worklogPanel = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[contains(text(), 'My Worklog')]")
            ));

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

            System.out.println("Filling form fields...");

            JavascriptExecutor js = (JavascriptExecutor) driver;

            WebElement tasksField = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[contains(text(), '📋 Tasks completed today')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true'] | " +
                         "//*[contains(text(), 'Tasks completed today')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true']")
            ));

            String tasksHtml = (String) js.executeScript("return arguments[0].innerHTML;", tasksField);
            String updatedTasksHtml = updateListItems(tasksHtml, config.getProperty("tasks.completed", "Need to complete the tasks assigned."));
            js.executeScript("arguments[0].innerHTML = arguments[1]; arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", tasksField, updatedTasksHtml);
            System.out.println("\n=== Tasks Field HTML ===");
            System.out.println(updatedTasksHtml);
            Thread.sleep(500);

            WebElement challengesField = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[contains(text(), '⚡ Challenges encountered')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true'] | " +
                         "//*[contains(text(), 'Challenges encountered')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true']")
            ));

            String challengesHtml = (String) js.executeScript("return arguments[0].innerHTML;", challengesField);
            String updatedChallengesHtml = updateListItems(challengesHtml, config.getProperty("challenges", "NA"));
            js.executeScript("arguments[0].innerHTML = arguments[1]; arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", challengesField, updatedChallengesHtml);
            System.out.println("\n=== Challenges Field HTML ===");
            System.out.println(updatedChallengesHtml);
            Thread.sleep(500);

            WebElement blockersField = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[contains(text(), '🚧 Blockers faced')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true'] | " +
                         "//*[contains(text(), 'Blockers faced')]/ancestor::div[contains(@class, 'prose')]//div[@contenteditable='true']")
            ));

            String blockersHtml = (String) js.executeScript("return arguments[0].innerHTML;", blockersField);
            String updatedBlockersHtml = updateListItems(blockersHtml, config.getProperty("blockers", "NA"));
            js.executeScript("arguments[0].innerHTML = arguments[1]; arguments[0].dispatchEvent(new Event('input', { bubbles: true }));", blockersField, updatedBlockersHtml);
            System.out.println("\n=== Blockers Field HTML ===");
            System.out.println(updatedBlockersHtml);
            Thread.sleep(500);

            System.out.println("Form filled successfully!");

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
            driver.findElement(By.xpath("//button[contains(text(), 'Continue with Google')] | " +
                                        "//*[contains(text(), 'Continue with Google')]"));
            return true;
        } catch (Exception e) {
            try {
                driver.findElement(By.xpath("//*[contains(text(), 'My Internship')] | " +
                                           "//*[contains(text(), 'My Organization')]"));
                return false;
            } catch (Exception ex) {
                return true;
            }
        }
    }

    private static void performLogin(WebDriver driver, WebDriverWait wait) throws InterruptedException {
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
        String mainWindow = driver.getWindowHandle();
        if (driver.getWindowHandles().size() > 1) {
            for (String windowHandle : driver.getWindowHandles()) {
                if (!windowHandle.equals(mainWindow)) {
                    driver.switchTo().window(windowHandle);
                    break;
                }
            }
        }

        System.out.println("Entering Google email...");
        WebElement googleEmailField = wait.until(ExpectedConditions.presenceOfElementLocated(
            By.xpath("//input[@type='email'] | //input[@id='identifierId'] | //input[@name='identifier']")
        ));
        googleEmailField.clear();
        googleEmailField.sendKeys(config.getProperty("google.email"));

        WebElement nextButton = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//button[contains(., 'Next')] | //*[@id='identifierNext'] | //button[@type='button']")
        ));
        nextButton.click();
        Thread.sleep(3000);

        System.out.println("Entering Google password...");
        WebElement googlePasswordField = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//input[@type='password'] | //input[@name='password'] | //input[@name='Passwd']")
        ));

        googlePasswordField.click();
        Thread.sleep(500);

        googlePasswordField.sendKeys(config.getProperty("google.password"));
        Thread.sleep(1000);

        WebElement signInButton = wait.until(ExpectedConditions.elementToBeClickable(
            By.xpath("//button[contains(., 'Next')] | //*[@id='passwordNext'] | " +
                     "//button[contains(., 'Sign in')] | //button[@type='button']")
        ));
        signInButton.click();

        System.out.println("Waiting for authentication to complete...");
        Thread.sleep(5000);

        if (driver.getWindowHandles().size() > 1) {
            driver.switchTo().window(mainWindow);
        }

        Thread.sleep(3000);

        System.out.println("Login completed!");
    }
}
