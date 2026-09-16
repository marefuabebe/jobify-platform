package com.webapp.jobportal.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/jobportal}")
    private String rawUrl;

    @Value("${spring.datasource.username:root}")
    private String rawUsername;

    @Value("${spring.datasource.password:marefu@@3854}")
    private String rawPassword;

    @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:5}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:1}")
    private int minIdle;

    @Value("${spring.datasource.hikari.connection-timeout:20000}")
    private long connectionTimeout;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        String url = rawUrl;
        String username = rawUsername;
        String password = rawPassword;

        // Automatically convert mysql://user:password@host:port/database to jdbc:mysql://host:port/database
        if (url != null && url.startsWith("mysql://")) {
            try {
                URI uri = new URI(url);
                if (uri.getUserInfo() != null) {
                    String[] userParts = uri.getUserInfo().split(":", 2);
                    username = userParts[0];
                    if (userParts.length > 1) {
                        password = userParts[1];
                    }
                }
                int port = uri.getPort() == -1 ? 3306 : uri.getPort();
                String host = uri.getHost();
                String path = uri.getPath(); // /dbname
                String query = uri.getQuery();

                url = "jdbc:mysql://" + host + ":" + port + path + (query != null ? "?" + query : "");
                log.info("Normalized database URL to JDBC format: jdbc:mysql://{}:{}{}", host, port, path);
            } catch (Exception e) {
                log.warn("Failed to parse mysql:// URI, using raw URL: {}", e.getMessage());
            }
        }

        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);

        // Memory-conscious pool settings for Render 512MB RAM free tier
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1800000);
        config.setPoolName("JobifyHikariPool");

        log.info("Initialized Jobify DataSource with URL: {}", url.replaceAll("(?<=:)[^/@:]+(?=@)", "******"));
        return new HikariDataSource(config);
    }
}
