package com.kalvium.service;

import com.kalvium.model.AuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.*;
import java.sql.*;

@Service
public class ConfigStorageService {

    private static final Logger logger = LoggerFactory.getLogger(ConfigStorageService.class);
    private String dbPath;
    private static final String TABLE_NAME = "worklog_config";

    @PostConstruct
    public void init() {
        try {
            // Try to use /app/data directory (for Render deployment)
            Path appData = Paths.get("/app/data");
            if (Files.isWritable(appData.getParent()) || Files.exists(appData)) {
                Files.createDirectories(appData);
                dbPath = "/app/data/worklog.db";
            } else {
                throw new Exception("Not writable");
            }
        } catch (Exception e) {
            // Fallback to current directory
            dbPath = "worklog.db";
            logger.info("Using local directory for database: " + dbPath);
        }

        // Initialize database
        initDatabase();
    }

    private void initDatabase() {
        try (Connection conn = getConnection()) {
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    auth_session_id TEXT NOT NULL,
                    keycloak_identity TEXT NOT NULL,
                    keycloak_session TEXT NOT NULL,
                    tasks_completed TEXT,
                    challenges TEXT,
                    blockers TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(TABLE_NAME);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createTableSQL);
                logger.info("Database initialized at: " + dbPath);
            }
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        return DriverManager.getConnection(url);
    }

    public void saveConfig(AuthConfig config) {
        String sql = """
            INSERT OR REPLACE INTO %s
            (id, auth_session_id, keycloak_identity, keycloak_session, tasks_completed, challenges, blockers, updated_at)
            VALUES (1, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.formatted(TABLE_NAME);

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, config.getAuthSessionId());
            pstmt.setString(2, config.getKeycloakIdentity());
            pstmt.setString(3, config.getKeycloakSession());
            pstmt.setString(4, config.getTasksCompleted());
            pstmt.setString(5, config.getChallenges());
            pstmt.setString(6, config.getBlockers());

            pstmt.executeUpdate();
            logger.info("Configuration saved to database");
        } catch (SQLException e) {
            logger.error("Failed to save configuration", e);
            throw new RuntimeException("Failed to save configuration", e);
        }
    }

    public AuthConfig loadConfig() {
        String sql = "SELECT * FROM %s WHERE id = 1".formatted(TABLE_NAME);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                AuthConfig config = new AuthConfig();
                config.setAuthSessionId(rs.getString("auth_session_id"));
                config.setKeycloakIdentity(rs.getString("keycloak_identity"));
                config.setKeycloakSession(rs.getString("keycloak_session"));
                config.setTasksCompleted(rs.getString("tasks_completed"));
                config.setChallenges(rs.getString("challenges"));
                config.setBlockers(rs.getString("blockers"));
                return config;
            }
            return null;
        } catch (SQLException e) {
            logger.error("Failed to load configuration", e);
            return null;
        }
    }

    public boolean hasConfig() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE id = 1")) {

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Failed to check configuration", e);
            return false;
        }
    }
}
