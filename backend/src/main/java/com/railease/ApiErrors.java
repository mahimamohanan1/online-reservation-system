package com.railease;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Request could not be completed.":e.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,String>> invalid(MethodArgumentNotValidException e) {
        String message=e.getBindingResult().getFieldErrors().stream().findFirst().map(FieldError::getDefaultMessage).orElse("Please check the submitted details.");
        return ResponseEntity.badRequest().body(Map.of("message",message));
    }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<Map<String,String>> database(DataAccessException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message","The reservation service is temporarily unavailable. Please try again."));
    }
}
