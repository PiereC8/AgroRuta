package pe.agroruta.domain.port.in;

import java.util.Optional;

import pe.agroruta.domain.model.SesionEnlace;

public interface AutenticarEnlaceUseCase {

    Optional<SesionEnlace> resolver(String token);
}
