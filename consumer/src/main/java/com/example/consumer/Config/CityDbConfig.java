package com.example.consumer.Config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;


@Configuration
public class CityDbConfig {

    private static final Map<String, String> cityDbMap = Map.of(
            "Ahmedabad", "ahmedabad_db",
            "Hyderabad", "hyderabad_db",
            "Delhi", "delhi_db",
            "Bengaluru", "bengaluru_db",
            "Kolkata", "kolkata_db",
            "Chennai", "chennai_db",
            "Mumbai", "mumbai_db",
            "Pune", "pune_db"
    );

    @Bean
    public Map<String, JdbcTemplate> cityJdbcTemplates() {
        Map<String, JdbcTemplate> templates = new HashMap<>();
        cityDbMap.forEach((city, dbName) -> {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName("org.postgresql.Driver");
            config.setJdbcUrl("jdbc:postgresql://localhost:5433/" + dbName+"?reWriteBatchedInserts=true");
            config.setUsername("postgres");
            config.setPassword("Aditya");
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(4);

            JdbcTemplate jdbcTemplate = new JdbcTemplate(new HikariDataSource(config));
            templates.put(city, jdbcTemplate);
        });
        return templates;
    }
}
