package com.linares.marketplace.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.linares.marketplace.bootstrap.MarketplaceBackendApplication;
import com.linares.marketplace.persistence.PostgresImage;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes=MarketplaceBackendApplication.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityHttpIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresImage.IMAGE);

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired WebApplicationContext web;
    @Autowired ConfigurableApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test void publicHealthHasOnlyStatus() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"UP\"}", org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test void onlyHealthIsActuallyExposedAndNoSampleUserExists() {
        assertThat(context.getBean(WebEndpointsSupplier.class).getEndpoints())
            .extracting(endpoint -> endpoint.getEndpointId().toString()).containsExactly("health");
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings={"/api/v1/products", "/internal", "/actuator/env", "/actuator/configprops",
            "/actuator/beans", "/actuator/heapdump", "/actuator/health/db", "/actuator/health/liveness",
            "/actuator", "/error", "/login"})
    void anonymousRequestsAreClosed(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.errors").isEmpty())
            .andExpect(header().doesNotExist("WWW-Authenticate")).andExpect(header().doesNotExist("Location"));
    }

    @Test void authenticatedUserStillCannotAccessAnUndeclaredRoute() throws Exception {
        mvc.perform(get("/internal").with(user("synthetic"))).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test void csrfIsCheckedBeforeAuthorization() throws Exception {
        mvc.perform(post("/internal")).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/internal").with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        mvc.perform(post("/internal").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal").with(user("synthetic")).with(csrf())).andExpect(status().isForbidden());
    }

    @Test void realCsrfCookieCanBeSubmittedAsAHeaderWithoutWeakeningAuthorization() throws Exception {
        var response = mvc.perform(post("/internal")).andExpect(status().isForbidden()).andReturn().getResponse();
        var token = response.getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        mvc.perform(post("/internal").cookie(token).header("X-XSRF-TOKEN", token.getValue()))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test void healthIsNotAllowedForOtherMethods() throws Exception {
        mvc.perform(post("/actuator/health").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(head("/actuator/health")).andExpect(status().isUnauthorized());
    }

    @Test void internalErrorDispatchIsSafeButCannotDispatchToASensitiveRoute() throws Exception {
        mvc.perform(get("/error").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_MESSAGE, "SECRET_SQL"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value("No se pudo completar la solicitud."));
        mvc.perform(get("/actuator/env").with(request -> { request.setDispatcherType(DispatcherType.ERROR); return request; })).andExpect(status().isUnauthorized());
    }

    @Test void asyncDispatchDoesNotBypassAuthorization() throws Exception {
        mvc.perform(get("/internal").with(request -> { request.setDispatcherType(DispatcherType.ASYNC); return request; })).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health").with(request -> { request.setDispatcherType(DispatcherType.ASYNC); return request; })).andExpect(status().isOk());
    }

    @Test void publicHealthDoesNotGrantCorsAccess() throws Exception {
        mvc.perform(get("/actuator/health").header("Origin", "https://untrusted.example"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
