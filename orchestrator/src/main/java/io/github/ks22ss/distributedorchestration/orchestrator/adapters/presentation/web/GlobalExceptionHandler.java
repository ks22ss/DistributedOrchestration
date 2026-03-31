package io.github.ks22ss.distributedorchestration.orchestrator.adapters.presentation.web;

import io.github.ks22ss.distributedorchestration.orchestrator.domain.exception.DuplicateWorkflowException;
import io.github.ks22ss.distributedorchestration.orchestrator.domain.exception.InvalidWorkflowException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain and validation errors to HTTP responses. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidWorkflowException.class)
    public ResponseEntity<Map<String, String>> handleInvalidWorkflow(InvalidWorkflowException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateWorkflowException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateWorkflowException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }
}

