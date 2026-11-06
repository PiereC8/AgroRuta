package pe.agroruta.infrastructure.adapter.in.web;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.agroruta.domain.port.in.ConfirmarPagoUseCase;

/**
 * Notificaciones de la pasarela. No se confía en lo que dicen: solo se toma el identificador del pago y se
 * pregunta a la pasarela por su estado real (RNF-16). Se responde 200 enseguida para que no la reintente sin necesidad.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookPagoController {

    private static final Pattern FORMATO_ID = Pattern.compile("^[A-Za-z0-9_-]{3,64}$");

    private final ConfirmarPagoUseCase confirmarPago;
    private final ObjectMapper json = new ObjectMapper();

    public WebhookPagoController(ConfirmarPagoUseCase confirmarPago) {
        this.confirmarPago = confirmarPago;
    }

    @PostMapping("/pago")
    public Map<String, Object> pago(@RequestBody JsonNode evento) {
        String idPago = idDelPago(evento);
        if (idPago == null) {
            return Map.of("recibido", true, "procesado", false);
        }
        return Map.of("recibido", true, "procesado", confirmarPago.confirmar(idPago));
    }

    /** Solo se atienden las notificaciones de pagos. El identificador viene en data.id (como texto o como objeto). */
    private String idDelPago(JsonNode evento) {
        String tipo = evento.path("type").asText(null);
        if (tipo != null && !"payment".equals(tipo)) {
            return null;
        }
        JsonNode datos = evento.path("data");
        try {
            if (datos.isTextual()) {
                datos = json.readTree(datos.asText());
            }
        } catch (JsonProcessingException e) {
            return null;
        }
        String id = datos.path("id").asText(null);
        return id != null && FORMATO_ID.matcher(id).matches() ? id : null;
    }
}
