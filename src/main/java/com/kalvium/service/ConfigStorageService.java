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
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private String configPath;

    @PostConstruct
    public void init() {
        try {
            Path appData = Paths.get("/app/data");
            if (Files.isWritable(appData.getParent())) {
                Files.createDirectories(appData);
                configPath = "/app/data/config.json";
            } else {
                throw new IOException("Not writable");
            }
        } catch (Exception e) {
            configPath = System.getProperty("java.io.tmpdir") + "/config.json";
            logger.info("Using temp directory for config: " + configPath);
        }
    }

    public void saveConfig(AuthConfig config) throws IOException {
        String json = gson.toJson(config);
        Files.writeString(Paths.get(configPath), json);
        logger.info("Configuration saved to: " + configPath);
    }

    public AuthConfig loadConfig() throws IOException {
        Path path = Paths.get(configPath);
        if (!Files.exists(path)) {
            return null;
        }
        String json = Files.readString(path);
        return gson.fromJson(json, AuthConfig.class);
    }

    public boolean hasConfig() {
        return Files.exists(Paths.get(configPath));
    }
}
