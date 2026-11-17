package com.linares.marketplace.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.linares.marketplace.bootstrap.MarketplaceBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@org.springframework.test.context.ActiveProfiles("ci")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes = MarketplaceBackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketplaceBackendApplicationIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresImage.IMAGE);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> PostgresImage.jdbcUrl(POSTGRES));
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

	@Autowired
	ConfigurableApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.isActive()).isTrue();
		assertThat(((WebServerApplicationContext) context).getWebServer().getPort()).isPositive();
	}

	@Test
	void scansTheWholeApplicationForComponentsAndFuturePersistence() {
		assertThat(MarketplaceBackendApplication.class.getAnnotation(SpringBootApplication.class)
				.scanBasePackages()).containsExactly("com.linares.marketplace");
		assertThat(AutoConfigurationPackages.get(context.getBeanFactory()))
				.contains("com.linares.marketplace");
	}

}
