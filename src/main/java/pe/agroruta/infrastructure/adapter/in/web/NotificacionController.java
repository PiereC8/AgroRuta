package pe.agroruta.infrastructure.adapter.in.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.agroruta.domain.model.EstadoNotificacion;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.TipoAviso;
import pe.agroruta.domain.port.in.ConsultarNotificacionesUseCase;
import pe.agroruta.domain.port.in.DespacharNotificacionesUseCase;

/** Historial de los correos del negocio y el envío de prueba. Solo el Dueño llega aquí. */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private static final int MAXIMO = 200;

    private final ConsultarNotificacionesUseCase consultas;
    private final DespacharNotificacionesUseCase envios;

    public NotificacionController(ConsultarNotificacionesUseCase consultas, DespacharNotificacionesUseCase envios) {
        this.consultas = consultas;
        this.envios = envios;
    }

    @GetMapping
    public List<NotificacionRespuesta> recientes(@RequestParam(defaultValue = "50") int limite) {
        return consultas.recientes(Math.max(1, Math.min(limite, MAXIMO))).stream()
                .map(NotificacionRespuesta::de).toList();
    }

    @PostMapping("/prueba")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void probar(@Valid @RequestBody PruebaSolicitud solicitud) {
        envios.probar(solicitud.correo());
    }

    public record PruebaSolicitud(@NotBlank @Size(max = 120) String correo) {
    }

    public record NotificacionRespuesta(Long idNotificacion, TipoAviso tipoAviso, String correoDestino, String asunto,
                                        EstadoNotificacion estado, int intentos, String ultimoError,
                                        Instant fechaCreacion, Instant fechaEnvio) {
        static NotificacionRespuesta de(Notificacion n) {
            return new NotificacionRespuesta(n.idNotificacion(), n.tipo(), n.correoDestino(), n.asunto(), n.estado(),
                    n.intentos(), n.ultimoError(), n.fechaCreacion(), n.fechaEnvio());
        }
    }
}
