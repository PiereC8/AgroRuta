package pe.agroruta.infrastructure.adapter.in.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.agroruta.domain.model.CredencialesInvalidasException;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.NegocioYaExisteException;

@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(NegocioYaExisteException.class)
    ResponseEntity<Map<String, String>> yaExiste(NegocioYaExisteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<Map<String, String>> credencialesInvalidas(CredencialesInvalidasException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(LoteNoEncontradoException.class)
    ResponseEntity<Map<String, String>> loteNoEncontrado(LoteNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "El cuerpo de la petición no es válido"));
    }

    @ExceptionHandler(DominioException.class)
    ResponseEntity<Map<String, String>> reglaDeDominio(DominioException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> datosInvalidos(MethodArgumentNotValidException e) {
        String campos = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Hay datos obligatorios vacíos o inválidos: " + campos));
    }
}
