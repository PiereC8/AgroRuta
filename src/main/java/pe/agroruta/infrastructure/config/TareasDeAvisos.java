package pe.agroruta.infrastructure.config;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.port.in.DespacharNotificacionesUseCase;
import pe.agroruta.domain.port.in.RevisarNegocioUseCase;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

/**
 * Las tareas que mantienen los avisos al día. Cada una recorre los negocios activos y un fallo en uno no detiene
 * a los demás. Corren en la hora de Lima.
 */
@Component
public class TareasDeAvisos {

    private static final Logger REGISTRO = LoggerFactory.getLogger(TareasDeAvisos.class);
    private static final String LIMA = "America/Lima";

    private final NegocioRepositoryPort negocios;
    private final RevisarNegocioUseCase revision;
    private final DespacharNotificacionesUseCase notificaciones;

    public TareasDeAvisos(NegocioRepositoryPort negocios, RevisarNegocioUseCase revision,
                          DespacharNotificacionesUseCase notificaciones) {
        this.negocios = negocios;
        this.revision = revision;
        this.notificaciones = notificaciones;
    }

    /** Cada minuto: busca lo que acaba de pasar y envía lo que esté en la bandeja. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void cadaMinuto() {
        porNegocio("revisar lo reciente", n -> revision.revisarRecientes());
        porNegocio("enviar los correos pendientes", n -> {
            int enviados = notificaciones.despachar();
            if (enviados > 0) {
                REGISTRO.info("Correos enviados en {}: {}", n.identificadorNegocio(), enviados);
            }
        });
    }

    /** Cada hora en punto y cinco: comprobantes y cobros que se quedaron sin atender. */
    @Scheduled(cron = "0 5 * * * *", zone = LIMA)
    public void cadaHora() {
        porNegocio("revisar lo pendiente", n -> revision.revisarPendientes());
    }

    /** Los lunes a las 7:00: el resumen de la semana anterior. */
    @Scheduled(cron = "0 0 7 * * MON", zone = LIMA)
    public void resumenSemanal() {
        porNegocio("enviar el resumen semanal", n -> revision.enviarResumenSemanal());
    }

    private void porNegocio(String tarea, Consumer<Negocio> accion) {
        for (Negocio negocio : negocios.listarActivos()) {
            try {
                TenantContext.ejecutarEn(negocio.identificadorNegocio(), () -> {
                    accion.accept(negocio);
                    return null;
                });
            } catch (RuntimeException e) {
                REGISTRO.error("No se pudo {} en {}: {}", tarea, negocio.identificadorNegocio(), e.getMessage());
            }
        }
    }
}
