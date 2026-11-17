package com.linares.marketplace.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linares.marketplace.bootstrap.MarketplaceBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

class ModuleArchitectureTests {

    @Test
    void detectsExactlyTheNineBusinessModules() {
        var modules = ApplicationModules.of(MarketplaceBackendApplication.class);

        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder("identity", "sellers", "catalog", "orders",
                        "payments", "shipping", "disputes", "compliance", "notifications");
        modules.forEach(module -> {
            assertThat(module.getBasePackage().getName())
                    .isEqualTo("com.linares.marketplace.modules." + module.getIdentifier());
            assertThat(module.isOpen()).isFalse();
        });
    }

    @Test
    void realArchitectureRespectsModuleBoundaries() {
        ApplicationModules.of(MarketplaceBackendApplication.class).verify();
    }

    @Test
    void rejectsAccessToAnotherModulesInternalType() {
        // Fixtures live only in test-classes; production verification keeps the default importer.
        var modules = ApplicationModules.of("example.marketplacefixtures.internalaccess", location -> true);
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder("consumer", "producer");

        var violations = assertThrows(Violations.class, modules::verify);

        assertThat(violations.getMessages()).anySatisfy(message -> assertThat(message)
                .contains("non-exposed type",
                        "example.marketplacefixtures.internalaccess.producer.internal.Secret",
                        "consumer", "producer"));
    }

    @Test
    void rejectsCircularDependenciesBetweenModules() {
        var modules = ApplicationModules.of("example.marketplacefixtures.cycle", location -> true);
        assertThat(modules.stream().map(module -> module.getIdentifier().toString()))
                .containsExactlyInAnyOrder("alpha", "beta");

        var violations = assertThrows(Violations.class, modules::verify);

        assertThat(violations.getMessages()).anySatisfy(message -> assertThat(message)
                .contains("Cycle detected", "alpha", "beta",
                        "example.marketplacefixtures.cycle.alpha.Alpha",
                        "example.marketplacefixtures.cycle.beta.Beta"));
    }
}
