package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OperacionDespachoTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");

    private static Lote lote() {
        return Lote.nuevo("Palta", new BigDecimal("1000"), LocalDate.of(2026, 11, 5), "Sur", "dueno.demo", AHORA,
                LocalDate.of(2026, 11, 6)).conId(1L);
    }

    private static Pedido pedido() {
        return Pedido.nuevo(1L, lote(), 1L, Dinero.de("5.20"), "operador.demo", AHORA).conId(1L);
    }

    private static Vehiculo vehiculo() {
        return Vehiculo.nuevo(1L, "abc-123", new BigDecimal("2000")).conId(1L);
    }

    private static Lote reservado() {
        Lote l = lote();
        return new Lote(l.idLote(), l.productoLote(), l.cantidadKg(), l.fechaCosecha(), l.chacraOrigen(),
                EstadoLote.RESERVADO, l.usuarioRegistro(), l.fechaRegistro());
    }

    @Test
    void el_atajo_de_pedido_por_todo_el_lote_solo_vale_si_el_lote_no_tiene_otros_pedidos() {
        Lote reservado = reservado();

        assertThatThrownBy(() -> Pedido.nuevo(1L, reservado, 1L, Dinero.de("5"), "x1x", AHORA))
                .isInstanceOf(ConflictoDominioException.class);
    }

    @Test
    void el_pedido_despachado_no_vuelve_a_despacharse() {
        Pedido despachado = pedido().despachado();

        assertThat(despachado.estadoPedido()).isEqualTo(EstadoPedido.DESPACHADO);
        assertThatThrownBy(despachado::despachado).isInstanceOf(ConflictoDominioException.class);
    }

    @Test
    void el_envio_sigue_programado_en_ruta_entregado_y_no_admite_saltos() {
        Envio programado = Envio.programar(pedido(), vehiculo(), "operador.demo", AHORA).conId(1L);
        assertThat(programado.estadoEnvio()).isEqualTo(EstadoEnvio.PROGRAMADO);
        assertThat(programado.idTransportista()).isEqualTo(1L);

        Envio enRuta = programado.iniciarRuta(AHORA.plusSeconds(60));
        assertThat(enRuta.fechaSalida()).isEqualTo(AHORA.plusSeconds(60));
        Envio entregado = enRuta.marcarEntregado(AHORA.plusSeconds(3600));
        assertThat(entregado.estadoEnvio()).isEqualTo(EstadoEnvio.ENTREGADO);
        assertThat(entregado.fechaEntrega()).isEqualTo(AHORA.plusSeconds(3600));

        assertThatThrownBy(() -> programado.marcarEntregado(AHORA)).isInstanceOf(ConflictoDominioException.class);
        assertThatThrownBy(() -> enRuta.iniciarRuta(AHORA)).isInstanceOf(ConflictoDominioException.class);
        assertThatThrownBy(() -> entregado.iniciarRuta(AHORA)).isInstanceOf(ConflictoDominioException.class);
    }

    @Test
    void el_vehiculo_normaliza_la_placa_y_verifica_la_capacidad() {
        Vehiculo v = vehiculo();

        assertThat(v.placaVehiculo()).isEqualTo("ABC-123");
        v.verificarCapacidad(new BigDecimal("2000"));
        assertThatThrownBy(() -> v.verificarCapacidad(new BigDecimal("2000.001")))
                .isInstanceOf(DominioException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "AB", "ABC 123", "ABC-12345", "ab!-123"})
    void rechaza_placas_invalidas(String placa) {
        assertThatThrownBy(() -> Vehiculo.nuevo(1L, placa, new BigDecimal("100")))
                .isInstanceOf(DominioException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100000.001", "10.0001"})
    void rechaza_capacidades_invalidas(String capacidad) {
        assertThatThrownBy(() -> Vehiculo.nuevo(1L, "ABC-123", new BigDecimal(capacidad)))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void el_comprador_valida_documento_y_telefono_opcionales() {
        Comprador sinDatos = Comprador.nuevo("Mercado Central", TipoComprador.MERCADO, " ", "", "Lima");
        assertThat(sinDatos.documentoComprador()).isNull();
        assertThat(sinDatos.telefonoComprador()).isNull();

        Comprador conDatos = Comprador.nuevo("Mercado Central", TipoComprador.EMPRESA, "20123456789",
                "+51 987 654 321", "Lima");
        assertThat(conDatos.documentoComprador()).isEqualTo("20123456789");

        assertThatThrownBy(() -> Comprador.nuevo("Mercado", TipoComprador.MERCADO, "123", null, "Lima"))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Comprador.nuevo("Mercado", TipoComprador.MERCADO, null, "abc", "Lima"))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Comprador.nuevo("Mercado", null, null, null, "Lima"))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void el_transportista_exige_nombre() {
        assertThatThrownBy(() -> Transportista.nuevo(" ", null)).isInstanceOf(DominioException.class);
        assertThat(Transportista.nuevo("Transportes Andinos", "").telefonoTransportista()).isNull();
    }
}
