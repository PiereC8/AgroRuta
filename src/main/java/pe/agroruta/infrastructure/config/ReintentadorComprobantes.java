package pe.agroruta.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.port.in.EmitirComprobanteUseCase;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

@Component
@EnableScheduling
public class ReintentadorComprobantes {

    private static final Logger REGISTRO = LoggerFactory.getLogger(ReintentadorComprobantes.class);
    private static final int POR_NEGOCIO = 20;

    private final NegocioRepositoryPort negocios;
    private final EmitirComprobanteUseCase comprobantes;

    public ReintentadorComprobantes(NegocioRepositoryPort negocios, EmitirComprobanteUseCase comprobantes) {
        this.negocios = negocios;
        this.comprobantes = comprobantes;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT5M")
    public void reintentar() {
        for (Negocio negocio : negocios.listarActivos()) {
            try {
                int intentados = TenantContext.ejecutarEn(negocio.identificadorNegocio(),
                        () -> comprobantes.reintentarPendientes(POR_NEGOCIO));
                if (intentados > 0) {
                    REGISTRO.info("Comprobantes reintentados en {}: {}", negocio.identificadorNegocio(), intentados);
                }
            } catch (RuntimeException e) {
                REGISTRO.error("No se pudieron reintentar los comprobantes de {}: {}",
                        negocio.identificadorNegocio(), e.getMessage());
            }
        }
    }
}
