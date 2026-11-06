package pe.agroruta.infrastructure.adapter.out.pago;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.SolicitudCobro;
import pe.agroruta.domain.port.out.PagoPort;
import pe.agroruta.infrastructure.adapter.out.http.ClienteHttp;

/**
 * Pasarela Culqi. El navegador crea el token de la tarjeta con la llave pública; aquí solo se usa la llave secreta
 * para cobrar ese token. La llave secreta nunca sale del servidor ni se escribe en los registros.
 */
public class CulqiPagoAdapter implements PagoPort {

    private static final Pattern FORMATO_CARGO = Pattern.compile("^chr_[a-z]+_[A-Za-z0-9]{6,40}$");
    private static final Pattern ULTIMOS_CUATRO = Pattern.compile("(\\d{4})\\D*$");

    private final ClienteHttp http;
    private final ObjectMapper json = new ObjectMapper();
    private final String urlBase;
    private final String llaveSecreta;

    public CulqiPagoAdapter(ClienteHttp http, String urlBase, String llaveSecreta) {
        this.http = http;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.llaveSecreta = llaveSecreta;
    }

    @Override
    public ResultadoCobro cobrar(SolicitudCobro solicitud) {
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.put("amount", solicitud.monto().monto().movePointRight(2).longValueExact());
        cuerpo.put("currency_code", "PEN");
        cuerpo.put("email", solicitud.email());
        cuerpo.put("source_id", solicitud.tokenTarjeta());
        cuerpo.put("description", solicitud.descripcion());
        ObjectNode metadatos = cuerpo.putObject("metadata");
        solicitud.metadatos().forEach(metadatos::put);

        ClienteHttp.Respuesta respuesta = http.enviar("POST", urlBase + "/charges", escribir(cuerpo),
                "Authorization", "Bearer " + llaveSecreta);
        if (respuesta.estado() >= 500 || respuesta.estado() == 429) {
            throw new ServicioExternoNoDisponibleException("Culqi respondió " + respuesta.estado());
        }
        JsonNode datos = leer(respuesta.cuerpo());
        if (respuesta.estado() / 100 == 2) {
            return aCobro(datos).orElseGet(() -> new ResultadoCobro(ResultadoCobro.Estado.PENDIENTE,
                    texto(datos, "id"), null, null, null, null, metadatos(datos)));
        }
        if (respuesta.estado() == 401 || respuesta.estado() == 403) {
            throw new ServicioExternoNoDisponibleException("Culqi rechazó las credenciales del comercio");
        }
        String codigo = primero(texto(datos, "code"), texto(datos, "decline_code"), texto(datos, "type"));
        String mensaje = primero(texto(datos, "user_message"), texto(datos, "merchant_message"));
        return ResultadoCobro.rechazado(texto(datos, "charge_id"), codigo, mensaje, solicitud.metadatos());
    }

    @Override
    public Optional<ResultadoCobro> consultarCargo(String idCargo) {
        if (idCargo == null || !FORMATO_CARGO.matcher(idCargo).matches()) {
            return Optional.empty();
        }
        ClienteHttp.Respuesta respuesta = http.enviar("GET", urlBase + "/charges/" + idCargo, null,
                "Authorization", "Bearer " + llaveSecreta);
        if (respuesta.estado() == 404) {
            return Optional.empty();
        }
        if (respuesta.estado() / 100 != 2) {
            throw new ServicioExternoNoDisponibleException("Culqi respondió " + respuesta.estado());
        }
        JsonNode datos = leer(respuesta.cuerpo());
        return Optional.of(aCobro(datos).orElseGet(() -> new ResultadoCobro(ResultadoCobro.Estado.PENDIENTE,
                texto(datos, "id"), null, null, null, null, metadatos(datos))));
    }

    /** Un cargo es exitoso solo si la pasarela lo dice de forma explícita; ante la duda, queda pendiente. */
    private Optional<ResultadoCobro> aCobro(JsonNode datos) {
        boolean exitoso = "venta_exitosa".equals(texto(datos, "response_code"))
                || "venta_exitosa".equals(datos.path("outcome").path("type").asText(null))
                || "Exitosa".equalsIgnoreCase(texto(datos, "state"));
        if (!exitoso) {
            return Optional.empty();
        }
        JsonNode origen = datos.path("source");
        String marca = origen.path("iin").path("card_brand").asText(null);
        Matcher cuatro = ULTIMOS_CUATRO.matcher(origen.path("card_number").asText(""));
        return Optional.of(ResultadoCobro.aprobado(texto(datos, "id"), marca, cuatro.find() ? cuatro.group(1) : null,
                metadatos(datos)));
    }

    private Map<String, String> metadatos(JsonNode datos) {
        Map<String, String> resultado = new HashMap<>();
        datos.path("metadata").fields().forEachRemaining(campo -> resultado.put(campo.getKey(),
                campo.getValue().asText()));
        return resultado;
    }

    private JsonNode leer(String cuerpo) {
        try {
            return json.readTree(cuerpo == null || cuerpo.isBlank() ? "{}" : cuerpo);
        } catch (JsonProcessingException e) {
            throw new ServicioExternoNoDisponibleException("Culqi respondió algo que no se entiende", e);
        }
    }

    private String escribir(ObjectNode nodo) {
        try {
            return json.writeValueAsString(nodo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() ? null : valor.asText();
    }

    private static String primero(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return null;
    }
}
