package com.kalvium.scheduler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

import com.kalvium.model.AuthConfig;
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

    // Constructor to initialize RestTemplate with proper connection pool management
    public WorklogScheduler() {
        // Configure connection pool with aggressive cleanup to prevent memory leaks
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(5); // Limit total connections
        connectionManager.setDefaultMaxPerRoute(2); // Limit per-route connections

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .evictIdleConnections(TimeValue.ofSeconds(30)) // Evict idle connections after 30s
                .evictExpiredConnections() // Evict expired connections
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(5000); // 5 second connect timeout
        requestFactory.setConnectionRequestTimeout(5000); // 5 second connection request timeout

        this.restTemplate = new RestTemplate(requestFactory);
    }


    @Scheduled(fixedRate = 600000)
    public void keepAlive() {
        try {
            String healthUrl = appBaseUrl + "/health";
            restTemplate.getForObject(healthUrl, String.class);
            logger.debug("Keep-alive ping sent to: {}", healthUrl);
        } catch (Exception e) {
            logger.debug("Keep-alive ping failed (this is normal on startup): {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 30 11 * * MON-FRI", zone = "UTC")
    public void runDailyWorklogSubmission() {
        logger.info("=== Scheduled Worklog Automation Started at {} ===",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        try {
            AuthConfig config = configStorage.loadConfig();
            if (config == null) {
                logger.error("No configuration found for scheduled run");
                return;
            }

            String result = worklogService.submitWorklog(config);
            logger.info("Automation Result: {}", result);

            if (result.startsWith("SUCCESS")) {
                logger.info("Scheduled worklog submission completed successfully");
            } else {
                logger.error("Scheduled worklog submission failed: {}", result);
            }
        } catch (Exception e) {
            logger.error("Error during scheduled worklog submission", e);
        }

        logger.info("=== Scheduled Worklog Automation Finished at {} ===",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
