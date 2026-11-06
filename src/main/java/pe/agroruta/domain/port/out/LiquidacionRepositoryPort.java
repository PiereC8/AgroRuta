package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.Liquidacion;

public interface LiquidacionRepositoryPort {

    Liquidacion guardarNueva(Liquidacion liquidacion);

    Optional<Liquidacion> buscarPorLote(Long idLote);

    Optional<Liquidacion> buscarPorId(Long idLiquidacion);

    List<Liquidacion> listar(int pagina, int tamano);
}
