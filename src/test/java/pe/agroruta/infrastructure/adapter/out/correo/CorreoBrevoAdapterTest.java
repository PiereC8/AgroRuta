package pe.agroruta.infrastructure.adapter.out.correo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.infrastructure.adapter.out.ServidorFalso;
import pe.agroruta.infrastructure.adapter.out.http.ClienteHttp;

class CorreoBrevoAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private CorreoBrevoAdapter adaptador(ServidorFalso servidor) {
        return new CorreoBrevoAdapter(new ClienteHttp(Duration.ofSeconds(2), Duration.ofSeconds(5)),
                servidor.url() + "/", "xkeysib-clave-secreta", "avisos@agroruta.pe");
    }

    @Test
    void envia_el_correo_por_la_api_con_la_clave_en_la_cabecera() throws Exception {
        try (var servidor = new ServidorFalso().responde(201, "{\"messageId\":\"<abc@brevo>\"}")) {
            adaptador(servidor).enviar("comprador@ejemplo.com", "Enlace de pago", "Pague aquí: https://x/pago");

            var peticion = servidor.ultima();
            assertThat(peticion.metodo()).isEqualTo("POST");
            assertThat(peticion.ruta()).isEqualTo("/v3/smtp/email");
            assertThat(peticion.cabecera("api-key")).isEqualTo("xkeysib-clave-secreta");
            JsonNode cuerpo = JSON.readTree(peticion.cuerpo());
            assertThat(cuerpo.get("sender").get("email").asText()).isEqualTo("avisos@agroruta.pe");
            assertThat(cuerpo.get("sender").get("name").asText()).isEqualTo("AgroRuta");
            assertThat(cuerpo.get("to").get(0).get("email").asText()).isEqualTo("comprador@ejemplo.com");
            assertThat(cuerpo.get("subject").asText()).isEqualTo("Enlace de pago");
            assertThat(cuerpo.get("textContent").asText()).isEqualTo("Pague aquí: https://x/pago");
            assertThat(peticion.cuerpo()).doesNotContain("xkeysib-clave-secreta");
        }
    }

    @Test
    void un_rechazo_de_brevo_es_un_error_con_su_motivo() throws Exception {
        try (var servidor = new ServidorFalso().responde(400,
                "{\"code\":\"invalid_parameter\",\"message\":\"sender is not valid\"}")) {
            assertThatThrownBy(() -> adaptador(servidor).enviar("a@b.com", "x", "y"))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class)
                    .hasMessageContaining("400")
                    .hasMessageContaining("sender is not valid");
        }
    }

    @Test
    void si_brevo_falla_el_correo_queda_para_reintentar() throws Exception {
        try (var servidor = new ServidorFalso().responde(503, "Service Unavailable")) {
            assertThatThrownBy(() -> adaptador(servidor).enviar("a@b.com", "x", "y"))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class)
                    .hasMessageContaining("503");
        }
    }
}
