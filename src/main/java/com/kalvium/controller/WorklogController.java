package com.kalvium.controller;

import com.kalvium.model.AuthConfig;
import com.kalvium.service.ConfigStorageService;
import com.kalvium.service.WorklogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class WorklogController {

    @Autowired
    private ConfigStorageService configStorage;

    @Autowired
    private WorklogService worklogService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("hasConfig", configStorage.hasConfig());
        try {
            AuthConfig config = configStorage.loadConfig();
            if (config != null) {
                model.addAttribute("config", config);
            }
        } catch (Exception e) {
        }
        return "index";
    }

    @PostMapping("/api/config")
    @ResponseBody
    public ResponseEntity<Map<String, String>> saveConfig(@RequestBody AuthConfig config) {
        Map<String, String> response = new HashMap<>();
        try {
            configStorage.saveConfig(config);
            response.put("status", "success");
            response.put("message", "Configuration saved!");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/api/run")
    @ResponseBody
    public ResponseEntity<Map<String, String>> run() {
        Map<String, String> response = new HashMap<>();
        String result = worklogService.submitWorklog();
        response.put("status", result.startsWith("SUCCESS") ? "success" : "error");
        response.put("message", result);
        return result.startsWith("SUCCESS") ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }
}
