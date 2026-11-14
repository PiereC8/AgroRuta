package pe.agroruta.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteConStock;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.port.in.ConsultarLotesUseCase;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;

public class ConsultarLotesService implements ConsultarLotesUseCase {

    private final LoteRepositoryPort lotes;
    private final PedidoRepositoryPort pedidos;

    public ConsultarLotesService(LoteRepositoryPort lotes, PedidoRepositoryPort pedidos) {
        this.lotes = lotes;
        this.pedidos = pedidos;
    }

    @Override
    public List<LoteConStock> listar(int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO_PAGINA) {
            throw new DominioException("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO_PAGINA);
        }
        List<Lote> pagina_ = lotes.listar(pagina, tamano);
        Map<Long, BigDecimal> comprometidos = pedidos.kgComprometidosPorLote(pagina_.stream().map(Lote::idLote).toList());
        return pagina_.stream()
                .map(l -> new LoteConStock(l, comprometidos.getOrDefault(l.idLote(), BigDecimal.ZERO.setScale(3))))
                .toList();
    }

    @Override
    public LoteConStock obtener(Long idLote) {
        Lote lote = lotes.buscarPorId(idLote).orElseThrow(() -> new LoteNoEncontradoException(idLote));
        return new LoteConStock(lote,
                pedidos.kgComprometidosPorLote(List.of(idLote)).getOrDefault(idLote, BigDecimal.ZERO.setScale(3)));
    }

    @Override
    public List<EventoLote> historial(Long idLote) {
        obtener(idLote);
        return lotes.historial(idLote);
    }
}
