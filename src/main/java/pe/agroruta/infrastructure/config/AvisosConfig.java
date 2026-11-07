package pe.agroruta.infrastructure.config;

import java.math.BigDecimal;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.agroruta.application.service.AvisosService;
import pe.agroruta.application.service.NotificacionesService;
import pe.agroruta.application.service.RevisionNegocioService;
import pe.agroruta.domain.model.PoliticaAvisos;
import pe.agroruta.domain.port.in.ConsultarReportesUseCase;
import pe.agroruta.domain.port.in.PublicarAvisosUseCase;
import pe.agroruta.domain.port.in.RevisarNegocioUseCase;
import pe.agroruta.domain.port.out.EnvioCorreoPort;
import pe.agroruta.domain.port.out.EquipoRepositoryPort;
import pe.agroruta.domain.port.out.NotificacionRepositoryPort;
import pe.agroruta.domain.port.out.RevisionRepositoryPort;

/** Arma los avisos por correo: la política, la bandeja de salida y la revisión periódica. */
@Configuration
public class AvisosConfig {

    @Bean
    PublicarAvisosUseCase publicarAvisosUseCase(EquipoRepositoryPort equipo, NotificacionRepositoryPort bandeja,
                                                Clock reloj,
                                                @Value("${agroruta.avisos.merma-alta-porcentaje}") BigDecimal merma) {
        return new AvisosService(new PoliticaAvisos(merma), equipo, bandeja, reloj);
    }

    @Bean
    NotificacionesService notificacionesService(NotificacionRepositoryPort bandeja, EnvioCorreoPort correo,
                                                Clock reloj) {
        return new NotificacionesService(bandeja, correo, reloj);
    }

    @Bean
    RevisarNegocioUseCase revisarNegocioUseCase(PublicarAvisosUseCase avisos, RevisionRepositoryPort revision,
                                                ConsultarReportesUseCase reportes, Clock reloj,
                                                @Value("${agroruta.avisos.horas-comprobante-atascado}") int horas,
                                                @Value("${agroruta.avisos.dias-cobro-vencido}") int dias) {
        return new RevisionNegocioService(avisos, revision, reportes, reloj, horas, dias);
    }
}
