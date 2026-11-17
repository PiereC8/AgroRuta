package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.LoteConStock;

// RFF-05: puerto de entrada para listar lotes y ver su historial.
public interface ConsultarLotesUseCase {

    int TAMANO_MAXIMO_PAGINA = 200;

    List<LoteConStock> listar(int pagina, int tamano);

    LoteConStock obtener(Long idLote);

    List<EventoLote> historial(Long idLote);
}
