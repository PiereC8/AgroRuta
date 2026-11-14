package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reglas del stock de un lote. Un lote se puede repartir entre varios pedidos mientras lo comprometido no pase de
 * lo cosechado. Los kg de un pedido cancelado vuelven al stock.
 *
 * <pre>
 * kg disponibles = kg del lote - kg de los pedidos no cancelados (pendientes, despachados y entregados)
 * </pre>
 *
 * El estado del lote no se mueve paso a paso: se calcula a partir de sus pedidos y envíos, así que no puede quedar
 * en desacuerdo con ellos.
 */
public final class ReglasLote {

    private ReglasLote() {
    }

    public static BigDecimal kgComprometidos(List<Pedido> pedidos) {
        return pedidos.stream()
                .filter(p -> p.estadoPedido() != EstadoPedido.CANCELADO)
                .map(Pedido::cantidadKg)
                .reduce(BigDecimal.ZERO.setScale(3), BigDecimal::add);
    }

    public static BigDecimal kgDisponibles(Lote lote, List<Pedido> pedidos) {
        return lote.cantidadKg().subtract(kgComprometidos(pedidos));
    }

    /**
     * <ul>
     *   <li>DESPACHADO: al menos un envío del lote va en ruta.</li>
     *   <li>RESERVADO: hay pedidos por despachar o por entregar, pero ninguno va en ruta.</li>
     *   <li>LIQUIDADO: todo el lote se entregó y no queda nada por vender.</li>
     *   <li>REGISTRADO: no hay pedidos por atender; lo que no se vendió sigue disponible.</li>
     * </ul>
     */
    public static EstadoLote estadoSegun(Lote lote, List<Pedido> pedidos, boolean hayEnvioEnRuta) {
        List<Pedido> vigentes = pedidos.stream().filter(p -> p.estadoPedido() != EstadoPedido.CANCELADO).toList();
        if (hayEnvioEnRuta) {
            return EstadoLote.DESPACHADO;
        }
        boolean hayPorAtender = vigentes.stream().anyMatch(
                p -> p.estadoPedido() == EstadoPedido.PENDIENTE || p.estadoPedido() == EstadoPedido.DESPACHADO);
        if (hayPorAtender) {
            return EstadoLote.RESERVADO;
        }
        boolean hayEntregados = vigentes.stream().anyMatch(p -> p.estadoPedido() == EstadoPedido.ENTREGADO);
        if (hayEntregados && kgDisponibles(lote, vigentes).signum() == 0) {
            return EstadoLote.LIQUIDADO;
        }
        return EstadoLote.REGISTRADO;
    }
}
