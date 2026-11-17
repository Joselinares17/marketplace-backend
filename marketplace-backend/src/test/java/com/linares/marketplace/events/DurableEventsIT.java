package com.linares.marketplace.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.linares.marketplace.bootstrap.MarketplaceBackendApplication;
import com.linares.marketplace.persistence.PostgresImage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
public class DurableEventsIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresImage.IMAGE);

    public record ProbeEvent(long userId) {
    }

    @Test
    void commitStoresBusinessAndPublicationInOneTransactionAndCompletesDelivery() {
        try (var context = start(false)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            assertThat(context.getBeansOfType(PlatformTransactionManager.class)).hasSize(1);
            assertThat(context.getBean(PlatformTransactionManager.class)).isInstanceOf(JdbcTransactionManager.class);
            long id = publish(context, "committed@example.invalid", false);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM probe_consumed WHERE user_id=?",
                        Long.class, id)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE "
                        + "(serialized_event::jsonb ->> 'userId') = ? AND completion_date IS NOT NULL AND status='COMPLETED'",
                        Long.class, Long.toString(id))).isEqualTo(1);
            });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE id=?", Long.class, id)).isEqualTo(1);
        }
    }

    @Test
    void rollbackLeavesNeitherBusinessNorPublicationNorConsumerEffects() {
        try (var context = start(false)) {
            var jdbc = context.getBean(JdbcTemplate.class);
            long publicationsBefore = jdbc.queryForObject("SELECT count(*) FROM event_publication", Long.class);
            long effectsBefore = jdbc.queryForObject("SELECT count(*) FROM probe_consumed", Long.class);
            assertThrows(IllegalStateException.class, () -> publish(context, "rollback@example.invalid", true));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email='rollback@example.invalid'",
                    Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_publication", Long.class))
                    .isEqualTo(publicationsBefore);
            assertThat(context.getBean(ProbeListener.class).invocationCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM probe_consumed", Long.class))
                    .isEqualTo(effectsBefore);
        }
    }

    @Test
    void failedPublicationIsRecoveredAfterClosingAndReplacingTheApplicationContext() {
        UUID publicationId;
        long userId;
        var first = start(true);
        try (first) {
            userId = publish(first, "restart@example.invalid", false);
            var jdbc = first.getBean(JdbcTemplate.class);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT status FROM event_publication WHERE (serialized_event::jsonb ->> 'userId') = ?",
                            String.class, Long.toString(userId))).isEqualTo("FAILED"));
            publicationId = jdbc.queryForObject("SELECT id FROM event_publication WHERE (serialized_event::jsonb ->> 'userId') = ?",
                    UUID.class, Long.toString(userId));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM probe_consumed WHERE user_id=?",
                    Long.class, userId)).isZero();
        }
        assertThat(first.isActive()).isFalse();
        try (var second = start(false)) {
            assertThat(second).isNotSameAs(first);
            var jdbc = second.getBean(JdbcTemplate.class);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(jdbc.queryForObject("SELECT status FROM event_publication WHERE id=?",
                        String.class, publicationId)).isEqualTo("COMPLETED");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM probe_consumed WHERE user_id=?",
                        Long.class, userId)).isEqualTo(1);
            });
            assertThat(jdbc.queryForObject("SELECT completion_attempts FROM event_publication WHERE id=?",
                    Integer.class, publicationId)).isGreaterThanOrEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id=?", String.class, userId))
                    .isEqualTo("restart@example.invalid");
        }
    }

    private ConfigurableApplicationContext start(boolean fail) {
        return new SpringApplicationBuilder(MarketplaceBackendApplication.class, ProbeConfiguration.class)
                .profiles("ci")
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + PostgresImage.jdbcUrl(POSTGRES),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--fixture.fail=" + fail);
    }

    private long publish(ConfigurableApplicationContext context, String email, boolean rollback) {
        var jdbc = context.getBean(JdbcTemplate.class);
        return new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status -> {
            long id = jdbc.queryForObject("INSERT INTO users(email) VALUES (?) RETURNING id", Long.class, email);
            context.publishEvent(new ProbeEvent(id));
            // The publication is already visible on the business transaction's connection before commit.
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE (serialized_event::jsonb ->> 'userId') = ?",
                    Long.class, Long.toString(id))).isEqualTo(1);
            if (rollback) {
                throw new IllegalStateException("Synthetic business rollback");
            }
            return id;
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class ProbeConfiguration {
        @Bean
        ProbeListener probeListener(JdbcTemplate jdbc, @Value("${fixture.fail:false}") boolean fail) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS probe_consumed (user_id BIGINT PRIMARY KEY)");
            return new ProbeListener(jdbc, fail);
        }
    }

    public static class ProbeListener {
        private final JdbcTemplate jdbc;
        private final boolean fail;
        final AtomicInteger invocations = new AtomicInteger();

        ProbeListener(JdbcTemplate jdbc, boolean fail) {
            this.jdbc = jdbc;
            this.fail = fail;
        }

        public int invocationCount() {
            return invocations.get();
        }

        @ApplicationModuleListener
        public void on(ProbeEvent event) {
            invocations.incrementAndGet();
            jdbc.update("INSERT INTO probe_consumed(user_id) VALUES (?) ON CONFLICT DO NOTHING", event.userId());
            if (fail) {
                throw new IllegalStateException("Controlled synthetic listener failure");
            }
        }
    }
}
