package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.agroruta.domain.model.Aviso;
import pe.agroruta.domain.model.HechoDelNegocio;
import pe.agroruta.domain.model.MiembroEquipo;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.PoliticaAvisos;
import pe.agroruta.domain.port.in.PublicarAvisosUseCase;
import pe.agroruta.domain.port.out.EquipoRepositoryPort;
import pe.agroruta.domain.port.out.NotificacionRepositoryPort;

public class AvisosService implements PublicarAvisosUseCase {

    private static final Logger REGISTRO = LoggerFactory.getLogger(AvisosService.class);

    private final PoliticaAvisos politica;
    private final EquipoRepositoryPort equipo;
    private final NotificacionRepositoryPort bandeja;
    private final Clock reloj;

    public AvisosService(PoliticaAvisos politica, EquipoRepositoryPort equipo, NotificacionRepositoryPort bandeja,
                         Clock reloj) {
        this.politica = politica;
        this.equipo = equipo;
        this.bandeja = bandeja;
        this.reloj = reloj;
    }

    @Override
    public void publicar(HechoDelNegocio hecho) {
        try {
            Instant ahora = reloj.instant();
            for (Aviso aviso : politica.avisosDe(hecho)) {
                for (MiembroEquipo destinatario : equipo.destinatarios(aviso.tipo().destinatarios())) {
                    bandeja.encolar(Notificacion.nueva(aviso, destinatario.correo(), ahora));
                }
            }
        } catch (RuntimeException e) {
            REGISTRO.warn("No se pudo dejar el aviso en la bandeja ({}): {}", hecho.getClass().getSimpleName(),
                    e.getMessage());
        }
    }
}
