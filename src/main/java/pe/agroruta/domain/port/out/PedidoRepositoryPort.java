package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.Pedido;

public interface PedidoRepositoryPort {

    Pedido guardarNuevo(Pedido pedido);

    Optional<Pedido> buscarPorId(Long idPedido);

    boolean existeParaLote(Long idLote);

    List<Pedido> listar(int pagina, int tamano);

    void actualizarEstado(Long idPedido, EstadoPedido nuevoEstado);
}
