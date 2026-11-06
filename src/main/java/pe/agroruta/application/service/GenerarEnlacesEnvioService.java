package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import pe.agroruta.domain.model.EnlaceEnvio;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.GenerarEnlacesEnvioUseCase;
import pe.agroruta.domain.port.out.EnlaceEnvioRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.GeneradorTokenPort;

public class GenerarEnlacesEnvioService implements GenerarEnlacesEnvioUseCase {

    public static final Duration VIGENCIA = Duration.ofDays(30);

    private final EnvioRepositoryPort envios;
    private final EnlaceEnvioRepositoryPort enlaces;
    private final GeneradorTokenPort generador;
    private final Clock reloj;

    public GenerarEnlacesEnvioService(EnvioRepositoryPort envios, EnlaceEnvioRepositoryPort enlaces,
                                      GeneradorTokenPort generador, Clock reloj) {
        this.envios = envios;
        this.enlaces = enlaces;
        this.generador = generador;
        this.reloj = reloj;
    }

    @Override
    public EnlacesGenerados generar(String identificadorNegocio, Long idEnvio) {
        Envio envio = envios.buscarPorId(idEnvio)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + idEnvio));
        // Un envío entregado ya no comparte ubicación, pero el comprador sí necesita su enlace para pagar.
        boolean entregado = envio.estadoEnvio() == EstadoEnvio.ENTREGADO;
        Instant expira = reloj.instant().plus(VIGENCIA);
        String tokenConductor = entregado ? null : generador.nuevoToken();
        String tokenComprador = generador.nuevoToken();

        enlaces.desactivarDeEnvio(identificadorNegocio, idEnvio);
        if (!entregado) {
            enlaces.guardar(new EnlaceEnvio(identificadorNegocio, idEnvio, TipoEnlace.CONDUCTOR,
                    generador.hash(tokenConductor), expira));
        }
        enlaces.guardar(new EnlaceEnvio(identificadorNegocio, idEnvio, TipoEnlace.COMPRADOR,
                generador.hash(tokenComprador), expira));
        return new EnlacesGenerados(tokenConductor, tokenComprador, expira);
    }
}
