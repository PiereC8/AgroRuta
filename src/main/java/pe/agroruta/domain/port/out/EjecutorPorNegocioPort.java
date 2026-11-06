package pe.agroruta.domain.port.out;

import java.util.function.Supplier;

public interface EjecutorPorNegocioPort {

    <T> T ejecutarEn(String identificadorNegocio, Supplier<T> accion);
}
