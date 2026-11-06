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
 * Notificaciones de la pasarela. No se confía en lo que dicen: solo se toma el identificador del cargo y se
 * pregunta a la pasarela por su estado real (RNF-16).
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookPagoController {

    private static final Pattern FORMATO_CARGO = Pattern.compile("^chr_[a-z]+_[A-Za-z0-9]{6,40}$");

    private final ConfirmarPagoUseCase confirmarPago;
    private final ObjectMapper json = new ObjectMapper();

    public WebhookPagoController(ConfirmarPagoUseCase confirmarPago) {
        this.confirmarPago = confirmarPago;
    }

    @PostMapping("/pago")
    public Map<String, Object> pago(@RequestBody JsonNode evento) {
        String idCargo = idDelCargo(evento);
        if (idCargo == null) {
            return Map.of("recibido", true, "procesado", false);
        }
        return Map.of("recibido", true, "procesado", confirmarPago.confirmar(idCargo));
    }

    /** El cargo puede venir como objeto o como texto JSON dentro de "data". */
    private String idDelCargo(JsonNode evento) {
        JsonNode datos = evento.path("data");
        try {
            if (datos.isTextual()) {
                datos = json.readTree(datos.asText());
            }
        } catch (JsonProcessingException e) {
            return null;
        }
        String id = datos.path("id").asText(null);
        return id != null && FORMATO_CARGO.matcher(id).matches() ? id : null;
    }
}
