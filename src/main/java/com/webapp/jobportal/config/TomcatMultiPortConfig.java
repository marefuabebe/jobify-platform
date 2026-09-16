package com.webapp.jobportal.config;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures Tomcat listens on BOTH port 10000 (Render default) and port 8080 (standard Spring default).
 * This eliminates any possibility of HTTP 502 Bad Gateway caused by proxy port mismatches.
 */
@Configuration
public class TomcatMultiPortConfig {

    private static final Logger log = LoggerFactory.getLogger(TomcatMultiPortConfig.class);

    @Value("${server.port:10000}")
    private int primaryPort;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> servletContainerCustomizer() {
        return factory -> {
            log.info("Primary Tomcat port configured as: {}", primaryPort);

            // If primary port is not 8080, attach an additional connector on 8080
            if (primaryPort != 8080) {
                try {
                    Connector connector8080 = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
                    connector8080.setPort(8080);
                    factory.addAdditionalTomcatConnectors(connector8080);
                    log.info("Attached additional Tomcat connector on port 8080");
                } catch (Exception e) {
                    log.warn("Could not attach secondary port 8080: {}", e.getMessage());
                }
            }

            // If primary port is not 10000, attach an additional connector on 10000
            if (primaryPort != 10000) {
                try {
                    Connector connector10000 = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
                    connector10000.setPort(10000);
                    factory.addAdditionalTomcatConnectors(connector10000);
                    log.info("Attached additional Tomcat connector on port 10000");
                } catch (Exception e) {
                    log.warn("Could not attach secondary port 10000: {}", e.getMessage());
                }
            }
        };
    }
}
