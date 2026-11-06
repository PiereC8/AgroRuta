package pe.agroruta.domain.port.out;

import java.util.function.Supplier;

/** Ejecuta varias operaciones como una sola: o se guardan todas o no se guarda ninguna. */
public interface TransaccionPort {

    <T> T ejecutar(Supplier<T> accion);
}
