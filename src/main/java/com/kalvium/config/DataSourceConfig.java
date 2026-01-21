package com.kalvium.config;

import java.net.URI;
import java.net.URISyntaxException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfig {

    private static final String DEFAULT_DATABASE_URL = "postgresql://postgres.wmqoeaahrnboninrihkk:sunnyreddy2809@aws-1-ap-northeast-1.pooler.supabase.com:5432/postgres";

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        String dbUrl = (databaseUrl == null || databaseUrl.isEmpty()) ? DEFAULT_DATABASE_URL : databaseUrl;

        try {
            URI dbUri = new URI(dbUrl.replace("postgres://", "postgresql://"));

            String username = dbUri.getUserInfo().split(":")[0];
            String password = dbUri.getUserInfo().split(":")[1];
            String host = dbUri.getHost();
            int port = dbUri.getPort();
            String database = dbUri.getPath().substring(1);

            String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.postgresql.Driver");

            config.setMaximumPoolSize(2);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10000);
            config.setIdleTimeout(120000);
            config.setMaxLifetime(300000);
            config.setLeakDetectionThreshold(60000);

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "50");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "512");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            return new HikariDataSource(config);

        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid DATABASE_URL format", e);
        }
    }
}
