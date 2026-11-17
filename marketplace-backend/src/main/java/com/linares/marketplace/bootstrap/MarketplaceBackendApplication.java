package com.linares.marketplace.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.modulith.Modulithic;

@SpringBootApplication(scanBasePackages = "com.linares.marketplace")
@AutoConfigurationPackage(basePackages = "com.linares.marketplace")
@Modulithic(additionalPackages = "com.linares.marketplace.modules")
public class MarketplaceBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketplaceBackendApplication.class, args);
	}

}
