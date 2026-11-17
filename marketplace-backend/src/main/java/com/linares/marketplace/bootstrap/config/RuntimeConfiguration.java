package com.linares.marketplace.bootstrap.config;

import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Validates existing Boot properties before datasource creation or accepting traffic. */
@Configuration(proxyBeanMethods = false)
public class RuntimeConfiguration {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RuntimeConfiguration.class);

    @Bean
    org.springframework.context.ApplicationListener<org.springframework.boot.context.event.ApplicationReadyEvent> readyLog() {
        return event -> LOG.info("Application ready");
    }

    @Bean
    static BeanFactoryPostProcessor validateRuntime(Environment environment) {
        return beanFactory -> validate(environment);
    }

    static void validate(Environment environment) {
        String profile = EnvironmentPolicy.selected(environment);
        boolean cloud = Set.of("staging", "prod").contains(profile);
        String url = required(environment, "spring.datasource.url");
        String username = required(environment, "spring.datasource.username");
        String password = required(environment, "spring.datasource.password");
        try {
            if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            URI address = URI.create(url.substring(5));
            if (address.getHost() == null || address.getUserInfo() != null
                    || address.getFragment() != null || address.getPath() == null
                    || address.getPath().length() < 2 || address.getPort() == 0
                    || address.getPort() > 65535 || address.getPort() < -1) throw new IllegalArgumentException();
            var query = new HashMap<String, String>();
            if (address.getRawQuery() != null) {
                for (String part : address.getRawQuery().split("&")) {
                    String[] pair = part.split("=", -1);
                    if (pair.length != 2 || !Set.of("sslmode", "connectTimeout", "socketTimeout").contains(pair[0])
                            || query.put(pair[0], pair[1]) != null) throw new IllegalArgumentException();
                }
            }
            if (query.containsKey("sslmode") && !Set.of("disable", "allow", "prefer", "require", "verify-ca",
                    "verify-full").contains(query.get("sslmode"))) throw new IllegalArgumentException();
            for (String key : List.of("connectTimeout", "socketTimeout")) {
                if (query.containsKey(key)) {
                    int seconds = Integer.parseInt(query.get(key));
                    if (seconds < 1 || seconds > 300) throw new IllegalArgumentException();
                }
            }
            if (cloud && (!"verify-full".equals(query.get("sslmode"))
                    || Set.of("localhost", "127.0.0.1", "::1", "[::1]", "0.0.0.0", "postgres")
                        .contains(address.getHost().toLowerCase(Locale.ROOT)))) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw invalid("spring.datasource.url");
        }
        if (cloud && (username.equals("marketplace_local") || password.equals("local_postgres_only_change_me"))) {
            throw invalid("spring.datasource.credentials");
        }
        integer(environment, "server.port", cloud ? 1 : 0, 65535);
        integer(environment, "spring.datasource.hikari.maximum-pool-size", 1, 100);
        integer(environment, "spring.datasource.hikari.connection-timeout", 250, 300000);
    }

    private static void integer(Environment environment, String key, int min, int max) {
        String value = environment.getProperty(key);
        if (value == null) return; // Keep Boot's defaults when not overridden.
        try {
            int number = Integer.parseInt(value);
            if (number < min || number > max) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw invalid(key);
        }
    }

    private static String required(Environment environment, String key) {
        String value;
        try { value = environment.getProperty(key); } catch (RuntimeException ex) { throw invalid(key); }
        if (value == null || value.isBlank()) throw invalid(key);
        return value;
    }

    private static IllegalStateException invalid(String key) {
        return new IllegalStateException("Configuration rejected: " + key + " is missing or invalid.");
    }
}
