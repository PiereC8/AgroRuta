package pe.agroruta.application.service;

import java.time.Clock;
import java.util.Optional;

import pe.agroruta.domain.model.EnlaceEnvio;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.port.in.AutenticarEnlaceUseCase;
import pe.agroruta.domain.port.out.EnlaceEnvioRepositoryPort;
import pe.agroruta.domain.port.out.GeneradorTokenPort;

public class AutenticarEnlaceService implements AutenticarEnlaceUseCase {

    private final EnlaceEnvioRepositoryPort enlaces;
    private final GeneradorTokenPort generador;
    private final Clock reloj;

    public AutenticarEnlaceService(EnlaceEnvioRepositoryPort enlaces, GeneradorTokenPort generador, Clock reloj) {
        this.enlaces = enlaces;
        this.generador = generador;
        this.reloj = reloj;
    }

    @Override
    public Optional<SesionEnlace> resolver(String token) {
        if (token == null || token.isBlank() || token.length() > 200) {
            return Optional.empty();
        }
        return enlaces.buscarVigentePorHash(generador.hash(token), reloj.instant()).map(EnlaceEnvio::sesion);
    }
}
