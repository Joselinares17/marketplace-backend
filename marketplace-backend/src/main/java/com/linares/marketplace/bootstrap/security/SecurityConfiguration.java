package com.linares.marketplace.bootstrap.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.ObjectMapper;
import com.linares.marketplace.bootstrap.http.ApiProblems;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(request -> request.getDispatcherType() == DispatcherType.ERROR
                        && request.getRequestURI().equals(request.getContextPath() + "/error")).permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .anyRequest().denyAll())
            .csrf(csrf -> csrf.spa())
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    write(mapper, response, ApiProblems.create(HttpStatus.UNAUTHORIZED,
                            "Se requiere autenticación.", request.getRequestURI())))
                .accessDeniedHandler((request, response, exception) ->
                    write(mapper, response, ApiProblems.create(HttpStatus.FORBIDDEN,
                            "La solicitud no está permitida.", request.getRequestURI()))));
        return http.build();
    }

    private static void write(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse response,
            ProblemDetail problem) throws java.io.IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), java.util.Map.of(
                "type", problem.getType(), "title", problem.getTitle(), "status", problem.getStatus(),
                "detail", problem.getDetail(), "instance", problem.getInstance(),
                "errors", problem.getProperties().get("errors")));
    }
}
