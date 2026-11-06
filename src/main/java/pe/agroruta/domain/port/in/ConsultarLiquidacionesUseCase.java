package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.Liquidacion;

public interface ConsultarLiquidacionesUseCase {

    Liquidacion obtenerPorLote(Long idLote);

    List<Liquidacion> listar(int pagina, int tamano);
}
