package com.linares.marketplace.bootstrap.http;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Shared RFC 9457 representation for MVC and security; never accepts exception messages. */
public final class ApiProblems {
    private ApiProblems() {}

    public static ProblemDetail create(HttpStatusCode status, String detail, String path) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        var known = HttpStatus.resolve(status.value());
        problem.setTitle(known == null ? "Error HTTP" : known.getReasonPhrase());
        problem.setInstance(URI.create(path));
        problem.setProperty("errors", List.of());
        return problem;
    }

    public record FieldViolation(String field, String code) {}
}
