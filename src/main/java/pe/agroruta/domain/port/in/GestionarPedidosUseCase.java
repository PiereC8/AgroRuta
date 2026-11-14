package pe.agroruta.domain.port.in;

import java.math.BigDecimal;
import java.util.List;

import pe.agroruta.domain.model.Pedido;

public interface GestionarPedidosUseCase {

    Pedido registrar(Comando comando);

    List<Pedido> listar(int pagina, int tamano);

    Pedido obtener(Long idPedido);

    /** cantidadKg vacío = todo lo que quede disponible del lote. */
    record Comando(Long idComprador, Long idLote, Long idDestinoVenta, BigDecimal cantidadKg,
                   BigDecimal precioAcordadoPorKg, String usuarioRegistro) {
    }
}
