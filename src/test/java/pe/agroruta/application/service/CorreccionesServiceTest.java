package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.DestinosServiceTest.DestinosEnMemoria;
import pe.agroruta.application.service.Falsos.CompradoresEnMemoria;
import pe.agroruta.application.service.Falsos.EnlacesEnMemoria;
import pe.agroruta.application.service.Falsos.EnviosEnMemoria;
import pe.agroruta.application.service.Falsos.LiquidacionesEnMemoria;
import pe.agroruta.application.service.Falsos.PagosEnMemoria;
import pe.agroruta.application.service.Falsos.PedidosEnMemoria;
import pe.agroruta.application.service.Falsos.TransaccionDirecta;
import pe.agroruta.application.service.Falsos.TransporteEnMemoria;
import pe.agroruta.application.service.LotesServiceTest.LotesEnMemoria;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.EnlaceEnvio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.TipoComprador;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.CorregirEntregaUseCase;
import pe.agroruta.domain.port.in.GestionarPedidosUseCase.Comando;
import pe.agroruta.domain.port.in.RegistrarEntregaUseCase;

/** Tanda 2: cancelar envío y pedido, corregir una entrega antes del pago y editar el comprador. */
class CorreccionesServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneId.of("America/Lima"));

    private final LotesEnMemoria lotes = new LotesEnMemoria();
    private final DestinosEnMemoria destinos = new DestinosEnMemoria();
    private final PedidosEnMemoria pedidos = new PedidosEnMemoria();
    private final EnviosEnMemoria envios = new EnviosEnMemoria();
    private final LiquidacionesEnMemoria liquidaciones = new LiquidacionesEnMemoria();
    private final PagosEnMemoria pagos = new PagosEnMemoria();
    private final EnlacesEnMemoria enlaces = new EnlacesEnMemoria();
    private final TransaccionDirecta transaccion = new TransaccionDirecta();
    private final TransporteEnMemoria transporte = new TransporteEnMemoria();
    private final CompradoresEnMemoria compradores = new CompradoresEnMemoria();

    private DespacharEnvioService despacho;
    private GestionarPedidosService gestionPedidos;
    private CancelarOperacionService cancelar;
    private CorregirEntregaService corregir;

    @BeforeEach
    void preparar() {
        var admin = new AdministrarDestinosService(destinos, destinos, destinos);
        admin.registrarDestino("Mercado Lima", "Lima", false);
        new AdministrarCompradoresService(compradores).registrar("Mercado Central", TipoComprador.MERCADO, null, null,
                "Lima");
        var transportes = new AdministrarTransporteService(transporte);
        transportes.registrarTransportista("Transportes Andinos", null);
        transportes.registrarVehiculo(1L, "ABC-123", new BigDecimal("2000"));
        transportes.registrarVehiculo(1L, "XYZ-789", new BigDecimal("2000"));
        lotes.guardarNuevo(Lote.nuevo("Palta Hass", new BigDecimal("1000"), LocalDate.of(2026, 11, 5), "Sur",
                        "dueno.demo", Instant.parse("2026-11-05T15:00:00Z"), LocalDate.of(2026, 11, 6)),
                new EventoLote(TipoEventoLote.REGISTRO, "registro", "dueno.demo", AHORA));
        gestionPedidos = new GestionarPedidosService(pedidos, compradores, lotes, destinos, RELOJ);
        gestionPedidos.registrar(new Comando(1L, 1L, 1L, new BigDecimal("5.00"), "operador.demo"));
        despacho = new DespacharEnvioService(pedidos, lotes, transporte, envios, transaccion, RELOJ);
        cancelar = new CancelarOperacionService(envios, pedidos, lotes, enlaces, transaccion, RELOJ);
        corregir = new CorregirEntregaService(envios, pedidos, lotes, liquidaciones, pagos, transaccion, RELOJ);
    }

    private void entregar(String recibido, String merma, String rechazo) {
        despacho.despachar(1L, 1L, "operador.demo");
        despacho.iniciarRuta(1L, "operador.demo");
        new RegistrarEntregaService(envios, pedidos, lotes, destinos, ids -> Map.of(1L, Dinero.de("0.50")),
                liquidaciones, transaccion, RELOJ).registrar(new RegistrarEntregaUseCase.Comando(1L,
                new BigDecimal(recibido), new BigDecimal(merma), new BigDecimal(rechazo), "operador.demo"));
    }

    // ---------- F6: cancelar envío ----------

    @Test
    void cancelar_un_envio_en_ruta_libera_el_lote_y_el_vehiculo_y_corta_los_enlaces() {
        despacho.despachar(1L, 1L, "operador.demo");
        despacho.iniciarRuta(1L, "operador.demo");
        enlaces.guardar(new EnlaceEnvio("sol", 1L, TipoEnlace.CONDUCTOR, "hash:c", AHORA.plusSeconds(3600)));
        enlaces.guardar(new EnlaceEnvio("sol", 1L, TipoEnlace.COMPRADOR, "hash:b", AHORA.plusSeconds(3600)));

        var cancelado = cancelar.cancelarEnvio("sol", 1L, "dueno.demo");

        assertThat(cancelado.estadoEnvio()).isEqualTo(EstadoEnvio.CANCELADO);
        assertThat(pedidos.buscarPorId(1L).orElseThrow().estadoPedido()).isEqualTo(EstadoPedido.PENDIENTE);
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.REGISTRADO);
        assertThat(lotes.eventos).extracting(EventoLote::tipoEvento).contains(TipoEventoLote.CANCELACION);
        assertThat(enlaces.buscarVigentePorHash("hash:c", AHORA)).isEmpty();
        assertThat(enlaces.buscarVigentePorHash("hash:b", AHORA)).isEmpty();
        assertThat(cancelado.admitePosicionEn(AHORA)).isFalse();
    }

    @Test
    void despues_de_cancelar_se_despacha_de_nuevo_incluso_en_el_mismo_vehiculo() {
        despacho.despachar(1L, 1L, "operador.demo");
        cancelar.cancelarEnvio("sol", 1L, "dueno.demo");

        var otro = despacho.despachar(1L, 1L, "operador.demo");

        assertThat(otro.estadoEnvio()).isEqualTo(EstadoEnvio.PROGRAMADO);
        assertThat(lotes.buscarPorId(1L).orElseThrow().estadoLote()).isEqualTo(EstadoLote.RESERVADO);
    }

    @Test
    void no_se_cancela_un_envio_ya_entregado() {
        entregar("1000", "0", "0");

        assertThatThrownBy(() -> cancelar.cancelarEnvio("sol", 1L, "dueno.demo"))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("ENTREGADO");
    }

    // ---------- F6: cancelar pedido ----------

    @Test
    void un_pedido_con_envio_en_curso_no_se_cancela_hasta_cancelar_el_envio() {
        despacho.despachar(1L, 1L, "operador.demo");

        assertThatThrownBy(() -> cancelar.cancelarPedido(1L, "dueno.demo"))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("cancela primero su envío");

        cancelar.cancelarEnvio("sol", 1L, "dueno.demo");
        var pedido = cancelar.cancelarPedido(1L, "dueno.demo");

        assertThat(pedido.estadoPedido()).isEqualTo(EstadoPedido.CANCELADO);
    }

    @Test
    void cancelado_el_pedido_el_lote_admite_otro_pedido() {
        cancelar.cancelarPedido(1L, "dueno.demo");

        var otro = gestionPedidos.registrar(new Comando(1L, 1L, 1L, new BigDecimal("5.50"), "operador.demo"));

        assertThat(otro.estadoPedido()).isEqualTo(EstadoPedido.PENDIENTE);
        assertThat(otro.idLote()).isEqualTo(1L);
    }

    // ---------- F4: corregir entrega ----------

    @Test
    void corregir_la_entrega_recalcula_la_liquidacion_y_conserva_flete_y_fecha() {
        entregar("1000", "0", "0");
        var original = liquidaciones.buscarPorLote(1L).orElseThrow();

        var corregida = corregir.corregir(new CorregirEntregaUseCase.Comando(1L, new BigDecimal("900"),
                new BigDecimal("60"), new BigDecimal("40"), "dueno.demo"));

        assertThat(corregida.idLiquidacion()).isEqualTo(original.idLiquidacion());
        assertThat(corregida.ingresoReal().monto()).isEqualByComparingTo("4500.00");
        assertThat(corregida.costoFletePorKg()).isEqualTo(original.costoFletePorKg());
        assertThat(corregida.fechaEntrega()).isEqualTo(original.fechaEntrega());
        assertThat(liquidaciones.buscarPorLote(1L).orElseThrow().cantidadRecibidaKg()).isEqualByComparingTo("900");
        assertThat(lotes.eventos).extracting(EventoLote::tipoEvento).contains(TipoEventoLote.CORRECCION);
    }

    @Test
    void la_correccion_debe_cuadrar_con_lo_despachado() {
        entregar("1000", "0", "0");

        assertThatThrownBy(() -> corregir.corregir(new CorregirEntregaUseCase.Comando(1L, new BigDecimal("900"),
                new BigDecimal("0"), new BigDecimal("0"), "dueno.demo"))).isInstanceOf(DominioException.class);
    }

    @Test
    void con_un_pago_en_curso_o_aprobado_ya_no_se_corrige() {
        entregar("1000", "0", "0");
        pagos.guardarNuevo(Pago.nuevo(liquidaciones.buscarPorLote(1L).orElseThrow(), "a@b.pe", "clave-pago-0001",
                AHORA));

        assertThatThrownBy(() -> corregir.corregir(new CorregirEntregaUseCase.Comando(1L, new BigDecimal("900"),
                new BigDecimal("100"), new BigDecimal("0"), "dueno.demo")))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("ya no se puede corregir");
    }

    @Test
    void no_se_corrige_un_envio_que_todavia_no_se_entrego() {
        despacho.despachar(1L, 1L, "operador.demo");

        assertThatThrownBy(() -> corregir.corregir(new CorregirEntregaUseCase.Comando(1L, new BigDecimal("1000"),
                new BigDecimal("0"), new BigDecimal("0"), "dueno.demo"))).isInstanceOf(ConflictoDominioException.class);
    }

    // ---------- F5: editar comprador ----------

    @Test
    void al_comprador_se_le_puede_agregar_el_ruc_y_conserva_su_id() {
        var servicio = new AdministrarCompradoresService(compradores);

        var editado = servicio.actualizar(1L, "Mercado Central SAC", TipoComprador.EMPRESA, "20123456789",
                "952 000 111", "Lima");

        assertThat(editado.idComprador()).isEqualTo(1L);
        assertThat(compradores.buscarPorId(1L).orElseThrow().documentoComprador()).isEqualTo("20123456789");
        assertThat(compradores.buscarPorId(1L).orElseThrow().tipoComprador()).isEqualTo(TipoComprador.EMPRESA);
    }

    @Test
    void no_se_repite_el_documento_de_otro_comprador_pero_si_se_conserva_el_propio() {
        var servicio = new AdministrarCompradoresService(compradores);
        servicio.registrar("Otra Empresa", TipoComprador.EMPRESA, "20999999999", null, "Tacna");
        servicio.actualizar(1L, "Mercado Central", TipoComprador.MERCADO, "20123456789", null, "Lima");

        assertThatThrownBy(() -> servicio.actualizar(1L, "Mercado Central", TipoComprador.MERCADO, "20999999999",
                null, "Lima")).isInstanceOf(ConflictoDominioException.class);
        var mismo = servicio.actualizar(1L, "Mercado Central EIRL", TipoComprador.MERCADO, "20123456789", null, "Lima");
        assertThat(mismo.nombreComprador()).isEqualTo("Mercado Central EIRL");
    }
}
