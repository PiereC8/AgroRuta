package pe.agroruta.application.service;

import java.util.List;

import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.port.in.ConsultarLotesUseCase;
import pe.agroruta.domain.port.out.LoteRepositoryPort;

public class ConsultarLotesService implements ConsultarLotesUseCase {

    private final LoteRepositoryPort lotes;

    public ConsultarLotesService(LoteRepositoryPort lotes) {
        this.lotes = lotes;
    }

    @Override
    public List<Lote> listar(int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO_PAGINA) {
            throw new DominioException("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO_PAGINA);
        }
        return lotes.listar(pagina, tamano);
    }

    @Override
    public Lote obtener(Long idLote) {
        return lotes.buscarPorId(idLote).orElseThrow(() -> new LoteNoEncontradoException(idLote));
    }

    @Override
    public List<EventoLote> historial(Long idLote) {
        obtener(idLote);
        return lotes.historial(idLote);
    }
}
