package com.kalvium.scheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.kalvium.service.ConfigStorageService;
import com.kalvium.service.WorklogService;

@Component
public class WorklogScheduler {

    private static final Logger logger = LoggerFactory.getLogger(WorklogScheduler.class);

    @Autowired
    private WorklogService worklogService;

    @Autowired
    private ConfigStorageService configStorage;

    @Value("${app.base.url:http://localhost:8080}")
    private String appBaseUrl;

    private final RestTemplate restTemplate;

    public WorklogScheduler() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(5);
        connectionManager.setDefaultMaxPerRoute(2);

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .evictExpiredConnections()
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(5000);
        requestFactory.setConnectionRequestTimeout(5000);

        this.restTemplate = new RestTemplate(requestFactory);
    }


    @Scheduled(fixedRate = 420000)
    @SuppressWarnings("UseSpecificCatch")
    public void keepAlive() {
        try {
            String healthUrl = appBaseUrl + "/health";
            restTemplate.getForObject(healthUrl, String.class);
            logger.info("Keep-alive ping sent successfully to prevent service shutdown");
        } catch (Exception e) {
            logger.warn("Keep-alive ping failed: {}", e.getMessage());
        }
    }

    // Scheduled time set to 5:15 PM IST (Asia/Kolkata) on weekdays
    @Scheduled(cron = "0 40 17 * * MON-FRI", zone = "Asia/Kolkata")
    @SuppressWarnings("BusyWait")
    public void runDailyWorklogSubmission() {
        logger.info("=== Scheduled Worklog Automation Started at {} ===",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        try {
            java.util.List<com.kalvium.model.AuthConfig> configs = configStorage.loadAllConfigs();

            if (configs == null || configs.isEmpty()) {
                logger.error("No configurations found for scheduled run");
                return;
            }

            logger.info("Found {} user(s) to process. Processing sequentially...", configs.size());

            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < configs.size(); i++) {
                com.kalvium.model.AuthConfig config = configs.get(i);
                logger.info("=== Processing user {}/{} ===", (i + 1), configs.size());

                try {
                    logger.info("Starting worklog submission for user {}/{}", (i + 1), configs.size());

                    String result = worklogService.submitWorklog(config);

                    logger.info("User {}/{} Result: {}", (i + 1), configs.size(),
                        result.length() > 200 ? result.substring(0, 200) + "..." : result);

                    if (result.startsWith("SUCCESS")) {
                        successCount++;
                        logger.info("✓ User {}/{} worklog submitted successfully", (i + 1), configs.size());
                    } else {
                        failCount++;
                        logger.error("✗ User {}/{} worklog submission failed", (i + 1), configs.size());
                        logger.error("Full error for user {}/{}: {}", (i + 1), configs.size(), result);
                    }
                } catch (Exception e) {
                    failCount++;
                    logger.error("✗ Exception processing user {}/{}", (i + 1), configs.size());
                    logger.error("Exception details: {}", e.getMessage(), e);
                }

                if (i < configs.size() - 1) {
                    try {
                        logger.info("Waiting 7 seconds before processing next user to ensure cleanup...");
                        Thread.sleep(7000);
                        logger.info("Ready to process next user");
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.warn("Delay interrupted");
                    }
                }
            }

            logger.info("=== Processing Summary: {} successful, {} failed out of {} total users ===",
                    successCount, failCount, configs.size());

        } catch (Exception e) {
            logger.error("Error during scheduled worklog submission", e);
        }

        logger.info("=== Scheduled Worklog Automation Finished at {} ===",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    @Scheduled(cron = "0 0 */6 * * *", zone = "UTC")
    public void cleanupDiskSpace() {
        logger.info("=== Disk Space Cleanup Started at {} ===",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        long totalFreedBytes = 0;

        try {
            totalFreedBytes += cleanupTempChromeFiles();
            totalFreedBytes += cleanupHeapDumps();
            totalFreedBytes += cleanupSystemTemp();
            totalFreedBytes += cleanupWebDriverCache();
            System.gc();

            logger.info("Disk cleanup completed. Total space freed: {} MB",
                    totalFreedBytes / (1024 * 1024));

        } catch (Exception e) {
            logger.error("Error during disk cleanup", e);
        }

        logger.info("=== Disk Space Cleanup Finished at {} ===",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private long cleanupTempChromeFiles() {
        long freedBytes = 0;
        try {
            String tmpDir = System.getProperty("java.io.tmpdir");
            Path tempPath = Paths.get(tmpDir);

            try (Stream<Path> files = Files.walk(tempPath, 1)) {
                freedBytes = files
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.startsWith("scoped_dir") ||
                                    name.contains("chrome") ||
                                    name.contains("Crashpad") ||
                                    name.contains(".org.chromium");
                        })
                        .mapToLong(path -> {
                            try {
                                long size = Files.size(path);
                                Files.deleteIfExists(path);
                                return size;
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .sum();
            }

            logger.info("Cleaned up temp Chrome files: {} KB", freedBytes / 1024);
        } catch (Exception e) {
            logger.warn("Error cleaning temp Chrome files: {}", e.getMessage());
        }
        return freedBytes;
    }

    private long cleanupHeapDumps() {
        long freedBytes = 0;
        try {
            Path dataPath = Paths.get("/app/data");
            if (!Files.exists(dataPath)) {
                dataPath = Paths.get("./data");
            }

            if (Files.exists(dataPath)) {
                try (Stream<Path> files = Files.list(dataPath)) {
                    freedBytes = files
                            .filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".hprof"))
                            .sorted((p1, p2) -> {
                                try {
                                    return Files.getLastModifiedTime(p2).compareTo(
                                            Files.getLastModifiedTime(p1));
                                } catch (IOException e) {
                                    return 0;
                                }
                            })
                            .skip(1)
                            .mapToLong(path -> {
                                try {
                                    long size = Files.size(path);
                                    Files.deleteIfExists(path);
                                    logger.info("Deleted old heap dump: {}", path.getFileName());
                                    return size;
                                } catch (IOException e) {
                                    return 0;
                                }
                            })
                            .sum();
                }
            }

            logger.info("Cleaned up heap dumps: {} MB", freedBytes / (1024 * 1024));
        } catch (Exception e) {
            logger.warn("Error cleaning heap dumps: {}", e.getMessage());
        }
        return freedBytes;
    }

    private long cleanupSystemTemp() {
        long freedBytes = 0;
        try {
            String tmpDir = System.getProperty("java.io.tmpdir");
            Path tempPath = Paths.get(tmpDir);

            try (Stream<Path> files = Files.walk(tempPath, 1)) {
                freedBytes = files
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            try {
                                long ageInHours = (System.currentTimeMillis() -
                                        Files.getLastModifiedTime(path).toMillis()) / (1000 * 60 * 60);
                                return ageInHours > 24;
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .mapToLong(path -> {
                            try {
                                long size = Files.size(path);
                                Files.deleteIfExists(path);
                                return size;
                            } catch (IOException e) {
                                return 0;
                            }
                        })
                        .sum();
            }

            logger.info("Cleaned up old temp files: {} KB", freedBytes / 1024);
        } catch (Exception e) {
            logger.warn("Error cleaning system temp: {}", e.getMessage());
        }
        return freedBytes;
    }

    private long cleanupWebDriverCache() {
        long freedBytes = 0;
        try {
            String userHome = System.getProperty("user.home");
            Path[] cachePaths = {
                Paths.get(userHome, ".cache", "selenium"),
                Paths.get(userHome, ".wdm"),
                Paths.get(userHome, ".m2", "repository", "webdriver")
            };

            for (Path cachePath : cachePaths) {
                if (Files.exists(cachePath)) {
                    try (Stream<Path> files = Files.walk(cachePath)) {
                        freedBytes += files
                                .filter(Files::isRegularFile)
                                .filter(path -> {
                                    try {
                                        long ageInDays = (System.currentTimeMillis() -
                                                Files.getLastModifiedTime(path).toMillis()) / (1000 * 60 * 60 * 24);
                                        return ageInDays > 7;
                                    } catch (IOException e) {
                                        return false;
                                    }
                                })
                                .mapToLong(path -> {
                                    try {
                                        long size = Files.size(path);
                                        Files.deleteIfExists(path);
                                        return size;
                                    } catch (IOException e) {
                                        return 0;
                                    }
                                })
                                .sum();
                    }
                }
            }

            logger.info("Cleaned up WebDriver cache: {} MB", freedBytes / (1024 * 1024));
        } catch (Exception e) {
            logger.warn("Error cleaning WebDriver cache: {}", e.getMessage());
        }
        return freedBytes;
    }
}
