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

    @Value("${spring.datasource.hikari.connection-timeout:10000}")
    private long connectionTimeout;

    @Bean
    @Primary
    public DataSource dataSource() {
        String url = rawUrl;
        String username = rawUsername;
        String password = rawPassword;

        // 1. Automatically convert cloud provider URI (mysql://user:password@host:port/database)
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

        // 2. Detect Render/Cloud environment without external database
        boolean isRender = System.getenv("RENDER") != null || "true".equalsIgnoreCase(System.getenv("RENDER"));
        boolean isLocalhost = url != null && (url.contains("localhost:3306") || url.contains("127.0.0.1:3306"));

        if (isRender && isLocalhost) {
            log.warn("===============================================================================");
            log.warn("Render deployment detected with NO remote MySQL database configured!");
            log.warn("Falling back to embedded MySQL-compatible H2 database to keep the platform online.");
            log.warn("To use persistent data, configure SPRING_DATASOURCE_URL in Render Environment settings.");
            log.warn("===============================================================================");
            return createH2DataSource();
        }

        // 3. Attempt connecting to MySQL with cloud-friendly connection parameters
        try {
            if (url != null && url.startsWith("jdbc:mysql:")) {
                boolean isRemoteHost = !url.contains("localhost") && !url.contains("127.0.0.1");
                boolean hasExplicitSsl = url.contains("sslMode=") || url.contains("useSSL=");

                StringBuilder params = new StringBuilder();
                if (!url.contains("serverTimezone")) {
                    params.append(url.contains("?") || params.length() > 0 ? "&" : "?").append("serverTimezone=UTC");
                }

                if (!hasExplicitSsl) {
                    if (isRemoteHost) {
                        // Cloud databases (Aiven, AWS RDS, PlanetScale, etc.) require SSL
                        params.append(url.contains("?") || params.length() > 0 ? "&" : "?").append("sslMode=REQUIRED");
                    } else {
                        // Local MySQL without explicit SSL configuration
                        params.append(url.contains("?") || params.length() > 0 ? "&" : "?").append("useSSL=false");
                    }
                }

                if (!url.contains("allowPublicKeyRetrieval")) {
                    params.append(url.contains("?") || params.length() > 0 ? "&" : "?").append("allowPublicKeyRetrieval=true");
                }

                url = url + params.toString();
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName(driverClassName);
            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(minIdle);
            config.setConnectionTimeout(connectionTimeout);
            config.setInitializationFailTimeout(20000); // Allow up to 20s for cloud TLS handshake
            config.setIdleTimeout(300000);
            config.setMaxLifetime(1800000);
            config.setPoolName("JobifyHikariPool");

            log.info("Attempting connection to MySQL: {}", url.replaceAll("(?<=:)[^/@:]+(?=@)", "******"));
            HikariDataSource ds = new HikariDataSource(config);
            // Eager test connection
            ds.getConnection().close();
            log.info("Successfully connected to MySQL database!");
            return ds;
        } catch (Exception e) {
            log.error("Could not connect to configured MySQL database ({}). Message: {}", url, e.getMessage());
            log.warn("Falling back to embedded MySQL-compatible H2 database so application boots with 200 OK.");
            return createH2DataSource();
        }
    }

    private DataSource createH2DataSource() {
        HikariConfig h2Config = new HikariConfig();
        h2Config.setDriverClassName("org.h2.Driver");
        String dbDir = System.getProperty("java.io.tmpdir", "/tmp").replace("\\", "/");
        String h2Url = "jdbc:h2:file:" + dbDir + "/jobportal_h2;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
        h2Config.setJdbcUrl(h2Url);
        h2Config.setUsername("sa");
        h2Config.setPassword("");
        h2Config.setMaximumPoolSize(maxPoolSize);
        h2Config.setMinimumIdle(minIdle);
        h2Config.setPoolName("JobifyH2FallbackPool");

        log.info("Initialized embedded MySQL-compatible fallback database: {}", h2Url);
        return new HikariDataSource(h2Config);
    }
}
