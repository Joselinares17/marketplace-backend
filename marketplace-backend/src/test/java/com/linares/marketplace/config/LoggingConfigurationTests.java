package com.linares.marketplace.config;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.*;
import com.linares.marketplace.bootstrap.config.*;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class LoggingConfigurationTests {
    private static final String SECRET = "LOG_SECRET_CANARY_9234";

    @Configuration(proxyBeanMethods=false)
    @Import(RuntimeConfiguration.class)
    @EnableConfigurationProperties(DataSourceProperties.class)
    static class ValidationOnlyApplication {}

    private SpringApplicationBuilder application(String profile) {
        return new SpringApplicationBuilder(ValidationOnlyApplication.class)
                .profiles(profile).web(WebApplicationType.NONE)
                .bannerMode(org.springframework.boot.Banner.Mode.OFF).registerShutdownHook(false);
    }

    private String[] properties(String profile, String password) {
        return new String[]{"--spring.profiles.active="+profile,
                "--spring.datasource.url=jdbc:postgresql://db.example.invalid:5432/app?sslmode=verify-full",
                "--spring.datasource.username=synthetic_app", "--spring.datasource.password="+password};
    }

    @ParameterizedTest @ValueSource(strings={"local","ci","staging","prod"})
    void emitsProtectedRealJsonForEachEnvironment(String profile, CapturedOutput output) {
        try (var context = application(profile).run(properties(profile, SECRET))) {
            assertThat(context.getBean(DataSourceProperties.class).toString()).doesNotContain(SECRET);
            var logger = LoggerFactory.getLogger("com.linares.marketplace.loggingprobe");
            MDC.put("Authorization", "Bearer AUTH_SECRET_CANARY");
            MDC.put("email", "person@example.invalid");
            MDC.put("traceId", "0123456789abcdef0123456789abcdef");
            try {
                logger.info("Service ready");
                logger.error("Operation failed password={}\r\nforged-line", SECRET,
                        new IllegalStateException("token=TOKEN_SECRET_CANARY email=person@example.invalid"));
                logger.atInfo().addKeyValue("cookie", "SESSION_SECRET_CANARY").log("Request handled");
            } finally { MDC.clear(); }
            assertThat(new ProtectedLogging(context.getEnvironment()).toString()).doesNotContain(SECRET);
        }
        String log = output.getOut();
        assertThat(log).doesNotContain(SECRET, "AUTH_SECRET_CANARY","TOKEN_SECRET_CANARY",
                "SESSION_SECRET_CANARY", "person@example.invalid");
        var mapper = JsonMapper.builder().build();
        var lines = log.lines().filter(line -> line.startsWith("{")).toList();
        assertThat(lines).isNotEmpty();
        var ready = lines.stream().map(mapper::readTree).filter(json -> "Service ready".equals(json.path("message").asString()))
                .findFirst().orElseThrow();
        assertThat(ready.path("@timestamp").asString()).isNotBlank();
        assertThat(ready.at("/log/level").asString()).isEqualTo("INFO");
        assertThat(ready.at("/log/logger").asString()).contains("loggingprobe");
        assertThat(ready.at("/service/name").asString()).isEqualTo("marketplace-backend");
        assertThat(ready.at("/service/environment").asString()).contains(profile);
        assertThat(ready.path("traceId").asString()).isEqualTo("0123456789abcdef0123456789abcdef");
        lines.forEach(mapper::readTree);
    }

    @Test void missingProductionPropertiesFailWithSafeRealLogs(CapturedOutput output) {
        assertThatThrownBy(() -> application("prod").run(
                "--spring.profiles.active=prod",
                "--spring.datasource.url=", "--spring.datasource.username=",
                "--spring.datasource.password="+SECRET))
            .hasStackTraceContaining("spring.datasource.url");
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @ParameterizedTest @ValueSource(strings={"spring.datasource.username", "spring.datasource.password"})
    void missingCredentialFailsBeforeConnectionWithSafeOutput(String key, CapturedOutput output) {
        var args = new java.util.ArrayList<>(List.of(properties("prod", SECRET)));
        args.removeIf(argument -> argument.startsWith("--" + key + "="));
        args.add("--" + key + "=");
        assertThatThrownBy(() -> application("prod").run(args.toArray(String[]::new)))
                .hasStackTraceContaining(key);
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @Test void rejectsDiagnosticLoggingBeforeStartup(CapturedOutput output) {
        var args = new java.util.ArrayList<>(List.of(properties("prod", SECRET)));
        args.add("--logging.level.root=DEBUG");
        assertThatThrownBy(() -> application("prod").run(args.toArray(String[]::new)))
                .hasStackTraceContaining("logging.level");
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @Test void rejectsMixedEnvironmentProfilesWithSafeOutput(CapturedOutput output) {
        assertThatThrownBy(() -> application("prod").profiles("local").run(properties("prod", SECRET)))
                .hasStackTraceContaining("exactly one environment");
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @Test void malformedConfigurationErrorDoesNotLogRejectedCredentials(CapturedOutput output) {
        assertThatThrownBy(() -> application("prod").run(
                "--spring.profiles.active=prod",
                "--spring.datasource.url=jdbc:postgresql://user:"+SECRET+"@db/app",
                "--spring.datasource.username=synthetic", "--spring.datasource.password="+SECRET))
            .hasStackTraceContaining("spring.datasource.url");
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    @Test void cloudDoesNotInheritLocalDefaults(CapturedOutput output) {
        try (var context = application("prod").run(properties("prod", SECRET))) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).contains("db.example.invalid")
                    .doesNotContain("localhost","marketplace_local");
        }
        assertThat(output.getAll()).doesNotContain(SECRET);
    }
}
