package pe.agroruta.domain.port.out;

import java.util.function.Supplier;

/** Ejecuta algo dentro de la base de un negocio concreto, para quien llega sin sesión (por ejemplo, un webhook). */
public interface EjecutorPorNegocioPort {

    <T> T ejecutarEn(String identificadorNegocio, Supplier<T> accion);
}
