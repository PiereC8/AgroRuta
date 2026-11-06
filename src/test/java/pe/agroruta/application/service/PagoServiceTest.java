package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

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
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.SeriesComprobante;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoComprador;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.PagarPedidoUseCase.Comando;

class PagoServiceTest {

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
    private final PasarelaGrabadora pasarela = new PasarelaGrabadora();
    private final EmisorGrabador emisor = new EmisorGrabador();
    private final EjecutorDirecto ejecutor = new EjecutorDirecto();

    private EmitirComprobanteService emitir;
    private PagarPedidoService pagar;
    private ConfirmarPagoService confirmar;

    @BeforeEach
    void preparar() {
        emitir = new EmitirComprobanteService(pagos, comprobantes, liquidaciones, pedidos, compradores, lotes,
                correlativos, emisor, new SeriesComprobante("B001", "F001"), reloj);
        pagar = new PagarPedidoService(envios, pedidos, liquidaciones, compradores, pagos, comprobantes, pasarela,
                emitir, reloj);
        confirmar = new ConfirmarPagoService(pasarela, pagos, emitir, ejecutor, reloj);
        pedidoEntregadoYLiquidado("20123456789");
    }

    /** Un lote de 1250.5 kg entregado: llegaron 1180, se perdieron 40.5 y se rechazaron 30 (ingreso real S/ 6,372.00). */
    private void pedidoEntregadoYLiquidado(String documento) {
        compradores.guardar(Comprador.nuevo("Mercado Central SAC", TipoComprador.MERCADO, documento, null, "Lima"));
        long idLote = lotes.lotes.size() + 1L;
        Lote lote = lotes.guardarNuevo(Lote.nuevo("Palta Hass", new BigDecimal("1250.5"), LocalDate.of(2026, 11, 5),
                        "Sur", "dueno.demo", T0, LocalDate.of(2026, 11, 6)),
                new EventoLote(TipoEventoLote.REGISTRO, "registro", "dueno.demo", T0));
        Pedido pedido = pedidos.guardarNuevo(Pedido.nuevo(compradores.compradores.size() + 0L, lote, 3L,
                Dinero.de("5.40"), "operador.demo", T0));
        Envio envio = envios.guardarNuevo(new Envio(null, pedido.idPedido(), lote.idLote(), 1L, 1L,
                EstadoEnvio.ENTREGADO, T0, T0, T0, "operador.demo"));
        liquidaciones.guardarNueva(Liquidacion.calcular(pedido, envio, new BigDecimal("1180"),
                new BigDecimal("40.5"), new BigDecimal("30"), Dinero.de("1.20"), "operador.demo", T0));
        assertThat(idLote).isEqualTo(lote.idLote());
    }

    private Comando pago(String clave) {
        return new Comando(COMPRADOR, "tkn_test_abc", "comprador@ejemplo.com", clave);
    }

    // ---------- pagar ----------

    @Test
    void cobra_el_ingreso_real_a_la_pasarela_sin_tocar_datos_de_tarjeta_y_emite_la_factura() {
        var resultado = pagar.pagar(pago("clave-de-operacion-1"));

        assertThat(resultado.estadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(resultado.mensaje()).contains("Pago recibido");
        var solicitud = pasarela.solicitudes.get(0);
        assertThat(solicitud.monto().monto().toPlainString()).isEqualTo("6372.00");
        assertThat(solicitud.tokenTarjeta()).isEqualTo("tkn_test_abc");
        assertThat(solicitud.claveIdempotencia()).isEqualTo("clave-de-operacion-1");
        assertThat(solicitud.metadatos()).containsEntry("negocio", "empresa_demo").containsEntry("idPago", "1");
        assertThat(pagos.pagos.get(0).ultimosCuatro()).isEqualTo("4242");

        var comprobante = resultado.comprobante();
        assertThat(comprobante.tipoComprobante()).isEqualTo(TipoComprobante.FACTURA);
        assertThat(comprobante.numeroCompleto()).isEqualTo("F001-1");
        assertThat(comprobante.estadoComprobante()).isEqualTo(EstadoComprobante.ACEPTADO);
    }

    @Test
    void el_comprobante_va_exonerado_con_los_kilos_recibidos_y_el_precio_acordado() {
        pagar.pagar(pago("clave-de-operacion-1"));

        var datos = emisor.emitidos.get(0);
        assertThat(datos.total().monto().toPlainString()).isEqualTo("6372.00");
        assertThat(datos.items()).hasSize(1);
        var item = datos.items().get(0);
        assertThat(item.descripcion()).isEqualTo("Palta Hass");
        assertThat(item.cantidad().toPlainString()).isEqualTo("1180.000");
        assertThat(item.valorUnitario().monto().toPlainString()).isEqualTo("5.40");
        assertThat(datos.cliente().tipoDocumento()).isEqualTo("6");
        assertThat(datos.cliente().numeroDocumento()).isEqualTo("20123456789");
        assertThat(datos.cliente().email()).isEqualTo("comprador@ejemplo.com");
    }

    @Test
    void repetir_la_misma_clave_no_cobra_dos_veces() {
        var primero = pagar.pagar(pago("clave-de-operacion-1"));
        var segundo = pagar.pagar(pago("clave-de-operacion-1"));

        assertThat(pasarela.solicitudes).hasSize(1);
        assertThat(segundo.idPago()).isEqualTo(primero.idPago());
        assertThat(segundo.estadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(emisor.emitidos).hasSize(1);
    }

    @Test
    void un_pedido_ya_pagado_no_se_cobra_otra_vez_con_otra_clave() {
        pagar.pagar(pago("clave-de-operacion-1"));

        assertThatThrownBy(() -> pagar.pagar(pago("clave-de-operacion-2")))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("ya está pagado");
        assertThat(pasarela.solicitudes).hasSize(1);
    }

    @Test
    void una_clave_usada_en_otro_pedido_se_rechaza() {
        pagar.pagar(pago("clave-de-operacion-1"));
        pedidoEntregadoYLiquidado("45678912");

        assertThatThrownBy(() -> pagar.pagar(new Comando(new SesionEnlace("empresa_demo", 2L, TipoEnlace.COMPRADOR),
                "tkn", "a@b.co", "clave-de-operacion-1"))).isInstanceOf(ConflictoDominioException.class);
    }

    @Test
    void un_rechazo_explica_el_motivo_no_emite_comprobante_y_permite_reintentar_con_otra_clave() {
        pasarela.comportamiento = s -> ResultadoCobro.rechazado("chr_test_no", "card_declined",
                "La tarjeta no tiene fondos suficientes", s.metadatos());

        var rechazado = pagar.pagar(pago("clave-de-operacion-1"));

        assertThat(rechazado.estadoPago()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(rechazado.mensaje()).contains("no tiene fondos").contains("otra tarjeta");
        assertThat(emisor.emitidos).isEmpty();

        pasarela.comportamiento = s -> ResultadoCobro.aprobado("chr_test_ok", "Mastercard", "5100", s.metadatos());
        var aprobado = pagar.pagar(pago("clave-de-operacion-2"));

        assertThat(aprobado.estadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(pagos.pagos).hasSize(2);
        assertThat(emisor.emitidos).hasSize(1);
    }

    @Test
    void si_la_pasarela_no_responde_el_pago_queda_pendiente_y_no_se_permite_pagar_de_nuevo() {
        pasarela.noResponde = true;

        var pendiente = pagar.pagar(pago("clave-de-operacion-1"));

        assertThat(pendiente.estadoPago()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(pendiente.mensaje()).contains("confirmando");
        assertThat(emisor.emitidos).isEmpty();
        assertThatThrownBy(() -> pagar.pagar(pago("clave-de-operacion-2")))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("confirmando un pago anterior");
    }

    @Test
    void la_notificacion_de_la_pasarela_confirma_el_pago_pendiente_y_emite_el_comprobante_una_sola_vez() {
        pasarela.noResponde = true;
        pagar.pagar(pago("clave-de-operacion-1"));
        pasarela.noResponde = false;

        assertThat(confirmar.confirmar("chr_test_abc123")).isTrue();
        assertThat(confirmar.confirmar("chr_test_abc123")).isTrue();

        assertThat(pagos.pagos.get(0).estadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(comprobantes.comprobantes).hasSize(1);
        assertThat(emisor.emitidos).hasSize(1);
        assertThat(ejecutor.negocios).containsOnly("empresa_demo");
    }

    @Test
    void una_notificacion_falsa_o_sin_datos_de_agroruta_se_ignora() {
        assertThat(confirmar.confirmar("chr_test_inexistente")).isFalse();

        pasarela.cargos.put("chr_test_ajeno", ResultadoCobro.aprobado("chr_test_ajeno", "Visa", "4242", Map.of()));
        assertThat(confirmar.confirmar("chr_test_ajeno")).isFalse();

        pasarela.cargos.put("chr_test_malo", ResultadoCobro.aprobado("chr_test_malo", "Visa", "4242",
                Map.of("negocio", "x'; DROP", "idPago", "1")));
        assertThat(confirmar.confirmar("chr_test_malo")).isFalse();

        pasarela.cargos.put("chr_test_otro", ResultadoCobro.aprobado("chr_test_otro", "Visa", "4242",
                Map.of("negocio", "empresa_demo", "idPago", "999")));
        assertThat(confirmar.confirmar("chr_test_otro")).isFalse();
        assertThat(pagos.pagos).isEmpty();
    }

    @Test
    void un_cargo_distinto_al_ya_registrado_no_cambia_el_pago() {
        pagar.pagar(pago("clave-de-operacion-1"));
        pasarela.cargos.put("chr_test_otro", ResultadoCobro.aprobado("chr_test_otro", "Visa", "4242",
                Map.of("negocio", "empresa_demo", "idPago", "1")));

        assertThat(confirmar.confirmar("chr_test_otro")).isFalse();
        assertThat(pagos.pagos.get(0).idCargoPasarela()).isEqualTo("chr_test_abc123");
    }

    // ---------- reglas antes de cobrar ----------

    @Test
    void solo_se_paga_un_pedido_entregado_con_liquidacion() {
        envios.envios.set(0, new Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.EN_RUTA, T0, T0, null, "x1x"));

        assertThatThrownBy(() -> pagar.pagar(pago("clave-de-operacion-1")))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("no fue entregado");
        assertThat(pasarela.solicitudes).isEmpty();

        liquidaciones.liquidaciones.clear();
        assertThatThrownBy(() -> pagar.pagar(pago("clave-de-operacion-1")))
                .isInstanceOf(ConflictoDominioException.class).hasMessageContaining("liquidación");
    }

    @Test
    void un_enlace_de_conductor_no_puede_pagar_y_los_datos_invalidos_no_llegan_a_la_pasarela() {
        assertThatThrownBy(() -> pagar.pagar(new Comando(CONDUCTOR, "tkn", "a@b.co", "clave-de-operacion-1")))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> pagar.pagar(new Comando(COMPRADOR, " ", "a@b.co", "clave-de-operacion-1")))
                .isInstanceOf(DominioException.class).hasMessageContaining("token");
        assertThatThrownBy(() -> pagar.pagar(new Comando(COMPRADOR, "tkn", "no-es-correo", "clave-de-operacion-1")))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> pagar.pagar(new Comando(COMPRADOR, "tkn", "a@b.co", "corta")))
                .isInstanceOf(DominioException.class);
        assertThat(pasarela.solicitudes).isEmpty();
        assertThat(pagos.pagos).isEmpty();
    }

    @Test
    void no_cobra_si_no_podra_emitir_el_comprobante_por_falta_de_documento_del_comprador() {
        compradores.compradores.set(0, Comprador.nuevo("Mercado Central SAC", TipoComprador.MERCADO, null, null, "Lima")
                .conId(1L));

        assertThatThrownBy(() -> pagar.pagar(pago("clave-de-operacion-1")))
                .isInstanceOf(DominioException.class).hasMessageContaining("DNI o un RUC");
        assertThat(pasarela.solicitudes).isEmpty();
    }

    @Test
    void un_comprador_con_dni_recibe_boleta_con_la_serie_de_boletas() {
        compradores.compradores.set(0, Comprador.nuevo("Juan Pérez", TipoComprador.MAYORISTA, "45678912", null, "Lima")
                .conId(1L));

        var resultado = pagar.pagar(pago("clave-de-operacion-1"));

        assertThat(resultado.comprobante().tipoComprobante()).isEqualTo(TipoComprobante.BOLETA);
        assertThat(resultado.comprobante().numeroCompleto()).isEqualTo("B001-1");
        assertThat(emisor.emitidos.get(0).cliente().tipoDocumento()).isEqualTo("1");
    }

    // ---------- comprobantes ----------

    @Test
    void si_el_proveedor_de_comprobantes_cae_el_cobro_se_mantiene_y_el_comprobante_queda_pendiente() {
        emisor.caido = true;

        var resultado = pagar.pagar(pago("clave-de-operacion-1"));

        assertThat(resultado.estadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(resultado.comprobante().estadoComprobante()).isEqualTo(EstadoComprobante.PENDIENTE);
        assertThat(resultado.comprobante().intentos()).isEqualTo(1);
        assertThat(resultado.comprobante().proximoIntento()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
    }

    @Test
    void los_pendientes_se_reintentan_solo_cuando_llega_su_hora_y_conservan_su_numero() {
        emisor.caido = true;
        pagar.pagar(pago("clave-de-operacion-1"));
        emisor.caido = false;

        assertThat(emitir.reintentarPendientes(10)).isZero();
        assertThat(emisor.emitidos).isEmpty();

        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(emitir.reintentarPendientes(10)).isEqualTo(1);

        var comprobante = comprobantes.comprobantes.get(0);
        assertThat(comprobante.estadoComprobante()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(comprobante.numeroCompleto()).isEqualTo("F001-1");
        assertThat(correlativos.ultimos).containsEntry("F001", 1);
        assertThat(emitir.reintentarPendientes(10)).isZero();
    }

    @Test
    void si_el_proveedor_sigue_caido_el_reintento_vuelve_a_programarse() {
        emisor.caido = true;
        pagar.pagar(pago("clave-de-operacion-1"));
        reloj.avanzar(Duration.ofMinutes(5));

        emitir.reintentarPendientes(10);

        var comprobante = comprobantes.comprobantes.get(0);
        assertThat(comprobante.intentos()).isEqualTo(2);
        assertThat(comprobante.proximoIntento()).isEqualTo(T0.plus(Duration.ofMinutes(10)));
    }

    @Test
    void cada_pago_aprobado_recibe_un_numero_distinto_de_su_serie() {
        pagar.pagar(pago("clave-de-operacion-1"));
        pedidoEntregadoYLiquidado("20987654321");
        var segundo = pagar.pagar(new Comando(new SesionEnlace("empresa_demo", 2L, TipoEnlace.COMPRADOR), "tkn",
                "otro@ejemplo.com", "clave-de-operacion-9"));

        assertThat(segundo.comprobante().numeroCompleto()).isEqualTo("F001-2");
    }

    @Test
    void el_dueno_puede_reintentar_o_consultar_un_comprobante_y_se_actualiza_su_estado() {
        emisor.caido = true;
        pagar.pagar(pago("clave-de-operacion-1"));
        emisor.caido = false;

        var reintentado = emitir.reintentar(1L);
        assertThat(reintentado.estadoComprobante()).isEqualTo(EstadoComprobante.ACEPTADO);

        emisor.respuesta = EstadoComprobante.OBSERVADO;
        assertThat(emitir.consultar(1L).estadoComprobante()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThatThrownBy(() -> emitir.reintentar(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void emitir_el_comprobante_de_un_pago_no_aprobado_o_inexistente_es_un_error() {
        pasarela.noResponde = true;
        pagar.pagar(pago("clave-de-operacion-1"));

        assertThatThrownBy(() -> emitir.emitirPara(1L)).isInstanceOf(ConflictoDominioException.class);
        assertThatThrownBy(() -> emitir.emitirPara(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---------- estado público ----------

    @Test
    void el_estado_publico_dice_si_se_puede_pagar_y_muestra_el_comprobante_ya_pagado() {
        var antes = pagar.estado(COMPRADOR);
        assertThat(antes.pagable()).isTrue();
        assertThat(antes.monto().monto().toPlainString()).isEqualTo("6372.00");
        assertThat(antes.ultimoPago()).isNull();

        pagar.pagar(pago("clave-de-operacion-1"));
        var despues = pagar.estado(COMPRADOR);

        assertThat(despues.pagable()).isFalse();
        assertThat(despues.ultimoPago().estadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(despues.comprobante().numeroCompleto()).isEqualTo("F001-1");
    }

    @Test
    void tras_un_rechazo_sigue_siendo_pagable_y_con_el_pago_pendiente_no() {
        pasarela.comportamiento = s -> ResultadoCobro.rechazado("chr_test_no", "x", "x", s.metadatos());
        pagar.pagar(pago("clave-de-operacion-1"));
        assertThat(pagar.estado(COMPRADOR).pagable()).isTrue();

        pasarela.noResponde = true;
        pasarela.comportamiento = s -> ResultadoCobro.aprobado("chr_test_ok", "Visa", "4242", s.metadatos());
        pagar.pagar(pago("clave-de-operacion-2"));
        assertThat(pagar.estado(COMPRADOR).pagable()).isFalse();
    }

    @Test
    void el_estado_de_un_envio_sin_entregar_no_es_pagable() {
        envios.envios.set(0, new Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.EN_RUTA, T0, T0, null, "x1x"));
        liquidaciones.liquidaciones.clear();

        var estado = pagar.estado(COMPRADOR);

        assertThat(estado.pagable()).isFalse();
        assertThat(estado.estadoEnvio()).isEqualTo(EstadoEnvio.EN_RUTA);
        assertThat(estado.monto()).isEqualTo(Dinero.CERO);
    }
}
