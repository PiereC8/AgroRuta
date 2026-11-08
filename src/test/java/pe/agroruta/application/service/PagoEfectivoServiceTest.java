package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.Falsos.CompradoresEnMemoria;
import pe.agroruta.application.service.Falsos.ComprobantesEnMemoria;
import pe.agroruta.application.service.Falsos.CorrelativoEnMemoria;
import pe.agroruta.application.service.Falsos.EjecutorDirecto;
import pe.agroruta.application.service.Falsos.EmisorGrabador;
import pe.agroruta.application.service.Falsos.EnviosEnMemoria;
import pe.agroruta.application.service.Falsos.LiquidacionesEnMemoria;
import pe.agroruta.application.service.Falsos.PagosEnMemoria;
import pe.agroruta.application.service.Falsos.PasarelaGrabadora;
import pe.agroruta.application.service.Falsos.PedidosEnMemoria;
import pe.agroruta.application.service.LotesServiceTest.LotesEnMemoria;
import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.SeriesComprobante;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoComprador;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.PagarPedidoUseCase.Comando;
import pe.agroruta.infrastructure.adapter.out.pago.PasarelaSimulada;

/** El pago con código (CIP) de PagoEfectivo, simulado: se genera el código, queda pendiente y luego se "paga en un agente". */
class PagoEfectivoServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-06T15:00:00Z");
    private static final SesionEnlace COMPRADOR = new SesionEnlace("empresa_demo", 1L, TipoEnlace.COMPRADOR);
    private static final SesionEnlace CONDUCTOR = new SesionEnlace("empresa_demo", 1L, TipoEnlace.CONDUCTOR);

    private final RelojMutable reloj = new RelojMutable(T0);
    private final LotesEnMemoria lotes = new LotesEnMemoria();
    private final CompradoresEnMemoria compradores = new CompradoresEnMemoria();
    private final PedidosEnMemoria pedidos = new PedidosEnMemoria();
    private final EnviosEnMemoria envios = new EnviosEnMemoria();
    private final LiquidacionesEnMemoria liquidaciones = new LiquidacionesEnMemoria();
    private final PagosEnMemoria pagos = new PagosEnMemoria();
    private final ComprobantesEnMemoria comprobantes = new ComprobantesEnMemoria();
    private final CorrelativoEnMemoria correlativos = new CorrelativoEnMemoria();
    private final EmisorGrabador emisor = new EmisorGrabador();
    private final PasarelaSimulada pasarela = new PasarelaSimulada(reloj);

    private PagarPedidoService pagar;
    private SimularPagoEfectivoService simular;

    @BeforeEach
    void preparar() {
        EmitirComprobanteService emitir = new EmitirComprobanteService(pagos, comprobantes, liquidaciones, pedidos,
                compradores, lotes, correlativos, emisor, new SeriesComprobante("B001", "F001"), reloj);
        pagar = new PagarPedidoService(envios, pedidos, liquidaciones, compradores, pagos, comprobantes, pasarela,
                emitir, reloj);
        ConfirmarPagoService confirmar = new ConfirmarPagoService(pasarela, pagos, emitir, new EjecutorDirecto(), reloj);
        simular = new SimularPagoEfectivoService(envios, liquidaciones, pagos, pasarela, confirmar);
        compradores.guardar(Comprador.nuevo("Mercado Central SAC", TipoComprador.MERCADO, "20123456789", null, "Lima"));
        Lote lote = lotes.guardarNuevo(Lote.nuevo("Palta Hass", new BigDecimal("1250.5"), LocalDate.of(2026, 11, 5),
                        "Sur", "dueno.demo", T0, LocalDate.of(2026, 11, 6)),
                new EventoLote(TipoEventoLote.REGISTRO, "registro", "dueno.demo", T0));
        Pedido pedido = pedidos.guardarNuevo(Pedido.nuevo(1L, lote, 3L, Dinero.de("5.40"), "operador.demo", T0));
        Envio envio = envios.guardarNuevo(new Envio(null, pedido.idPedido(), lote.idLote(), 1L, 1L,
                EstadoEnvio.ENTREGADO, T0, T0, T0, "operador.demo"));
        liquidaciones.guardarNueva(Liquidacion.calcular(pedido, envio, new BigDecimal("1180"),
                new BigDecimal("40.5"), new BigDecimal("30"), Dinero.de("1.20"), "operador.demo", T0));
    }

    private Comando conCodigo(String clave) {
        return new Comando(COMPRADOR, "sim_pagoefectivo", "comprador@ejemplo.com", clave, "pagoefectivo", null);
    }

    @Test
    void generar_el_codigo_deja_el_pago_pendiente_con_el_cip_y_su_vencimiento() {
        var resultado = pagar.pagar(conCodigo("clave-de-operacion-1"));

        assertThat(resultado.estadoPago()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(resultado.mensaje()).containsPattern("CIP\\D+\\d{12}").contains("08/11/2026 10:00");
        var pago = pagos.pagos.get(0);
        assertThat(pago.idCargoPasarela()).isNotBlank();
        assertThat(pago.marcaTarjeta()).isEqualTo("PagoEfectivo");
        assertThat(comprobantes.comprobantes).isEmpty();
    }

    @Test
    void repetir_la_misma_clave_devuelve_el_mismo_codigo() {
        var primero = pagar.pagar(conCodigo("clave-de-operacion-1"));
        var segundo = pagar.pagar(conCodigo("clave-de-operacion-1"));

        assertThat(segundo.idPago()).isEqualTo(primero.idPago());
        assertThat(segundo.mensaje()).isEqualTo(primero.mensaje());
        assertThat(pagos.pagos).hasSize(1);
    }

    @Test
    void con_un_codigo_vigente_no_se_puede_generar_otro_pago() {
        pagar.pagar(conCodigo("clave-de-operacion-1"));

        assertThatThrownBy(() -> pagar.pagar(conCodigo("clave-de-operacion-2")))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("código de pago");
    }

    @Test
    void pagar_en_el_agente_aprueba_el_pago_y_emite_el_comprobante() {
        pagar.pagar(conCodigo("clave-de-operacion-1"));

        simular.simular(COMPRADOR);

        var pago = pagos.pagos.get(0);
        assertThat(pago.estadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(pago.marcaTarjeta()).isEqualTo("PagoEfectivo");
        assertThat(comprobantes.comprobantes).hasSize(1);
        assertThat(emisor.emitidos).hasSize(1);
    }

    @Test
    void solo_el_comprador_puede_simular_el_pago() {
        pagar.pagar(conCodigo("clave-de-operacion-1"));

        assertThatThrownBy(() -> simular.simular(CONDUCTOR)).isInstanceOf(DominioException.class);
    }

    @Test
    void sin_un_codigo_pendiente_no_hay_nada_que_simular() {
        assertThatThrownBy(() -> simular.simular(COMPRADOR)).isInstanceOf(ConflictoDominioException.class);
    }

    @Test
    void la_simulacion_no_existe_con_una_pasarela_real() {
        var real = new PasarelaGrabadora();
        real.comportamiento = s -> pe.agroruta.domain.model.ResultadoCobro.pendiente("cip_real_1",
                pe.agroruta.domain.model.ResultadoCobro.CIP_GENERADO, "CIP 123456789012", "PagoEfectivo", s.metadatos());
        var pagarReal = new PagarPedidoService(envios, pedidos, liquidaciones, compradores, pagos, comprobantes, real,
                null, reloj);
        pagarReal.pagar(conCodigo("clave-de-operacion-9"));
        var simularReal = new SimularPagoEfectivoService(envios, liquidaciones, pagos, real, null);

        assertThatThrownBy(() -> simularReal.simular(COMPRADOR))
                .isInstanceOf(DominioException.class).hasMessageContaining("simulada");
    }

    @Test
    void el_codigo_vence_a_las_48_horas() {
        reloj.avanzar(Duration.ofHours(1));
        var resultado = pagar.pagar(conCodigo("clave-de-operacion-1"));

        assertThat(resultado.mensaje()).contains("08/11/2026 11:00");
    }
}
