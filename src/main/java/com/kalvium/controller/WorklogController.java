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

    @GetMapping("/api/users")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> listUsers() {
        Map<String, Object> response = new HashMap<>();
        try {
            java.util.List<AuthConfig> configs = configStorage.loadAllConfigs();
            java.util.List<Map<String, String>> users = new java.util.ArrayList<>();

            for (int i = 0; i < configs.size(); i++) {
                AuthConfig config = configs.get(i);
                Map<String, String> userInfo = new HashMap<>();
                userInfo.put("id", String.valueOf(i + 1));
                // Show only last 8 characters of auth_session_id for privacy
                String authId = config.getAuthSessionId();
                userInfo.put("authSessionId", authId.length() > 8 ? "..." + authId.substring(authId.length() - 8) : authId);
                userInfo.put("tasksCompleted", config.getTasksCompleted());
                userInfo.put("challenges", config.getChallenges());
                userInfo.put("blockers", config.getBlockers());
                users.add(userInfo);
            }

            response.put("status", "success");
            response.put("userCount", configs.size());
            response.put("users", users);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/api/reset")
    @ResponseBody
    public ResponseEntity<Map<String, String>> resetDatabase() {
        Map<String, String> response = new HashMap<>();
        try {
            int userCount = configStorage.getUserCount();
            configStorage.resetDatabase();
            response.put("status", "success");
            response.put("message", "Database reset successfully. Deleted " + userCount + " user configuration(s).");
            logger.info("Database reset via API - deleted {} users", userCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
