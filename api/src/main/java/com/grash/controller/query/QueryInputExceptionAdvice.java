package com.grash.controller.query;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.Map;

@RestControllerAdvice(assignableTypes=MaintenanceQueryController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name="enhancement.query.enabled",havingValue="true")
public class QueryInputExceptionAdvice {
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> invalidInput(Exception exception) {
        return Map.of("success",false,"message","Invalid query input: use documented dates, numbers and enum values");
    }
}
