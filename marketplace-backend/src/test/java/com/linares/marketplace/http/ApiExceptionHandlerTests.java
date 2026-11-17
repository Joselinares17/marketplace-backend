package com.linares.marketplace.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import java.util.UUID;
import java.util.concurrent.Callable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import com.linares.marketplace.bootstrap.http.ApiExceptionHandler;

class ApiExceptionHandlerTests {
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Fixture())
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test void validInput() throws Exception {
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@example.invalid\"}")).andExpect(status().isOk());
    }

    @Test void invalidBodyHasSafeFieldErrors() throws Exception {
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"SECRET_REJECTED\"}"))
            .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.instance").value("/fixture"))
            .andExpect(jsonPath("$.errors[0].field").value("email"))
            .andExpect(jsonPath("$.errors[0].code").value("Email"))
            .andExpect(content().string(not(containsString("SECRET_REJECTED"))));
    }

    @Test void malformedJson() throws Exception {
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON).content("{\"email\":"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors").isEmpty())
            .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test void invalidUuid() throws Exception {
        mvc.perform(get("/fixture/not-a-uuid")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Un parámetro tiene un formato inválido."));
    }

    @Test void validUuidRemainsAPublicIdentifierWithoutChangingDatabaseKeys() throws Exception {
        var id = UUID.randomUUID();
        mvc.perform(get("/fixture/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("$").value(id.toString()));
    }

    @Test void unacceptableResponseFormatKeeps406() throws Exception {
        mvc.perform(get("/fixture/" + UUID.randomUUID()).accept(MediaType.APPLICATION_XML))
            .andExpect(status().isNotAcceptable()).andExpect(jsonPath("$.status").value(406));
    }

    @Test void missingRequiredParameterIs400() throws Exception {
        mvc.perform(get("/fixture/parameters")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test void validatesRequestParametersWithoutEchoingValues() throws Exception {
        mvc.perform(get("/fixture/parameters").param("limit", "0"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("limit"))
            .andExpect(jsonPath("$.errors[0].code").value("Min"));
    }

    @Test void methodNotAllowedPreservesAllow() throws Exception {
        mvc.perform(delete("/fixture")).andExpect(status().isMethodNotAllowed())
            .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
            .andExpect(jsonPath("$.status").value(405));
    }

    @Test void unsupportedContentType() throws Exception {
        mvc.perform(post("/fixture").contentType(MediaType.TEXT_PLAIN).content("secret"))
            .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
    }

    @Test void missingRouteInMvc() throws Exception {
        mvc.perform(get("/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test void unexpectedFailureIsGenericAndDoesNotEchoQuery() throws Exception {
        mvc.perform(get("/fixture/failure").param("password", "SECRET_QUERY"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.detail").value("No se pudo completar la solicitud."))
            .andExpect(content().string(not(containsString("SELECT"))))
            .andExpect(content().string(not(containsString("SECRET_QUERY"))));
    }

    @Test void responseStatusExceptionKeepsItsStatusButSanitizesItsReason() throws Exception {
        mvc.perform(get("/fixture/conflict")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(content().string(not(containsString("SECRET_REASON"))));
    }

    @Test void asyncFailureAlsoUsesTheAdvice() throws Exception {
        var result = mvc.perform(get("/fixture/async")).andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500));
    }

    @Test void invalidControllerReturnValueIsAServerError() throws Exception {
        mvc.perform(get("/fixture/invalid-result")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500)).andExpect(jsonPath("$.errors").isEmpty());
    }

    public record Input(@NotBlank @Email String email) {}

    @RestController
    static class Fixture {
        @PostMapping(value="/fixture", consumes=MediaType.APPLICATION_JSON_VALUE)
        Input create(@Valid @RequestBody Input input) { return input; }
        @GetMapping("/fixture/{id}")
        UUID id(@PathVariable UUID id) { return id; }
        @GetMapping("/fixture/parameters")
        int parameters(@RequestParam @Min(1) int limit) { return limit; }
        @GetMapping("/fixture/failure")
        void failure() { throw new IllegalStateException("SELECT password FROM users SECRET"); }
        @GetMapping("/fixture/invalid-result")
        @NotNull String invalidResult() { return null; }
        @GetMapping("/fixture/conflict")
        void conflict() { throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, "SECRET_REASON"); }
        @GetMapping("/fixture/async")
        Callable<String> async() { return () -> { throw new IllegalStateException("SECRET_ASYNC"); }; }
    }
}
