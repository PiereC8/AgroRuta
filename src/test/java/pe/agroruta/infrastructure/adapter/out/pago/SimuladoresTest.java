package pe.agroruta.infrastructure.adapter.out.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.SolicitudCobro;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.infrastructure.adapter.out.comprobante.ComprobanteSimulado;

class SimuladoresTest {

    private final PasarelaSimulada pasarela = new PasarelaSimulada();

    private SolicitudCobro con(String token) {
        return new SolicitudCobro(Dinero.de("100.00"), "a@b.co", token, "clave-de-operacion-1", "pedido",
                Map.of("negocio", "empresa_demo", "idPago", "1"));
    }

    @Test
    void la_tarjeta_simulada_aprobada_cobra_y_se_puede_consultar_despues() {
        ResultadoCobro resultado = pasarela.cobrar(con(PasarelaSimulada.APROBADA));

        assertThat(resultado.estado()).isEqualTo(ResultadoCobro.Estado.APROBADO);
        assertThat(resultado.ultimosCuatro()).isEqualTo("4242");
        assertThat(pasarela.consultarCargo(resultado.idCargo())).contains(resultado);
        assertThat(pasarela.consultarCargo("chr_sim_inexistente")).isEmpty();
        assertThat(resultado.idCargo()).matches("chr_[a-z]+_[A-Za-z0-9]{6,40}");
    }

    @Test
    void el_token_trae_la_marca_y_los_ultimos_cuatro_digitos() {
        var mastercard = pasarela.cobrar(con("sim_aprobada_mastercard_4444"));
        var paypal = pasarela.cobrar(con("sim_aprobada_paypal"));
        var amex = pasarela.cobrar(con("sim_aprobada_amex_0005"));

        assertThat(mastercard.marcaTarjeta()).isEqualTo("Mastercard");
        assertThat(mastercard.ultimosCuatro()).isEqualTo("4444");
        assertThat(paypal.estado()).isEqualTo(ResultadoCobro.Estado.APROBADO);
        assertThat(paypal.marcaTarjeta()).isEqualTo("PayPal");
        assertThat(paypal.ultimosCuatro()).isNull();
        assertThat(amex.marcaTarjeta()).isEqualTo("American Express");
        assertThat(pasarela.cobrar(con("sim_sin_fondos_visa_9995")).codigoRespuesta()).isEqualTo("fondos_insuficientes");
        assertThatThrownBy(() -> pasarela.cobrar(con("sim_sin_respuesta_visa_0119")))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }

    @Test
    void un_token_con_forma_rara_se_rechaza_sin_cobrar() {
        for (String token : new String[]{"sim_aprobada_bitcoin_1234", "sim_aprobada_visa_12", "4242424242424242", "", null}) {
            assertThat(pasarela.cobrar(con(token)).estado()).isEqualTo(ResultadoCobro.Estado.RECHAZADO);
        }
    }

    @Test
    void sin_fondos_y_vencida_se_rechazan_con_su_motivo() {
        assertThat(pasarela.cobrar(con(PasarelaSimulada.SIN_FONDOS)).codigoRespuesta()).isEqualTo("fondos_insuficientes");
        assertThat(pasarela.cobrar(con(PasarelaSimulada.VENCIDA)).codigoRespuesta()).isEqualTo("tarjeta_vencida");
        assertThat(pasarela.cobrar(con("cualquier-otra-cosa")).estado()).isEqualTo(ResultadoCobro.Estado.RECHAZADO);
    }

    @Test
    void sin_respuesta_cobra_pero_no_contesta_para_probar_la_confirmacion_por_notificacion() {
        assertThatThrownBy(() -> pasarela.cobrar(con(PasarelaSimulada.SIN_RESPUESTA)))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }

    @Test
    void el_emisor_simulado_acepta_todo_y_avisa_que_no_vale_ante_sunat() {
        DatosComprobante datos = new DatosComprobante(TipoComprobante.BOLETA, "B001", 1, LocalDate.of(2026, 11, 6),
                new DatosComprobante.Cliente("-", "-", "CLIENTE VARIOS", "a@b.co"),
                List.of(new DatosComprobante.Item("KGM", "P", "Palta", BigDecimal.ONE, Dinero.de("5"), Dinero.de("5"))),
                Dinero.de("5"));
        ComprobanteSimulado emisor = new ComprobanteSimulado();

        assertThat(emisor.emitir(datos).estado()).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(emisor.emitir(datos).mensajeSunat()).contains("no tiene validez ante SUNAT");
        assertThat(emisor.consultar(TipoComprobante.BOLETA, "B001", 1).estado()).isEqualTo(EstadoComprobante.ACEPTADO);
    }
}
