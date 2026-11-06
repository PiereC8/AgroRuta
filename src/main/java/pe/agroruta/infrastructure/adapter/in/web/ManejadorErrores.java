package pe.agroruta.infrastructure.adapter.in.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.NegocioYaExisteException;

@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(NegocioYaExisteException.class)
    ResponseEntity<Map<String, String>> yaExiste(NegocioYaExisteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DominioException.class)
    ResponseEntity<Map<String, String>> reglaDeDominio(DominioException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> datosInvalidos(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Hay datos obligatorios vacíos o inválidos"));
    }
}
