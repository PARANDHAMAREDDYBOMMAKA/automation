package com.kalvium.scheduler;

import com.kalvium.model.AuthConfig;
import com.kalvium.service.ConfigStorageService;
import com.kalvium.service.WorklogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class WorklogScheduler {

    private static final Logger logger = LoggerFactory.getLogger(WorklogScheduler.class);

    @Autowired
    private WorklogService worklogService;

    @Autowired
    private ConfigStorageService configStorage;

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
