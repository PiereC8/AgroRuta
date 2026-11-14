package pe.agroruta.domain.port.out;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.Pedido;

public interface PedidoRepositoryPort {

    Pedido guardarNuevo(Pedido pedido);

    Optional<Pedido> buscarPorId(Long idPedido);

    /** Los pedidos del lote que no están cancelados: pendientes, despachados y entregados. */
    List<Pedido> vigentesDeLote(Long idLote);

    /** Kg comprometidos en pedidos no cancelados, por lote. Los lotes sin pedidos no aparecen. */
    Map<Long, BigDecimal> kgComprometidosPorLote(Collection<Long> idsLote);

    List<Pedido> listar(int pagina, int tamano);

    void actualizarEstado(Long idPedido, EstadoPedido nuevoEstado);
}
