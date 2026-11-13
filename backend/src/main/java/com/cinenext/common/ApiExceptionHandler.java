package com.cinenext.common;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ProblemDetail business(BusinessException exception) { return ProblemDetail.forStatusAndDetail(exception.status(),exception.getMessage()); }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(org.springframework.web.bind.MethodArgumentNotValidException exception) {
        var fields = new LinkedHashMap<String,String>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage() == null ? "Check this field." : error.getDefaultMessage()));
        return invalidFields(fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableBody(HttpMessageNotReadableException exception) {
        // Never expose Jackson's raw message: it can contain submitted credentials or other values.
        for (Throwable cause=exception.getCause(); cause!=null; cause=cause.getCause()) {
            if (cause instanceof tools.jackson.databind.DatabindException mapping) {
                var field=mapping.getPath().stream().map(reference->reference.getPropertyName()).filter(name->name!=null).findFirst();
                if (field.isPresent()) {
                    String hint=switch(field.get()) {
                        case "releaseDate" -> "Choose a valid date in YYYY-MM-DD format.";
                        case "startsAt" -> "Choose a valid date and time with a timezone.";
                        case "durationMinutes" -> "Enter a whole number of minutes between 1 and 300.";
                        case "rating" -> "Enter a number between 0 and 10.";
                        default -> "The value has an invalid format.";
                    };
                    return invalidFields(Map.of(field.get(),hint));
                }
            }
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"The request could not be read. Check the entered values and try again.");
    }

    private ProblemDetail invalidFields(Map<String,String> fields) {
        String detail=fields.isEmpty()?"Check the required fields.":fields.entrySet().stream()
                .map(field->fieldLabel(field.getKey())+": "+field.getValue()).collect(Collectors.joining(" "));
        var problem=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,detail);
        problem.setProperty("fieldErrors",fields);
        return problem;
    }

    private String fieldLabel(String field) {
        return switch(field) {
            case "slug" -> "URL slug";
            case "releaseDate" -> "Release date";
            case "durationMinutes" -> "Duration";
            case "posterUrl" -> "Artwork";
            case "startsAt" -> "Start time";
            case "priceFromPaise" -> "Ticket price";
            default -> Character.toUpperCase(field.charAt(0))+field.substring(1).replaceAll("([A-Z])"," $1").toLowerCase();
        };
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    ProblemDetail duplicate(org.springframework.dao.DuplicateKeyException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"This record already exists or another request completed first. Refresh and try again.");
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ProblemDetail invalidInput(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Invalid query parameters. Use page 0–10000, size 1–50, dates as YYYY-MM-DD, and text up to 100 characters.");
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail databaseUnavailable(DataAccessException exception) {
        LOG.warn("Catalogue database unavailable: {}", exception.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The catalogue is temporarily unavailable. Please try again shortly.");
    }
}
