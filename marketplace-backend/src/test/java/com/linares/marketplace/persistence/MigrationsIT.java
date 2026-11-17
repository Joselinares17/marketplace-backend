package com.linares.marketplace.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationsIT {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresImage.IMAGE);

    @Test
    void installsIdentityAndOfficialRegistryOnAnEmptyDatabase() {
        var flyway = migrations("fresh", null);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        flyway.validate();
        var jdbc = jdbc();
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema='fresh' AND table_name='users'", String.class))
                .containsExactlyInAnyOrder("id", "email", "created_at", "updated_at");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema='fresh' AND table_name='event_publication'", String.class))
                .containsExactlyInAnyOrder("id", "listener_id", "event_type", "serialized_event",
                        "publication_date", "completion_date", "status", "completion_attempts",
                        "last_resubmission_date");
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname='fresh' "
                + "AND tablename='event_publication'", String.class))
                .contains("event_publication_serialized_event_hash_idx",
                        "event_publication_by_completion_date_idx");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fresh.event_publication", Long.class)).isZero();
    }

    @Test
    void upgradesIdentityV1ToRegistryV2WithoutLosingDataAndCanRunAgain() {
        assertThat(migrations("upgrade", "1").migrate().migrationsExecuted).isEqualTo(1);
        jdbc().update("INSERT INTO upgrade.users(email) VALUES (?)", "migration@example.invalid");
        var before = jdbc().queryForMap("SELECT * FROM upgrade.users");
        var current = migrations("upgrade", null);
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc().queryForMap("SELECT * FROM upgrade.users")).isEqualTo(before);
        assertThat(current.migrate().migrationsExecuted).isZero();
        current.validate();
    }

    private Flyway migrations(String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }
}
