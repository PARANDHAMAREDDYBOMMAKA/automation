package com.kalvium.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kalvium.model.AuthConfig;

import jakarta.annotation.PostConstruct;

@Service
public class SupabaseConfigStorageService {

    private static final Logger logger = LoggerFactory.getLogger(SupabaseConfigStorageService.class);
    private static final String TABLE_NAME = "worklog_config";
    private static final String SCREENSHOTS_TABLE = "worklog_screenshots";

    @Autowired(required = false)
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        if (dataSource == null) {
            logger.error("DataSource not configured. Please set DATABASE_URL environment variable.");
            return;
        }

        verifyDatabaseConnection();
    }

    private void verifyDatabaseConnection() {
        try (Connection conn = getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                // Just verify connection and count existing data
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME)) {
                    if (rs.next()) {
                        int rowCount = rs.getInt(1);
                        logger.info("✓ Connected to database successfully. Users count: {}", rowCount);
                    }
                }

                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + SCREENSHOTS_TABLE)) {
                    if (rs.next()) {
                        int rowCount = rs.getInt(1);
                        logger.info("✓ Screenshots count: {}", rowCount);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to connect to database: {}", e.getMessage());
            logger.error("Make sure your tables exist in Supabase:");
            logger.error("  - worklog_config (for user credentials)");
            logger.error("  - worklog_screenshots (for screenshots)");
        }
    }

    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not configured");
        }
        return dataSource.getConnection();
    }

    public void saveScreenshot(String authSessionId, String description, byte[] screenshotData) {
        String sql = """
            INSERT INTO %s (user_auth_session_id, description, screenshot_data)
            VALUES (?, ?, ?)
            """.formatted(SCREENSHOTS_TABLE);

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, authSessionId);
            pstmt.setString(2, description);
            pstmt.setBytes(3, screenshotData);
            pstmt.executeUpdate();
            logger.info("Screenshot saved for user: {}", description);
        } catch (SQLException e) {
            logger.error("Failed to save screenshot", e);
        }
    }

    public java.util.List<java.util.Map<String, Object>> getScreenshots(String authSessionId) {
        java.util.List<java.util.Map<String, Object>> screenshots = new java.util.ArrayList<>();
        String sql = """
            SELECT id, description, screenshot_data, created_at
            FROM %s
            WHERE user_auth_session_id = ?
            ORDER BY created_at DESC
            LIMIT 10
            """.formatted(SCREENSHOTS_TABLE);

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, authSessionId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> screenshot = new java.util.HashMap<>();
                    screenshot.put("id", rs.getInt("id"));
                    screenshot.put("description", rs.getString("description"));
                    screenshot.put("screenshot_data", rs.getBytes("screenshot_data"));
                    screenshot.put("created_at", rs.getTimestamp("created_at"));
                    screenshots.add(screenshot);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve screenshots", e);
        }

        return screenshots;
    }

    public void saveConfig(AuthConfig config) {
        String checkSql = "SELECT id FROM %s WHERE auth_session_id = ?".formatted(TABLE_NAME);
        String insertSql = """
            INSERT INTO %s
            (auth_session_id, keycloak_identity, keycloak_session, tasks_completed, challenges, blockers, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.formatted(TABLE_NAME);
        String updateSql = """
            UPDATE %s
            SET keycloak_identity = ?, keycloak_session = ?, tasks_completed = ?,
                challenges = ?, blockers = ?, updated_at = CURRENT_TIMESTAMP
            WHERE auth_session_id = ?
            """.formatted(TABLE_NAME);

        try (Connection conn = getConnection()) {
            Integer existingId = null;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, config.getAuthSessionId());
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        existingId = rs.getInt("id");
                    }
                }
            }

            if (existingId != null) {
                try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                    pstmt.setString(1, config.getKeycloakIdentity());
                    pstmt.setString(2, config.getKeycloakSession());
                    pstmt.setString(3, config.getTasksCompleted());
                    pstmt.setString(4, config.getChallenges());
                    pstmt.setString(5, config.getBlockers());
                    pstmt.setString(6, config.getAuthSessionId());
                    pstmt.executeUpdate();
                    logger.info("Configuration updated in Supabase for user (id: {})", existingId);
                }
            } else {
                try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    pstmt.setString(1, config.getAuthSessionId());
                    pstmt.setString(2, config.getKeycloakIdentity());
                    pstmt.setString(3, config.getKeycloakSession());
                    pstmt.setString(4, config.getTasksCompleted());
                    pstmt.setString(5, config.getChallenges());
                    pstmt.setString(6, config.getBlockers());
                    pstmt.executeUpdate();
                    logger.info("New user configuration saved to Supabase");
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save configuration to Supabase", e);
            throw new RuntimeException("Failed to save configuration to Supabase", e);
        }
    }

    public AuthConfig loadConfig() {
        String sql = "SELECT * FROM %s ORDER BY id LIMIT 1".formatted(TABLE_NAME);

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
            logger.error("Failed to load configuration from Supabase", e);
            return null;
        }
    }

    public java.util.List<AuthConfig> loadAllConfigs() {
        java.util.List<AuthConfig> configs = new java.util.ArrayList<>();
        String sql = "SELECT * FROM %s ORDER BY id".formatted(TABLE_NAME);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                AuthConfig config = new AuthConfig();
                config.setAuthSessionId(rs.getString("auth_session_id"));
                config.setKeycloakIdentity(rs.getString("keycloak_identity"));
                config.setKeycloakSession(rs.getString("keycloak_session"));
                config.setTasksCompleted(rs.getString("tasks_completed"));
                config.setChallenges(rs.getString("challenges"));
                config.setBlockers(rs.getString("blockers"));
                configs.add(config);
            }
            logger.info("Loaded {} user configurations from Supabase", configs.size());
            return configs;
        } catch (SQLException e) {
            logger.error("Failed to load configurations from Supabase", e);
            return configs;
        }
    }

    public boolean hasConfig() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME)) {

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        } catch (SQLException e) {
            logger.error("Failed to check configuration in Supabase", e);
            return false;
        }
    }

    public int getUserCount() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.error("Failed to count users in Supabase", e);
            return 0;
        }
    }

    public void resetDatabase() {
        String deleteSql = "DELETE FROM %s".formatted(TABLE_NAME);

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.executeUpdate(deleteSql);
            logger.info("Supabase database reset successfully - all user configurations deleted");
        } catch (SQLException e) {
            logger.error("Failed to reset Supabase database", e);
            throw new RuntimeException("Failed to reset Supabase database", e);
        }
    }
}
