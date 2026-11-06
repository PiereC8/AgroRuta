package pe.agroruta.infrastructure.adapter.out.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.SolicitudCobro;
import pe.agroruta.infrastructure.adapter.out.ServidorFalso;
import pe.agroruta.infrastructure.adapter.out.http.ClienteHttp;

class MercadoPagoPagoAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String APROBADO = """
            {"id":1234567890,"status":"approved","status_detail":"accredited","payment_method_id":"visa",
             "card":{"last_four_digits":"4242"},"external_reference":"empresa_demo:5",
             "metadata":{"negocio":"empresa_demo","id_pago":"5"}}""";

    private MercadoPagoPagoAdapter adaptador(ServidorFalso servidor) {
        return new MercadoPagoPagoAdapter(new ClienteHttp(Duration.ofSeconds(2), Duration.ofSeconds(5)),
                servidor.url(), "TEST-token-secreto");
    }

    private SolicitudCobro solicitud() {
        return new SolicitudCobro(Dinero.de("6372.00"), "comprador@ejemplo.com", "tkn_abc", "clave-de-operacion-1",
                "Pedido 7", Map.of("negocio", "empresa_demo", "idPago", "5"), "visa", "310");
    }

    @Test
    void cobrar_envia_el_token_de_la_tarjeta_con_la_clave_de_idempotencia_y_la_referencia() throws Exception {
        try (var servidor = new ServidorFalso().responde(201, APROBADO)) {
            ResultadoCobro resultado = adaptador(servidor).cobrar(solicitud());

            assertThat(resultado.estado()).isEqualTo(ResultadoCobro.Estado.APROBADO);
            assertThat(resultado.idCargo()).isEqualTo("1234567890");
            assertThat(resultado.marcaTarjeta()).isEqualTo("Visa");
            assertThat(resultado.ultimosCuatro()).isEqualTo("4242");
            assertThat(resultado.metadatos()).containsEntry("negocio", "empresa_demo").containsEntry("idPago", "5");

            var peticion = servidor.ultima();
            assertThat(peticion.metodo()).isEqualTo("POST");
            assertThat(peticion.ruta()).isEqualTo("/v1/payments");
            assertThat(peticion.cabecera("Authorization")).isEqualTo("Bearer TEST-token-secreto");
            assertThat(peticion.cabecera("X-Idempotency-Key")).isEqualTo("clave-de-operacion-1");
            JsonNode cuerpo = JSON.readTree(peticion.cuerpo());
            assertThat(cuerpo.get("transaction_amount").decimalValue()).isEqualByComparingTo("6372.00");
            assertThat(cuerpo.get("token").asText()).isEqualTo("tkn_abc");
            assertThat(cuerpo.get("installments").asInt()).isEqualTo(1);
            assertThat(cuerpo.get("payment_method_id").asText()).isEqualTo("visa");
            assertThat(cuerpo.get("issuer_id").asLong()).isEqualTo(310L);
            assertThat(cuerpo.get("payer").get("email").asText()).isEqualTo("comprador@ejemplo.com");
            assertThat(cuerpo.get("external_reference").asText()).isEqualTo("empresa_demo:5");
            assertThat(cuerpo.get("metadata").get("id_pago").asText()).isEqualTo("5");
            assertThat(peticion.cuerpo()).doesNotContain("TEST-token-secreto");
        }
    }

    @Test
    void un_pago_rechazado_es_un_resultado_y_no_un_error() throws Exception {
        try (var servidor = new ServidorFalso().responde(201, """
                {"id":99999,"status":"rejected","status_detail":"cc_rejected_insufficient_amount",
                 "external_reference":"empresa_demo:5"}""")) {
            ResultadoCobro resultado = adaptador(servidor).cobrar(solicitud());

            assertThat(resultado.estado()).isEqualTo(ResultadoCobro.Estado.RECHAZADO);
            assertThat(resultado.codigoRespuesta()).isEqualTo("cc_rejected_insufficient_amount");
            assertThat(resultado.metadatos()).containsEntry("idPago", "5");
        }
    }

    @Test
    void un_pago_en_proceso_queda_pendiente() throws Exception {
        try (var servidor = new ServidorFalso().responde(201, """
                {"id":777,"status":"in_process","status_detail":"pending_contingency"}""")) {
            assertThat(adaptador(servidor).cobrar(solicitud()).estado()).isEqualTo(ResultadoCobro.Estado.PENDIENTE);
        }
    }

    @Test
    void un_error_4xx_con_causa_se_trata_como_rechazo_con_su_motivo() throws Exception {
        try (var servidor = new ServidorFalso().responde(400, """
                {"message":"invalid token","error":"bad_request","cause":[{"code":3034,"description":"Invalid card_token_id"}]}""")) {
            ResultadoCobro resultado = adaptador(servidor).cobrar(solicitud());

            assertThat(resultado.estado()).isEqualTo(ResultadoCobro.Estado.RECHAZADO);
            assertThat(resultado.codigoRespuesta()).isEqualTo("3034");
            assertThat(resultado.mensajePasarela()).isEqualTo("Invalid card_token_id");
        }
    }

    @Test
    void si_la_pasarela_cae_o_no_acepta_las_credenciales_no_se_sabe_si_cobro() throws Exception {
        for (int estado : new int[]{500, 503, 429, 401, 403}) {
            try (var servidor = new ServidorFalso().responde(estado, "{}")) {
                assertThatThrownBy(() -> adaptador(servidor).cobrar(solicitud()))
                        .isInstanceOf(ServicioExternoNoDisponibleException.class);
            }
        }
    }

    @Test
    void sin_medio_de_pago_no_se_llama_a_la_pasarela() throws Exception {
        try (var servidor = new ServidorFalso()) {
            var sinMedio = new SolicitudCobro(Dinero.de("10.00"), "a@b.co", "tkn", "clave-de-operacion-1", "Pedido",
                    Map.of("negocio", "empresa_demo", "idPago", "5"));

            assertThat(adaptador(servidor).cobrar(sinMedio).estado()).isEqualTo(ResultadoCobro.Estado.RECHAZADO);
            assertThat(servidor.peticiones).isEmpty();
        }
    }

    @Test
    void consultar_pregunta_a_la_pasarela_por_el_estado_real_y_recupera_la_referencia() throws Exception {
        try (var servidor = new ServidorFalso().responde(200, """
                {"id":1234567890,"status":"approved","payment_method_id":"master",
                 "card":{"last_four_digits":"1111"},"external_reference":"empresa_demo:8"}""")) {
            var resultado = adaptador(servidor).consultarCargo("1234567890").orElseThrow();

            assertThat(servidor.ultima().metodo()).isEqualTo("GET");
            assertThat(servidor.ultima().ruta()).isEqualTo("/v1/payments/1234567890");
            assertThat(resultado.estado()).isEqualTo(ResultadoCobro.Estado.APROBADO);
            assertThat(resultado.marcaTarjeta()).isEqualTo("Mastercard");
            assertThat(resultado.metadatos()).containsEntry("negocio", "empresa_demo").containsEntry("idPago", "8");
        }
    }

    @Test
    void consultar_ignora_identificadores_raros_y_cargos_inexistentes() throws Exception {
        try (var servidor = new ServidorFalso().responde(404, "{}")) {
            assertThat(adaptador(servidor).consultarCargo("../../x")).isEmpty();
            assertThat(adaptador(servidor).consultarCargo(null)).isEmpty();
            assertThat(servidor.peticiones).isEmpty();
            assertThat(adaptador(servidor).consultarCargo("12345")).isEmpty();
        }
    }

    @Test
    void consultar_con_la_pasarela_caida_lanza_error_para_que_se_reintente() throws Exception {
        try (var servidor = new ServidorFalso().responde(502, "{}")) {
            assertThatThrownBy(() -> adaptador(servidor).consultarCargo("12345"))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class);
        }
    }
}
