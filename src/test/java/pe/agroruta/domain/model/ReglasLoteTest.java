package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReglasLoteTest {

    private static final Instant AHORA = Instant.parse("2026-11-14T15:00:00Z");
    private static final Lote LOTE = new Lote(1L, "Aceituna", new BigDecimal("5000"), LocalDate.of(2026, 11, 10), "Chacra Sur",
            EstadoLote.REGISTRADO, "dueno.demo", AHORA);

    private static Pedido pedido(long id, String kg, EstadoPedido estado) {
        return new Pedido(id, 1L, 1L, 1L, new BigDecimal(kg).setScale(3), Dinero.de("5"), estado, "x1x", AHORA);
    }

    @Test
    void los_kg_disponibles_son_los_del_lote_menos_los_pedidos_no_cancelados() {
        var pedidos = List.of(pedido(1, "2000", EstadoPedido.PENDIENTE), pedido(2, "1500", EstadoPedido.ENTREGADO),
                pedido(3, "700", EstadoPedido.CANCELADO), pedido(4, "300.5", EstadoPedido.DESPACHADO));

        assertThat(ReglasLote.kgComprometidos(pedidos)).isEqualByComparingTo("3800.5");
        assertThat(ReglasLote.kgDisponibles(LOTE, pedidos)).isEqualByComparingTo("1199.5");
        assertThat(ReglasLote.kgDisponibles(LOTE, List.of())).isEqualByComparingTo("5000");
    }

    @Test
    void sin_pedidos_el_lote_esta_disponible_y_con_un_pedido_pendiente_o_despachado_esta_reservado() {
        assertThat(ReglasLote.estadoSegun(LOTE, List.of(), false)).isEqualTo(EstadoLote.REGISTRADO);
        assertThat(ReglasLote.estadoSegun(LOTE, List.of(pedido(1, "2000", EstadoPedido.PENDIENTE)), false))
                .isEqualTo(EstadoLote.RESERVADO);
        assertThat(ReglasLote.estadoSegun(LOTE, List.of(pedido(1, "2000", EstadoPedido.DESPACHADO)), false))
                .isEqualTo(EstadoLote.RESERVADO);
    }

    @Test
    void basta_un_envio_en_ruta_para_que_el_lote_este_en_camino() {
        var pedidos = List.of(pedido(1, "2000", EstadoPedido.DESPACHADO), pedido(2, "1000", EstadoPedido.PENDIENTE));

        assertThat(ReglasLote.estadoSegun(LOTE, pedidos, true)).isEqualTo(EstadoLote.DESPACHADO);
    }

    @Test
    void los_pedidos_cancelados_no_cuentan_para_el_estado() {
        assertThat(ReglasLote.estadoSegun(LOTE, List.of(pedido(1, "5000", EstadoPedido.CANCELADO)), false))
                .isEqualTo(EstadoLote.REGISTRADO);
    }

    @Test
    void el_lote_se_liquida_solo_cuando_todo_se_entrego_y_no_queda_stock() {
        var todoEntregado = List.of(pedido(1, "3000", EstadoPedido.ENTREGADO), pedido(2, "2000", EstadoPedido.ENTREGADO));
        var conStockSinVender = List.of(pedido(1, "3000", EstadoPedido.ENTREGADO));

        assertThat(ReglasLote.estadoSegun(LOTE, todoEntregado, false)).isEqualTo(EstadoLote.LIQUIDADO);
        // Entregó 3000 de 5000 kg: lo que queda se puede seguir vendiendo.
        assertThat(ReglasLote.estadoSegun(LOTE, conStockSinVender, false)).isEqualTo(EstadoLote.REGISTRADO);
    }

    @Test
    void un_pedido_nuevo_no_puede_pasar_de_lo_disponible_ni_ser_de_cero_o_con_muchos_decimales() {
        var disponible = new BigDecimal("1200.000");

        Pedido ok = Pedido.nuevo(1L, LOTE, disponible, 1L, new BigDecimal("1200"), Dinero.de("5"), "x1x", AHORA);
        assertThat(ok.cantidadKg().toPlainString()).isEqualTo("1200.000");
        assertThat(ok.estadoPedido()).isEqualTo(EstadoPedido.PENDIENTE);

        assertThatThrownBy(() -> Pedido.nuevo(1L, LOTE, disponible, 1L, new BigDecimal("1200.001"), Dinero.de("5"), "x1x",
                AHORA)).isInstanceOf(ConflictoDominioException.class).hasMessageContaining("solo tiene 1200.000 kg");
        assertThatThrownBy(() -> Pedido.nuevo(1L, LOTE, BigDecimal.ZERO, 1L, new BigDecimal("1"), Dinero.de("5"), "x1x",
                AHORA)).isInstanceOf(ConflictoDominioException.class).hasMessageContaining("no tiene kg disponibles");
        assertThatThrownBy(() -> Pedido.nuevo(1L, LOTE, disponible, 1L, BigDecimal.ZERO, Dinero.de("5"), "x1x", AHORA))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Pedido.nuevo(1L, LOTE, disponible, 1L, null, Dinero.de("5"), "x1x", AHORA))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Pedido.nuevo(1L, LOTE, disponible, 1L, new BigDecimal("10.1234"), Dinero.de("5"), "x1x",
                AHORA)).isInstanceOf(DominioException.class).hasMessageContaining("3 decimales");
    }

    @Test
    void el_lote_con_stock_calcula_lo_que_queda() {
        var conStock = new LoteConStock(LOTE, new BigDecimal("1250.500"));

        assertThat(conStock.kgDisponibles()).isEqualByComparingTo("3749.5");
    }
}
