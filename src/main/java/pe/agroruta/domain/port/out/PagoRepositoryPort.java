package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.Pago;

public interface PagoRepositoryPort {

    Pago guardarNuevo(Pago pago);

    void actualizar(Pago pago);

    Optional<Pago> buscarPorId(Long idPago);

    Optional<Pago> buscarPorClave(String claveIdempotencia);

    boolean existeAprobadoDe(Long idLiquidacion);

    boolean existePendienteDe(Long idLiquidacion);

    Optional<Pago> ultimoDeLiquidacion(Long idLiquidacion);

    List<Pago> listar(int pagina, int tamano);
}
