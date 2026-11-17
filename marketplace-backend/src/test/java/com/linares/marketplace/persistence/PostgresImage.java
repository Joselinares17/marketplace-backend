package com.linares.marketplace.persistence;

import org.testcontainers.utility.DockerImageName;

/** Same PostgreSQL version and multiarch digest as the B004 Compose. */
public final class PostgresImage {
    // Testcontainers 2.0.5 parses a tag+digest reference incorrectly; use the same digest directly.
    public static final DockerImageName IMAGE = DockerImageName.parse(
            "postgres@sha256:a49f7438ba03589f9b1ca062f2da82e899b48ea1dc50d2449fa09a70be83ebca");

    private PostgresImage() {
    }
}
