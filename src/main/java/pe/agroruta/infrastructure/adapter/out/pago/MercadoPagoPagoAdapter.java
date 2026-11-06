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

public class MercadoPagoPagoAdapter implements PagoPort {

    private static final Pattern FORMATO_PAGO = Pattern.compile("^\\d{3,20}$");
    private static final Pattern REFERENCIA = Pattern.compile("^([a-z][a-z0-9_]{2,30}):(\\d{1,18})$");

    private final ClienteHttp http;
    private final ObjectMapper json = new ObjectMapper();
    private final String urlBase;
    private final String tokenAcceso;

    public MercadoPagoPagoAdapter(ClienteHttp http, String urlBase, String tokenAcceso) {
        this.http = http;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.tokenAcceso = tokenAcceso;
    }

    @Override
    public ResultadoCobro cobrar(SolicitudCobro solicitud) {
        if (solicitud.metodoPago() == null || solicitud.metodoPago().isBlank()) {
            return ResultadoCobro.rechazado(null, "datos_invalidos", "Falta el medio de pago", solicitud.metadatos());
        }
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.put("transaction_amount", solicitud.monto().monto());
        cuerpo.put("token", solicitud.tokenTarjeta());
        cuerpo.put("description", solicitud.descripcion());
        cuerpo.put("installments", 1);
        cuerpo.put("payment_method_id", solicitud.metodoPago());
        if (solicitud.emisor() != null && !solicitud.emisor().isBlank()) {
            cuerpo.put("issuer_id", Long.parseLong(solicitud.emisor()));
        }
        cuerpo.putObject("payer").put("email", solicitud.email());
        String negocio = solicitud.metadatos().get("negocio");
        String idPago = solicitud.metadatos().get("idPago");
        cuerpo.put("external_reference", negocio + ":" + idPago);
        cuerpo.putObject("metadata").put("negocio", negocio).put("id_pago", idPago);

        ClienteHttp.Respuesta respuesta = http.enviar("POST", urlBase + "/v1/payments", escribir(cuerpo),
                "Authorization", "Bearer " + tokenAcceso, "X-Idempotency-Key", solicitud.claveIdempotencia(),
                "Accept", "application/json");
        if (respuesta.estado() >= 500 || respuesta.estado() == 429) {
            throw new ServicioExternoNoDisponibleException("Mercado Pago respondió " + respuesta.estado());
        }
        if (respuesta.estado() == 401 || respuesta.estado() == 403) {
            throw new ServicioExternoNoDisponibleException("Mercado Pago rechazó las credenciales del comercio");
        }
        JsonNode datos = leer(respuesta.cuerpo());
        if (respuesta.estado() / 100 == 2) {
            return aCobro(datos);
        }
        return ResultadoCobro.rechazado(null, primero(causa(datos, "code"), texto(datos, "error")),
                primero(causa(datos, "description"), texto(datos, "message")), solicitud.metadatos());
    }

    @Override
    public Optional<ResultadoCobro> consultarCargo(String idCargo) {
        if (idCargo == null || !FORMATO_PAGO.matcher(idCargo).matches()) {
            return Optional.empty();
        }
        ClienteHttp.Respuesta respuesta = http.enviar("GET", urlBase + "/v1/payments/" + idCargo, null,
                "Authorization", "Bearer " + tokenAcceso, "Accept", "application/json");
        if (respuesta.estado() == 404) {
            return Optional.empty();
        }
        if (respuesta.estado() / 100 != 2) {
            throw new ServicioExternoNoDisponibleException("Mercado Pago respondió " + respuesta.estado());
        }
        return Optional.of(aCobro(leer(respuesta.cuerpo())));
    }

    /**
     * approved = aprobado; rejected o cancelled = rechazado; todo lo demás (in_process, pending, authorized,
     * reembolsos...) queda pendiente: ante la duda no se da un cobro por aprobado ni por rechazado.
     */
    private ResultadoCobro aCobro(JsonNode datos) {
        String idPago = texto(datos, "id");
        String estado = texto(datos, "status");
        String detalle = texto(datos, "status_detail");
        Map<String, String> metadatos = metadatos(datos);
        if ("approved".equals(estado)) {
            return ResultadoCobro.aprobado(idPago, marca(texto(datos, "payment_method_id")),
                    ultimosCuatro(datos.path("card").path("last_four_digits").asText(null)), metadatos);
        }
        if ("rejected".equals(estado) || "cancelled".equals(estado)) {
            return ResultadoCobro.rechazado(idPago, primero(detalle, estado), detalle, metadatos);
        }
        return new ResultadoCobro(ResultadoCobro.Estado.PENDIENTE, idPago, detalle, null, null, null, metadatos);
    }

    /** Devuelve siempre las claves "negocio" e "idPago", vengan de los metadatos o de la referencia externa. */
    private Map<String, String> metadatos(JsonNode datos) {
        Map<String, String> resultado = new HashMap<>();
        datos.path("metadata").fields().forEachRemaining(campo -> {
            String clave = campo.getKey().toLowerCase().replace("_", "");
            if (clave.equals("negocio")) {
                resultado.put("negocio", campo.getValue().asText());
            } else if (clave.equals("idpago")) {
                resultado.put("idPago", campo.getValue().asText());
            }
        });
        if (!resultado.containsKey("negocio") || !resultado.containsKey("idPago")) {
            Matcher referencia = REFERENCIA.matcher(datos.path("external_reference").asText(""));
            if (referencia.matches()) {
                resultado.putIfAbsent("negocio", referencia.group(1));
                resultado.putIfAbsent("idPago", referencia.group(2));
            }
        }
        return resultado;
    }

    private static String marca(String metodo) {
        if (metodo == null) {
            return null;
        }
        return switch (metodo) {
            case "visa" -> "Visa";
            case "master" -> "Mastercard";
            case "amex" -> "American Express";
            case "diners" -> "Diners";
            default -> metodo.toUpperCase();
        };
    }

    private static String ultimosCuatro(String valor) {
        return valor != null && valor.matches("\\d{4}") ? valor : null;
    }

    private JsonNode leer(String cuerpo) {
        try {
            return json.readTree(cuerpo == null || cuerpo.isBlank() ? "{}" : cuerpo);
        } catch (JsonProcessingException e) {
            throw new ServicioExternoNoDisponibleException("Mercado Pago respondió algo que no se entiende", e);
        }
    }

    private String escribir(ObjectNode nodo) {
        try {
            return json.writeValueAsString(nodo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String causa(JsonNode datos, String campo) {
        JsonNode causas = datos.path("cause");
        return causas.isArray() && causas.size() > 0 ? texto(causas.get(0), campo) : null;
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
