package com.linares.marketplace.bootstrap.config;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;

/** Additional output protection, not permission to log payloads or arbitrary objects. */
public class ProtectedLogging implements StructuredLoggingJsonMembersCustomizer<Object> {
    private static final Set<String> FIELDS = Set.of("@timestamp", "log", "log.level", "log.logger",
            "process", "process.thread", "process.thread.name", "process.pid", "message",
            "service", "service.name", "service.environment", "ecs", "ecs.version",
            "error", "error.type", "error.message", "error.stack_trace", "traceId", "spanId");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "(?i)(authorization|cookie|set-cookie|password|passwd|token|secret|session(?:id)?|iban|account)[\\\"'\\s]*[:=]\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^,;\\r\\n]+)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private final Environment environment;

    public ProtectedLogging(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingPathFilter(path -> !FIELDS.contains(path.toUnescapedString()));
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, this::redact));
    }

    private String redact(String value) {
        if (value == null) return null;
        for (String key : List.of("spring.datasource.password", "DB_PASSWORD")) {
            String secret = environment.getProperty(key, "");
            if (!secret.isBlank()) value = value.replace(secret, "[REDACTED]");
        }
        value = CREDENTIAL.matcher(value).replaceAll("$1=[REDACTED]");
        value = EMAIL.matcher(value).replaceAll("[REDACTED_EMAIL]");
        value = value.replaceAll("jdbc:postgresql://[^\\s\\\"']+", "[REDACTED_DATABASE_URL]");
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    @Override public String toString() { return "ProtectedLogging[redacted]"; }
}
