package com.linares.marketplace.bootstrap.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.SpringApplication;

class RuntimeConfigurationTests {
    private MockEnvironment valid(String profile) {
        var env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return env.withProperty("spring.datasource.url", "jdbc:postgresql://db.example.invalid:5432/app?sslmode=verify-full")
                .withProperty("spring.datasource.username", "synthetic_app")
                .withProperty("spring.datasource.password", "CONFIG_SECRET_CANARY");
    }

    @ParameterizedTest @ValueSource(strings={"local","ci","staging","prod"})
    void acceptsEachEnvironmentWithoutContactingServices(String profile) {
        assertThatCode(() -> RuntimeConfiguration.validate(valid(profile))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
        "spring.datasource.url,''",
        "spring.datasource.username,' '",
        "spring.datasource.password,''",
        "spring.datasource.url,jdbc:postgresql://user:CONFIG_SECRET_CANARY@db/app",
        "spring.datasource.url,jdbc:postgresql://db:99999/app",
        "spring.datasource.url,jdbc:postgresql://db/app?password=CONFIG_SECRET_CANARY",
        "spring.datasource.url,jdbc:postgresql://db/app?connectTimeout=0",
        "spring.datasource.url,jdbc:postgresql://db/app?sslmode=invalid",
        "server.port,65536",
        "spring.datasource.hikari.maximum-pool-size,0",
        "spring.datasource.hikari.connection-timeout,249"
    })
    void rejectsInvalidExistingBootPropertiesWithoutTheirValues(String key, String value) {
        assertThatThrownBy(() -> RuntimeConfiguration.validate(valid("ci").withProperty(key, value)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(key)
                .hasMessageNotContaining("CONFIG_SECRET_CANARY").hasNoCause();
    }

    @ParameterizedTest @ValueSource(strings={
        "jdbc:postgresql://localhost/app?sslmode=verify-full",
        "jdbc:postgresql://db.example.invalid/app",
        "jdbc:postgresql://db.example.invalid/app?sslmode=require"
    })
    void cloudRequiresVerifiedTlsAndNoLocalFallback(String url) {
        assertThatThrownBy(() -> RuntimeConfiguration.validate(valid("prod").withProperty("spring.datasource.url", url)))
                .hasMessageContaining("spring.datasource.url").hasMessageNotContaining(url);
    }

    @Test void missingEnvironmentAndMixedEnvironmentsFailButAuxiliaryProfilesWork() {
        var env = valid("local");
        env.setActiveProfiles("prod","local");
        assertThatThrownBy(() -> RuntimeConfiguration.validate(env)).hasMessageContaining("exactly one");
        env.setActiveProfiles("auxiliary");
        assertThatThrownBy(() -> RuntimeConfiguration.validate(env)).hasMessageContaining("exactly one");
        env.setActiveProfiles("ci", "auxiliary");
        assertThatCode(() -> RuntimeConfiguration.validate(env)).doesNotThrowAnyException();
    }

    @Test void cloudRejectsLocalCredentialsAndEphemeralPort() {
        assertThatThrownBy(() -> RuntimeConfiguration.validate(valid("prod")
                .withProperty("spring.datasource.username","marketplace_local"))).hasMessageContaining("credentials");
        assertThatThrownBy(() -> RuntimeConfiguration.validate(valid("prod").withProperty("server.port","0")))
                .hasMessageContaining("server.port");
    }

    @Test void diagnosticLoggingIsRejectedBeforeLoggerInitialization() {
        var env = valid("prod").withProperty("logging.level.root","DEBUG");
        assertThatThrownBy(() -> new EnvironmentPolicy().postProcessEnvironment(env, new SpringApplication()))
                .hasMessageContaining("logging.level");
        var candidate = valid("prod").withProperty("debug","CONFIG_SECRET_CANARY");
        assertThatThrownBy(() -> new EnvironmentPolicy().postProcessEnvironment(candidate, new SpringApplication()))
                .hasMessageNotContaining("CONFIG_SECRET_CANARY");
    }
}
