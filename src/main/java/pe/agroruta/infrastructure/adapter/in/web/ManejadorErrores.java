package pe.agroruta.infrastructure.adapter.in.web;

import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.CredencialesInvalidasException;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;

@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<Map<String, String>> credencialesInvalidas(CredencialesInvalidasException e) {
        return respuesta(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<Map<String, String>> noEncontrado(RecursoNoEncontradoException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictoDominioException.class)
    ResponseEntity<Map<String, String>> conflicto(ConflictoDominioException e) {
        return respuesta(HttpStatus.CONFLICT, e.getMessage());
    }

    /** La pasarela o el proveedor no respondieron: quien llama debe reintentar. */
    @ExceptionHandler(ServicioExternoNoDisponibleException.class)
    ResponseEntity<Map<String, String>> servicioExterno(ServicioExternoNoDisponibleException e) {
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "El servicio externo no está disponible, intenta de nuevo");
    }

    /** Red de seguridad: si dos peticiones chocan en una restricción única, responde 409 y no 500. */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<Map<String, String>> duplicado(DuplicateKeyException e) {
        return respuesta(HttpStatus.CONFLICT, "Ya existe un registro con esos datos");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido");
    }

    @ExceptionHandler(DominioException.class)
    ResponseEntity<Map<String, String>> reglaDeDominio(DominioException e) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> datosInvalidos(MethodArgumentNotValidException e) {
        String campos = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));
        return respuesta(HttpStatus.BAD_REQUEST, "Hay datos obligatorios vacíos o inválidos: " + campos);
    }

    private static ResponseEntity<Map<String, String>> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", mensaje));
    }
}
