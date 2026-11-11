package pe.agroruta.infrastructure.adapter.out.correo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.port.out.EnvioCorreoPort;
import pe.agroruta.infrastructure.adapter.out.http.ClienteHttp;

/**
 * Manda los correos por la API web de Brevo (HTTPS). Sirve donde el SMTP está bloqueado, como el plan gratis de
 * Render. El remitente tiene que estar verificado en la cuenta de Brevo.
 */
public class CorreoBrevoAdapter implements EnvioCorreoPort {

    private static final String NOMBRE_REMITENTE = "AgroRuta";

    private final ClienteHttp http;
    private final ObjectMapper json = new ObjectMapper();
    private final String urlBase;
    private final String claveApi;
    private final String remitente;

    public CorreoBrevoAdapter(ClienteHttp http, String urlBase, String claveApi, String remitente) {
        this.http = http;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.claveApi = claveApi;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String correoDestino, String asunto, String cuerpo) {
        ObjectNode mensaje = json.createObjectNode();
        mensaje.putObject("sender").put("name", NOMBRE_REMITENTE).put("email", remitente);
        mensaje.putArray("to").addObject().put("email", correoDestino);
        mensaje.put("subject", asunto);
        mensaje.put("textContent", cuerpo);

        ClienteHttp.Respuesta respuesta = http.enviar("POST", urlBase + "/v3/smtp/email", escribir(mensaje),
                "api-key", claveApi, "Accept", "application/json");
        if (respuesta.estado() < 200 || respuesta.estado() >= 300) {
            throw new ServicioExternoNoDisponibleException(
                    "Brevo no aceptó el correo (" + respuesta.estado() + "): " + motivo(respuesta.cuerpo()));
        }
    }

    private String escribir(ObjectNode nodo) {
        try {
            return json.writeValueAsString(nodo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo armar el correo", e);
        }
    }

    /** Brevo explica el error en "message"; si la respuesta no es JSON, se usa tal cual (recortada). */
    private String motivo(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return "sin detalle";
        }
        try {
            JsonNode nodo = json.readTree(cuerpo);
            if (nodo.hasNonNull("message")) {
                return nodo.get("message").asText();
            }
        } catch (JsonProcessingException e) {
            // no es JSON: se devuelve el texto
        }
        return cuerpo.length() > 200 ? cuerpo.substring(0, 200) : cuerpo;
    }
}
