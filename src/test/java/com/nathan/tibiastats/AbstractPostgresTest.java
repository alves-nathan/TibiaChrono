package com.nathan.tibiastats;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractPostgresTest {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("tibiastats")
            .withUsername("tibia")
            .withPassword("secret");

    @BeforeAll static void start(){ POSTGRES.start(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r){
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.security.oauth2.resourceserver.jwt.secret-key", () -> "test-secret-key-which-is-very-long-and-random-1234567890");
        r.add("spring.flyway.enabled", () -> true);
    }
}