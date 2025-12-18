package com.kalvium.controller;

import com.kalvium.model.AuthConfig;
import com.kalvium.service.ConfigStorageService;
import com.kalvium.service.WorklogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class WorklogController {

    private static final Logger logger = LoggerFactory.getLogger(WorklogController.class);

    @Autowired
    private WorklogService worklogService;

    @Autowired
    private ConfigStorageService configStorage;

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "Kalvium Worklog Automation");
        response.put("hasConfig", configStorage.hasConfig());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/config/save")
    @ResponseBody
    public ResponseEntity<Map<String, String>> saveConfig(@RequestBody AuthConfig config) {
        Map<String, String> response = new HashMap<>();
        try {
            configStorage.saveConfig(config);
            response.put("status", "success");
            response.put("message", "Configuration saved for scheduled tasks!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/api/run")
    @ResponseBody
    public ResponseEntity<Map<String, String>> run(@RequestBody AuthConfig config) {
        Map<String, String> response = new HashMap<>();

        try {
            configStorage.saveConfig(config);
        } catch (Exception e) {
            logger.warn("Could not save config: " + e.getMessage());
        }

        String result = worklogService.submitWorklog(config);
        response.put("status", result.startsWith("SUCCESS") ? "success" : "error");
        response.put("message", result);
        return result.startsWith("SUCCESS") ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }
}
