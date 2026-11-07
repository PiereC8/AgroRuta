package pe.agroruta.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Un correo en la bandeja de salida: a quién, qué y en qué va el envío. */
public record Notificacion(Long idNotificacion, String claveUnica, TipoAviso tipo, String correoDestino,
                           String asunto, String cuerpo, EstadoNotificacion estado, int intentos, String ultimoError,
                           Instant fechaCreacion, Instant proximoIntento, Instant fechaEnvio) {

    public static final int MAXIMO_INTENTOS = 5;
    private static final List<Duration> ESPERAS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofHours(1));
    private static final int LARGO_MAXIMO_ERROR = 300;

    public static Notificacion nueva(Aviso aviso, String correoDestino, Instant ahora) {
        return new Notificacion(null, aviso.clave() + "|" + correoDestino, aviso.tipo(), correoDestino,
                aviso.asunto(), aviso.cuerpo(), EstadoNotificacion.PENDIENTE, 0, null, ahora, ahora, null);
    }

    public Notificacion comoEnviada(Instant ahora) {
        return new Notificacion(idNotificacion, claveUnica, tipo, correoDestino, asunto, cuerpo,
                EstadoNotificacion.ENVIADA, intentos, null, fechaCreacion, proximoIntento, ahora);
    }

    public Notificacion conFallo(String error, Instant ahora) {
        int total = intentos + 1;
        String recortado = error == null ? null
                : error.length() > LARGO_MAXIMO_ERROR ? error.substring(0, LARGO_MAXIMO_ERROR) : error;
        if (total >= MAXIMO_INTENTOS) {
            return new Notificacion(idNotificacion, claveUnica, tipo, correoDestino, asunto, cuerpo,
                    EstadoNotificacion.FALLIDA, total, recortado, fechaCreacion, proximoIntento, null);
        }
        return new Notificacion(idNotificacion, claveUnica, tipo, correoDestino, asunto, cuerpo,
                EstadoNotificacion.PENDIENTE, total, recortado, fechaCreacion, ahora.plus(ESPERAS.get(total - 1)),
                null);
    }

    public Notificacion conId(Long nuevoId) {
        return new Notificacion(nuevoId, claveUnica, tipo, correoDestino, asunto, cuerpo, estado, intentos,
                ultimoError, fechaCreacion, proximoIntento, fechaEnvio);
    }
}
