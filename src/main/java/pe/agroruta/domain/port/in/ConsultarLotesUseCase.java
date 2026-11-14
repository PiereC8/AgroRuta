package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.LoteConStock;

public interface ConsultarLotesUseCase {

    int TAMANO_MAXIMO_PAGINA = 200;

    List<LoteConStock> listar(int pagina, int tamano);

    LoteConStock obtener(Long idLote);

    List<EventoLote> historial(Long idLote);
}
