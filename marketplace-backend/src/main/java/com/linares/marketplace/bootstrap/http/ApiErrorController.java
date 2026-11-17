package com.linares.marketplace.bootstrap.http;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Safe fallback for servlet ERROR dispatches that did not go through the MVC advice. */
@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("/error")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        var attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = attribute instanceof Integer value && value >= 400 && value <= 599 ? value : 500;
        var problem = ApiProblems.create(HttpStatusCode.valueOf(code),
                code >= 500 ? "No se pudo completar la solicitud." : "La solicitud no se pudo procesar.",
                "/error");
        return ResponseEntity.status(code).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
