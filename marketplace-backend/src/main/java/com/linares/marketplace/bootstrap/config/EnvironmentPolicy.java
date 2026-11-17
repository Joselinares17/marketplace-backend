package com.linares.marketplace.bootstrap.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

/** Runs after config data but before logging initialization; never includes rejected values. */
public class EnvironmentPolicy implements EnvironmentPostProcessor, Ordered {
    static final Set<String> ENVIRONMENTS = Set.of("local", "ci", "staging", "prod");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String selected = selected(environment);
        for (var source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (var sourceKey : enumerable.getPropertyNames()) {
                    String key = sourceKey.contains("_")
                            ? sourceKey.toLowerCase(java.util.Locale.ROOT).replace('_', '.') : sourceKey;
                    if (key.startsWith("logging.level.")) {
                        String level = environment.getProperty(key, "").toUpperCase(java.util.Locale.ROOT);
                        if (!Set.of("INFO", "WARN", "ERROR", "OFF").contains(level)) {
                            throw new IllegalStateException("Configuration rejected: logging.level requires INFO or higher.");
                        }
                    }
                }
            }
        }
        if (!"false".equalsIgnoreCase(environment.getProperty("debug", "false"))
                || !"false".equalsIgnoreCase(environment.getProperty("trace", "false"))) {
            throw new IllegalStateException("Configuration rejected: debug and trace must be disabled.");
        }
        for (String output : java.util.List.of("console", "file")) {
            if (!"ecs".equals(environment.getProperty("logging.structured.format." + output, "ecs"))) {
                throw new IllegalStateException("Configuration rejected: structured logging must use ecs.");
            }
        }
        environment.getPropertySources().addFirst(new MapPropertySource("runtimeLogging",
                java.util.Map.of("logging.structured.ecs.service.environment", selected)));
    }

    public static String selected(org.springframework.core.env.Environment environment) {
        var selected = Arrays.stream(environment.getActiveProfiles()).filter(ENVIRONMENTS::contains).toList();
        if (selected.size() != 1) {
            throw new IllegalStateException("Configuration rejected: select exactly one environment profile: local, ci, staging or prod.");
        }
        return selected.getFirst();
    }

    @Override public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 1; }
}
