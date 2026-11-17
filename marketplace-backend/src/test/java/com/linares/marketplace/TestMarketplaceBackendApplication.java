package com.linares.marketplace;

import com.linares.marketplace.bootstrap.MarketplaceBackendApplication;
import org.springframework.boot.SpringApplication;

public class TestMarketplaceBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(MarketplaceBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
