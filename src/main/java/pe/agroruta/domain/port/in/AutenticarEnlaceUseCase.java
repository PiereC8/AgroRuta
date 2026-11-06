package pe.agroruta.domain.port.in;

import java.util.Optional;

import pe.agroruta.domain.model.SesionEnlace;

public interface AutenticarEnlaceUseCase {

    /** Devuelve la sesión del enlace, o vacío si el token no existe, venció o fue reemplazado. */
    Optional<SesionEnlace> resolver(String token);
}
