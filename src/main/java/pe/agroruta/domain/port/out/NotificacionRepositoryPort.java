package pe.agroruta.domain.port.out;

import java.time.Instant;
import java.util.List;

import pe.agroruta.domain.model.Notificacion;

/** La bandeja de salida de correos del negocio en curso. */
public interface NotificacionRepositoryPort {

    /** Deja el correo en la bandeja. Devuelve falso, sin error, si ese mismo aviso ya estaba para ese correo. */
    boolean encolar(Notificacion notificacion);

    /** Los pendientes cuya hora de intento ya llegó, los más antiguos primero. */
    List<Notificacion> pendientes(Instant ahora, int limite);

    void guardar(Notificacion notificacion);

    List<Notificacion> recientes(int limite);
}
