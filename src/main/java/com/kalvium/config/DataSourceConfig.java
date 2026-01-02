package com.kalvium.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    @Value("${DATABASE_URL:}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        if (databaseUrl == null || databaseUrl.isEmpty()) {
            throw new IllegalStateException("DATABASE_URL environment variable is not set");
        }

        // Add jdbc: prefix if not present (for Render/Heroku compatibility)
        String jdbcUrl = databaseUrl.startsWith("jdbc:")
            ? databaseUrl
            : "jdbc:" + databaseUrl;

        return DataSourceBuilder
            .create()
            .url(jdbcUrl)
            .driverClassName("org.postgresql.Driver")
            .build();
    }
}
