package pe.agroruta.domain.port.in;

/** Resuelve los pagos que se quedaron pendientes: los confirma con la pasarela, los vence o los libera. */
public interface ConciliarPagosUseCase {

    /** Devuelve cuántos pagos cambiaron de estado. */
    int conciliar();
}
