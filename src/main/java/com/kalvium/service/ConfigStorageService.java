package com.kalvium.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kalvium.model.AuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;

@Service
public class ConfigStorageService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigStorageService.class);
    private static final String CONFIG_FILE = "/app/data/config.json";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get("/app/data"));
        } catch (IOException e) {
            logger.error("Failed to create data directory", e);
        }
    }

    public void saveConfig(AuthConfig config) throws IOException {
        String json = gson.toJson(config);
        Files.writeString(Paths.get(CONFIG_FILE), json);
        logger.info("Configuration saved successfully");
    }

    public AuthConfig loadConfig() throws IOException {
        if (!Files.exists(Paths.get(CONFIG_FILE))) {
            return null;
        }
        String json = Files.readString(Paths.get(CONFIG_FILE));
        return gson.fromJson(json, AuthConfig.class);
    }

    public boolean hasConfig() {
        return Files.exists(Paths.get(CONFIG_FILE));
    }
}
