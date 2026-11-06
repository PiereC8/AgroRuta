package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;

public interface ConsultarLotesUseCase {

    int TAMANO_MAXIMO_PAGINA = 200;

    List<Lote> listar(int pagina, int tamano);

    Lote obtener(Long idLote);

    List<EventoLote> historial(Long idLote);
}
