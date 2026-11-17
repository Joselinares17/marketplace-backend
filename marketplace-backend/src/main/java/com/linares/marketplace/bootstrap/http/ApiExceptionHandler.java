package com.linares.marketplace.bootstrap.http;

import java.util.ArrayList;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Set<String> CONSTRAINTS = Set.of("NotBlank", "NotNull", "NotEmpty",
            "Email", "Size", "Min", "Max", "Positive", "PositiveOrZero", "Pattern");

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ApiProblems.create(status, detail(ex, status),
                ((ServletWebRequest) request).getRequest().getRequestURI());
        var errors = new ArrayList<ApiProblems.FieldViolation>();
        if (ex instanceof MethodArgumentNotValidException validation) {
            validation.getBindingResult().getFieldErrors().forEach(error ->
                    errors.add(new ApiProblems.FieldViolation(error.getField(), safeCode(error.getCode()))));
            validation.getBindingResult().getGlobalErrors().forEach(error ->
                    errors.add(new ApiProblems.FieldViolation("$", safeCode(error.getCode()))));
        } else if (ex instanceof HandlerMethodValidationException validation && !validation.isForReturnValue()) {
            validation.getParameterValidationResults().forEach(result -> {
                var field = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(error -> {
                    var codes = error.getCodes();
                    errors.add(new ApiProblems.FieldViolation(field == null ? "parameter" : field,
                            safeCode(codes == null || codes.length == 0 ? null : codes[codes.length - 1])));
                });
            });
        }
        problem.setProperty("errors", errors);
        var responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers); // Preserve Allow, Accept and other framework headers.
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return super.handleExceptionInternal(ex, problem, responseHeaders, status, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex, WebRequest request) throws Exception {
        // These belong to the security filters, even if thrown by a controller.
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        return handleExceptionInternal(ex, null, new HttpHeaders(),
                HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private static String safeCode(String code) {
        return CONSTRAINTS.contains(code == null ? "" : code) ? code : "Invalid";
    }

    private static String detail(Exception ex, HttpStatusCode status) {
        if (status.is5xxServerError()) return "No se pudo completar la solicitud.";
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException)
            return "La solicitud contiene campos inválidos.";
        if (ex instanceof org.springframework.http.converter.HttpMessageNotReadableException)
            return "El cuerpo de la solicitud no se puede interpretar.";
        if (ex instanceof org.springframework.beans.TypeMismatchException)
            return "Un parámetro tiene un formato inválido.";
        return switch (status.value()) {
            case 400 -> "La solicitud no es válida.";
            case 404 -> "El recurso solicitado no existe.";
            case 405 -> "El método HTTP no está permitido.";
            case 406 -> "El formato de respuesta solicitado no está disponible.";
            case 415 -> "El tipo de contenido no está admitido.";
            default -> "La solicitud no se pudo procesar.";
        };
    }
}
