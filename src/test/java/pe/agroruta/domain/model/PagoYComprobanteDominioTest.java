package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PagoYComprobanteDominioTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");

    private static Liquidacion liquidacion(String recibido, String merma, String rechazo) {
        Lote lote = Lote.nuevo("Palta", new BigDecimal("1250.5"), LocalDate.of(2026, 11, 5), "Sur", "dueno.demo", AHORA,
                LocalDate.of(2026, 11, 6)).conId(1L);
        Pedido pedido = Pedido.nuevo(1L, lote, 3L, Dinero.de("5.40"), "x1x", AHORA).conId(1L);
        Envio envio = new Envio(1L, 1L, 1L, 1L, 1L, EstadoEnvio.ENTREGADO, AHORA, AHORA, AHORA, "x1x");
        return Liquidacion.calcular(pedido, envio, new BigDecimal(recibido), new BigDecimal(merma),
                new BigDecimal(rechazo), Dinero.de("1.20"), "operador.demo", AHORA).conId(1L);
    }

    private static Pago pago() {
        return Pago.nuevo(liquidacion("1180", "40.5", "30"), "comprador@ejemplo.com", "clave-de-operacion-1", AHORA)
                .conId(1L);
    }

    // ---------- Pago ----------

    @Test
    void el_pago_cobra_el_ingreso_real_de_la_liquidacion_y_empieza_pendiente() {
        Pago pago = pago();

        assertThat(pago.monto().monto().toPlainString()).isEqualTo("6372.00");
        assertThat(pago.estadoPago()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(pago.ultimosCuatro()).isNull();
    }

    @Test
    void no_hay_nada_que_cobrar_si_todo_se_rechazo() {
        assertThatThrownBy(() -> Pago.nuevo(liquidacion("0", "0", "1250.5"), "a@b.co", "clave-de-operacion-1", AHORA))
                .isInstanceOf(DominioException.class).hasMessageContaining("nada que cobrar");
    }

    @Test
    void valida_la_clave_de_operacion_y_el_correo() {
        Liquidacion liquidacion = liquidacion("1180", "40.5", "30");

        assertThatThrownBy(() -> Pago.nuevo(liquidacion, "a@b.co", "corta", AHORA)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Pago.nuevo(liquidacion, "a@b.co", "con espacios y simbolos!!", AHORA))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Pago.nuevo(liquidacion, "no-es-correo", "clave-de-operacion-1", AHORA))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Pago.nuevo(liquidacion, null, "clave-de-operacion-1", AHORA))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void aprobar_guarda_solo_la_marca_y_los_ultimos_cuatro_digitos() {
        Pago aprobado = pago().aplicar(ResultadoCobro.aprobado("chr_test_1", "Visa", "4242", Map.of()),
                AHORA.plusSeconds(3));

        assertThat(aprobado.estadoPago()).isEqualTo(EstadoPago.APROBADO);
        assertThat(aprobado.idCargoPasarela()).isEqualTo("chr_test_1");
        assertThat(aprobado.marcaTarjeta()).isEqualTo("Visa");
        assertThat(aprobado.ultimosCuatro()).isEqualTo("4242");
        assertThat(aprobado.fechaActualizacion()).isEqualTo(AHORA.plusSeconds(3));
    }

    @Test
    void nunca_acepta_un_numero_de_tarjeta_completo() {
        assertThatThrownBy(() -> pago().aplicar(
                ResultadoCobro.aprobado("chr_test_1", "Visa", "4111111111111111", Map.of()), AHORA))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void un_rechazo_explica_el_motivo_en_palabras_simples() {
        Pago rechazado = pago().aplicar(
                ResultadoCobro.rechazado("chr_test_2", "card_declined", "Fondos insuficientes", Map.of()), AHORA);

        assertThat(rechazado.estadoPago()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(rechazado.mensajeUsuario()).contains("no tiene fondos").contains("otra tarjeta");
    }

    @Test
    void un_pago_aprobado_no_cambia_y_un_resultado_pendiente_tampoco() {
        Pago aprobado = pago().aplicar(ResultadoCobro.aprobado("chr_test_1", "Visa", "4242", Map.of()), AHORA);

        assertThat(aprobado.aplicar(ResultadoCobro.rechazado("chr_x", "otro", "otro", Map.of()), AHORA))
                .isSameAs(aprobado);
        assertThat(pago().aplicar(new ResultadoCobro(ResultadoCobro.Estado.PENDIENTE, null, null, null, null, null,
                Map.of()), AHORA).estadoPago()).isEqualTo(EstadoPago.PENDIENTE);
    }

    @Test
    void si_la_pasarela_confirma_un_cobro_que_figuraba_rechazado_se_acepta_porque_el_dinero_ya_se_cobro() {
        Pago rechazado = pago().aplicar(ResultadoCobro.rechazado("chr_test_2", "x", "x", Map.of()), AHORA);

        Pago corregido = rechazado.aplicar(ResultadoCobro.aprobado("chr_test_2", "Visa", "4242", Map.of()), AHORA);

        assertThat(corregido.estadoPago()).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void los_mensajes_de_rechazo_cubren_los_casos_habituales_y_siempre_dan_un_siguiente_paso() {
        assertThat(MensajesPago.paraUsuario("fondos_insuficientes", null)).contains("fondos");
        assertThat(MensajesPago.paraUsuario(null, "La tarjeta está vencida")).contains("vencida");
        assertThat(MensajesPago.paraUsuario("stolen_card", null)).contains("banco");
        assertThat(MensajesPago.paraUsuario("invalid_cvv", null)).contains("Revisa los datos");
        assertThat(MensajesPago.paraUsuario(null, null)).contains("otra tarjeta");
        assertThat(MensajesPago.paraUsuario("xyz", "algo raro")).contains("Intenta con otra tarjeta");
    }

    // ---------- Comprobante ----------

    @Test
    void el_comprobante_nuevo_nace_pendiente_y_con_su_numero_completo() {
        Comprobante c = Comprobante.nuevo(1L, TipoComprobante.BOLETA, "B001", 7, LocalDate.of(2026, 11, 6), AHORA);

        assertThat(c.esPendiente()).isTrue();
        assertThat(c.numeroCompleto()).isEqualTo("B001-7");
        assertThat(c.intentos()).isZero();
    }

    @Test
    void si_el_proveedor_no_responde_queda_pendiente_y_se_reintenta_en_cinco_minutos() {
        Comprobante c = Comprobante.nuevo(1L, TipoComprobante.BOLETA, "B001", 7, LocalDate.of(2026, 11, 6), AHORA);

        Comprobante fallido = c.fallido("no respondió", AHORA);

        assertThat(fallido.esPendiente()).isTrue();
        assertThat(fallido.intentos()).isEqualTo(1);
        assertThat(fallido.proximoIntento()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(fallido.mensajeSunat()).isEqualTo("no respondió");
    }

    @Test
    void al_aceptarse_se_guardan_los_enlaces_y_ya_no_se_reintenta() {
        Comprobante c = Comprobante.nuevo(1L, TipoComprobante.FACTURA, "F001", 1, LocalDate.of(2026, 11, 6), AHORA)
                .conResultado(new ResultadoEmision(EstadoComprobante.ACEPTADO, "https://pdf", "https://xml", "ok"),
                        AHORA);

        assertThat(c.estadoComprobante()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(c.enlacePdf()).isEqualTo("https://pdf");
        assertThat(c.proximoIntento()).isNull();
    }

    @Test
    void despues_de_muchos_intentos_deja_de_reintentarse_sola() {
        Comprobante c = Comprobante.nuevo(1L, TipoComprobante.BOLETA, "B001", 7, LocalDate.of(2026, 11, 6), AHORA);
        for (int i = 0; i < Comprobante.MAXIMO_INTENTOS_AUTOMATICOS; i++) {
            c = c.fallido("no respondió", AHORA);
        }

        assertThat(c.esPendiente()).isTrue();
        assertThat(c.proximoIntento()).isNull();
    }

    // ---------- Qué comprobante corresponde ----------

    private static Comprador comprador(String documento) {
        return Comprador.nuevo("Mercado Central", TipoComprador.MERCADO, documento, null, "Lima").conId(1L);
    }

    @Test
    void con_ruc_corresponde_factura_con_dni_boleta_y_sin_documento_boleta_si_el_monto_es_chico() {
        assertThat(ReglasComprobante.tipoPara(comprador("20123456789"), Dinero.de("6372.00")))
                .isEqualTo(TipoComprobante.FACTURA);
        assertThat(ReglasComprobante.tipoPara(comprador("45678912"), Dinero.de("6372.00")))
                .isEqualTo(TipoComprobante.BOLETA);
        assertThat(ReglasComprobante.tipoPara(comprador(null), Dinero.de("699.99"))).isEqualTo(TipoComprobante.BOLETA);
    }

    @Test
    void desde_700_soles_una_boleta_exige_identificar_al_cliente() {
        assertThatThrownBy(() -> ReglasComprobante.tipoPara(comprador(null), Dinero.de("700.00")))
                .isInstanceOf(DominioException.class).hasMessageContaining("DNI o un RUC");
    }

    @Test
    void los_datos_del_cliente_siguen_los_codigos_de_sunat() {
        var ruc = ReglasComprobante.clienteDe(comprador("20123456789"), "a@b.co");
        var dni = ReglasComprobante.clienteDe(comprador("45678912"), "a@b.co");
        var varios = ReglasComprobante.clienteDe(comprador(null), "a@b.co");

        assertThat(ruc.tipoDocumento()).isEqualTo("6");
        assertThat(dni.tipoDocumento()).isEqualTo("1");
        assertThat(varios.tipoDocumento()).isEqualTo("-");
        assertThat(varios.denominacion()).isEqualTo("CLIENTE VARIOS");
    }

    @Test
    void las_series_deben_tener_cuatro_caracteres() {
        assertThat(new SeriesComprobante("B001", "F001").paraTipo(TipoComprobante.FACTURA)).isEqualTo("F001");
        assertThatThrownBy(() -> new SeriesComprobante("B01", "F001")).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> new SeriesComprobante("B001", "f001")).isInstanceOf(DominioException.class);
    }
}
