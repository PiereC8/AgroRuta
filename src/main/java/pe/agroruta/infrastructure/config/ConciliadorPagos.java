package pe.agroruta.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.port.in.ConciliarPagosUseCase;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

/** Cada 10 minutos, en cada negocio, resuelve los pagos que se quedaron pendientes. */
@Component
public class ConciliadorPagos {

    private static final Logger REGISTRO = LoggerFactory.getLogger(ConciliadorPagos.class);

    private final NegocioRepositoryPort negocios;
    private final ConciliarPagosUseCase conciliar;

    public ConciliadorPagos(NegocioRepositoryPort negocios, ConciliarPagosUseCase conciliar) {
        this.negocios = negocios;
        this.conciliar = conciliar;
    }

    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT10M")
    public void conciliar() {
        for (Negocio negocio : negocios.listarActivos()) {
            try {
                int cambiados = TenantContext.ejecutarEn(negocio.identificadorNegocio(), conciliar::conciliar);
                if (cambiados > 0) {
                    REGISTRO.info("Pagos conciliados en {}: {}", negocio.identificadorNegocio(), cambiados);
                }
            } catch (RuntimeException e) {
                REGISTRO.warn("No se pudieron conciliar los pagos de {}: {}", negocio.identificadorNegocio(),
                        e.getMessage());
            }
        }
    }
}
