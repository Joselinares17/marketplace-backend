package com.linares.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import com.linares.marketplace.bootstrap.MarketplaceBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = MarketplaceBackendApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketplaceBackendApplicationTests {

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
