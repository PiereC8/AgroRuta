package pe.agroruta.domain.port.out;

public interface CorrelativoPort {

    /** El siguiente número de una serie. Nunca entrega el mismo número dos veces, aunque haya peticiones a la vez. */
    int siguiente(String serie);
}
