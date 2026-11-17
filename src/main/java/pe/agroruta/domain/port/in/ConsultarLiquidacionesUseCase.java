package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.Liquidacion;

// RFF-12: puerto de entrada para consultar liquidaciones.
public interface ConsultarLiquidacionesUseCase {

    Liquidacion obtenerPorEnvio(Long idEnvio);

    List<Liquidacion> listar(int pagina, int tamano);
}
