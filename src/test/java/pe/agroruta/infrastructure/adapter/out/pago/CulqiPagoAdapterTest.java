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

class CulqiPagoAdapterTest {

    private static final String LLAVE_SECRETA = "sk_test_llave_de_prueba";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CARGO_EXITOSO = """
            {"object":"charge","id":"chr_test_Abc123XYZ","response_code":"venta_exitosa","state":"Exitosa",
             "source":{"card_number":"411111******1111","iin":{"card_brand":"Visa"}},
             "metadata":{"negocio":"empresa_demo","idPago":"5"}}""";

    private CulqiPagoAdapter adaptador(ServidorFalso servidor) {
        return new CulqiPagoAdapter(new ClienteHttp(Duration.ofSeconds(2), Duration.ofSeconds(5)),
                servidor.url() + "/v2", LLAVE_SECRETA);
    }

    private SolicitudCobro solicitud() {
        return new SolicitudCobro(Dinero.de("6372.00"), "comprador@ejemplo.com", "tkn_test_abc", "clave-de-operacion-1",
                "AgroRuta - pedido 1", Map.of("negocio", "empresa_demo", "idPago", "5"));
    }

    @Test
    void un_cargo_exitoso_se_aprueba_y_guarda_solo_marca_y_ultimos_cuatro_digitos() {
        try (var servidor = new ServidorFalso().responde(201, CARGO_EXITOSO)) {
            ResultadoCobro resultado = adaptador(servidor).cobrar(solicitud());

            assertThat(resultado.estado()).isEqualTo(ResultadoCobro.Estado.APROBADO);
            assertThat(resultado.idCargo()).isEqualTo("chr_test_Abc123XYZ");
            assertThat(resultado.marcaTarjeta()).isEqualTo("Visa");
            assertThat(resultado.ultimosCuatro()).isEqualTo("1111");
            assertThat(resultado.metadatos()).containsEntry("negocio", "empresa_demo").containsEntry("idPago", "5");
        }
    }

    @Test
    void el_cobro_va_en_centimos_con_el_token_y_la_llave_secreta_solo_en_el_encabezado() throws Exception {
        try (var servidor = new ServidorFalso().responde(201, CARGO_EXITOSO)) {
            adaptador(servidor).cobrar(solicitud());

            var peticion = servidor.ultima();
            assertThat(peticion.metodo()).isEqualTo("POST");
            assertThat(peticion.ruta()).isEqualTo("/v2/charges");
            assertThat(peticion.cabecera("Authorization")).isEqualTo("Bearer " + LLAVE_SECRETA);
            assertThat(peticion.cabecera("Content-Type")).contains("application/json");
            JsonNode cuerpo = JSON.readTree(peticion.cuerpo());
            assertThat(cuerpo.get("amount").asLong()).isEqualTo(637200L);
            assertThat(cuerpo.get("currency_code").asText()).isEqualTo("PEN");
            assertThat(cuerpo.get("email").asText()).isEqualTo("comprador@ejemplo.com");
            assertThat(cuerpo.get("source_id").asText()).isEqualTo("tkn_test_abc");
            assertThat(cuerpo.get("metadata").get("negocio").asText()).isEqualTo("empresa_demo");
            assertThat(peticion.cuerpo()).doesNotContain("sk_test");
        }
    }

    @Test
    void una_tarjeta_rechazada_es_un_resultado_con_su_motivo_no_un_error() {
        String rechazo = """
                {"object":"error","type":"card_error","code":"card_declined","decline_code":"insufficient_funds",
                 "user_message":"La tarjeta no tiene fondos suficientes","merchant_message":"insufficient funds"}""";
        try (var servidor = new ServidorFalso().responde(400, rechazo)) {
            ResultadoCobro resultado = adaptador(servidor).cobrar(solicitud());

            assertThat(resultado.estado()).isEqualTo(ResultadoCobro.Estado.RECHAZADO);
            assertThat(resultado.codigoRespuesta()).isEqualTo("card_declined");
            assertThat(resultado.mensajePasarela()).isEqualTo("La tarjeta no tiene fondos suficientes");
        }
    }

    @Test
    void si_la_pasarela_falla_se_avisa_que_no_se_sabe_que_paso() {
        for (int estado : new int[]{500, 502, 503, 429}) {
            try (var servidor = new ServidorFalso().responde(estado, "{}")) {
                assertThatThrownBy(() -> adaptador(servidor).cobrar(solicitud()))
                        .as("estado " + estado).isInstanceOf(ServicioExternoNoDisponibleException.class);
            }
        }
    }

    @Test
    void credenciales_invalidas_no_se_confunden_con_una_tarjeta_rechazada() {
        try (var servidor = new ServidorFalso().responde(401, "{\"object\":\"error\"}")) {
            assertThatThrownBy(() -> adaptador(servidor).cobrar(solicitud()))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class).hasMessageContaining("credenciales");
        }
    }

    @Test
    void sin_conexion_o_con_respuesta_ilegible_tampoco_se_sabe_que_paso() {
        ServidorFalso apagado = new ServidorFalso();
        CulqiPagoAdapter adaptador = adaptador(apagado);
        apagado.close();
        assertThatThrownBy(() -> adaptador.cobrar(solicitud())).isInstanceOf(ServicioExternoNoDisponibleException.class);

        try (var servidor = new ServidorFalso().responde(201, "esto no es json")) {
            assertThatThrownBy(() -> adaptador(servidor).cobrar(solicitud()))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class);
        }
    }

    @Test
    void una_respuesta_correcta_sin_confirmacion_explicita_queda_pendiente_y_no_se_da_por_aprobada() {
        try (var servidor = new ServidorFalso().responde(201, "{\"object\":\"charge\",\"id\":\"chr_test_Pend123\"}")) {
            ResultadoCobro resultado = adaptador(servidor).cobrar(solicitud());

            assertThat(resultado.estado()).isEqualTo(ResultadoCobro.Estado.PENDIENTE);
            assertThat(resultado.idCargo()).isEqualTo("chr_test_Pend123");
        }
    }

    @Test
    void consultar_un_cargo_devuelve_su_estado_real_y_sus_metadatos() {
        try (var servidor = new ServidorFalso().responde(200, CARGO_EXITOSO)) {
            var cargo = adaptador(servidor).consultarCargo("chr_test_Abc123XYZ");

            assertThat(cargo).isPresent();
            assertThat(cargo.get().estado()).isEqualTo(ResultadoCobro.Estado.APROBADO);
            assertThat(cargo.get().metadatos()).containsEntry("idPago", "5");
            assertThat(servidor.ultima().metodo()).isEqualTo("GET");
            assertThat(servidor.ultima().ruta()).isEqualTo("/v2/charges/chr_test_Abc123XYZ");
        }
    }

    @Test
    void un_identificador_raro_ni_siquiera_se_envia_a_la_pasarela() {
        try (var servidor = new ServidorFalso().responde(200, CARGO_EXITOSO)) {
            CulqiPagoAdapter adaptador = adaptador(servidor);

            assertThat(adaptador.consultarCargo("../../v2/tokens")).isEmpty();
            assertThat(adaptador.consultarCargo("chr_test_a b")).isEmpty();
            assertThat(adaptador.consultarCargo(null)).isEmpty();
            assertThat(servidor.peticiones).isEmpty();
        }
    }

    @Test
    void un_cargo_inexistente_da_vacio_y_una_falla_de_la_pasarela_da_error() {
        try (var servidor = new ServidorFalso().responde(404, "{}")) {
            assertThat(adaptador(servidor).consultarCargo("chr_test_Noexiste1")).isEmpty();
        }
        try (var servidor = new ServidorFalso().responde(500, "{}")) {
            assertThatThrownBy(() -> adaptador(servidor).consultarCargo("chr_test_Noexiste1"))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class);
        }
    }
}
