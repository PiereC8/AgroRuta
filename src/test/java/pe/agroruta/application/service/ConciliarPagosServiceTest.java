package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.Falsos.PagosEnMemoria;
import pe.agroruta.application.service.Falsos.PasarelaGrabadora;
import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.port.in.EmitirComprobanteUseCase;

/** F3: los pagos pendientes ya no se quedan así para siempre. */
class ConciliarPagosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");

    private final PagosEnMemoria pagos = new PagosEnMemoria();
    private final PasarelaGrabadora pasarela = new PasarelaGrabadora();
    private final List<Long> emitidos = new ArrayList<>();
    private ConciliarPagosService servicio;

    @BeforeEach
    void preparar() {
        EmitirComprobanteUseCase emitir = new EmitirComprobanteUseCase() {
            @Override
            public Comprobante emitirPara(Long idPago) {
                emitidos.add(idPago);
                return null;
            }

            @Override
            public int reintentarPendientes(int limite) {
                return 0;
            }

            @Override
            public Comprobante reintentar(Long idComprobante) {
                return null;
            }

            @Override
            public Comprobante consultar(Long idComprobante) {
                return null;
            }
        };
        servicio = new ConciliarPagosService(pasarela, pagos, emitir, Clock.fixed(AHORA, ZoneId.of("UTC")));
    }

    private Pago pendiente(String idCargo, String codigo, Duration hace) {
        Instant creado = AHORA.minus(hace);
        return pagos.guardarNuevo(new Pago(null, 1L, 1L, new Dinero(new BigDecimal("475.00")), EstadoPago.PENDIENTE,
                "clave-" + Math.abs((idCargo + codigo + hace).hashCode()), idCargo, codigo, "pendiente", null, null,
                "a@b.pe", creado, creado));
    }

    private EstadoPago estadoDe(Pago pago) {
        return pagos.buscarPorId(pago.idPago()).orElseThrow().estadoPago();
    }

    @Test
    void si_la_pasarela_ya_lo_aprobo_se_aprueba_y_se_emite_el_comprobante() {
        Pago pago = pendiente("chr_1", null, Duration.ofMinutes(30));
        pasarela.cargos.put("chr_1", ResultadoCobro.aprobado("chr_1", "Visa", "4242", Map.of()));

        assertThat(servicio.conciliar()).isEqualTo(1);

        assertThat(estadoDe(pago)).isEqualTo(EstadoPago.APROBADO);
        assertThat(emitidos).containsExactly(pago.idPago());
    }

    @Test
    void un_codigo_de_pago_vencido_se_rechaza_y_libera_el_pedido() {
        Pago pago = pendiente("cip_9", ResultadoCobro.CIP_GENERADO, Duration.ofHours(49));
        pasarela.cargos.put("cip_9", ResultadoCobro.pendiente("cip_9", ResultadoCobro.CIP_GENERADO, "esperando",
                "pagoefectivo", Map.of()));

        servicio.conciliar();

        Pago actualizado = pagos.buscarPorId(pago.idPago()).orElseThrow();
        assertThat(actualizado.estadoPago()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(actualizado.codigoRespuesta()).isEqualTo(ConciliarPagosService.CODIGO_VENCIDO);
    }

    @Test
    void un_codigo_de_pago_dentro_de_su_plazo_sigue_esperando() {
        Pago pago = pendiente("cip_8", ResultadoCobro.CIP_GENERADO, Duration.ofHours(20));
        pasarela.cargos.put("cip_8", ResultadoCobro.pendiente("cip_8", ResultadoCobro.CIP_GENERADO, "esperando",
                "pagoefectivo", Map.of()));

        assertThat(servicio.conciliar()).isZero();
        assertThat(estadoDe(pago)).isEqualTo(EstadoPago.PENDIENTE);
    }

    @Test
    void sin_cargo_de_la_pasarela_se_libera_recien_a_las_dos_horas() {
        Pago reciente = pendiente(null, null, Duration.ofMinutes(40));
        Pago viejo = pendiente(null, null, Duration.ofHours(3));

        servicio.conciliar();

        assertThat(estadoDe(reciente)).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(estadoDe(viejo)).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(pagos.buscarPorId(viejo.idPago()).orElseThrow().codigoRespuesta())
                .isEqualTo(ConciliarPagosService.SIN_CONFIRMAR);
    }

    @Test
    void los_pagos_de_menos_de_diez_minutos_no_se_tocan() {
        Pago pago = pendiente("chr_2", null, Duration.ofMinutes(3));
        pasarela.cargos.put("chr_2", ResultadoCobro.aprobado("chr_2", "Visa", "4242", Map.of()));

        assertThat(servicio.conciliar()).isZero();
        assertThat(estadoDe(pago)).isEqualTo(EstadoPago.PENDIENTE);
    }
}
