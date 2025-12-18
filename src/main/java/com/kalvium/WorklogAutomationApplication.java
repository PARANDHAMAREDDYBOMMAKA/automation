package com.kalvium;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorklogAutomationApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorklogAutomationApplication.class, args);
    }
}
