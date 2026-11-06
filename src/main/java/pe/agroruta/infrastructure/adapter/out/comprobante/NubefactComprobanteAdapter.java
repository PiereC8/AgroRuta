package pe.agroruta.infrastructure.adapter.out.comprobante;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.port.out.ComprobantePort;
import pe.agroruta.infrastructure.adapter.out.http.ClienteHttp;

/**
 * Proveedor de comprobantes Nubefact (OSE/PSE), en su ambiente de pruebas o de producción según la URL.
 *
 * <p>Los nombres de los campos siguen la API JSON de Nubefact tal como se conoce, pero no se pudieron contrastar
 * contra su manual ni contra una cuenta real: hay que confirmarlos con la cuenta de pruebas.
 */
public class NubefactComprobanteAdapter implements ComprobantePort {

    /** Cómo se envía el token: solo el token, o con el formato Token token="..." */
    public enum FormatoAutorizacion {
        SOLO_TOKEN,
        TOKEN_TOKEN
    }

    private static final int CODIGO_YA_EXISTE = 23;
    private static final int OPERACION_EXONERADA = 8;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ClienteHttp http;
    private final ObjectMapper json = new ObjectMapper();
    private final String url;
    private final String token;
    private final FormatoAutorizacion formato;

    public NubefactComprobanteAdapter(ClienteHttp http, String url, String token, FormatoAutorizacion formato) {
        this.http = http;
        this.url = url;
        this.token = token;
        this.formato = formato;
    }

    @Override
    public ResultadoEmision emitir(DatosComprobante datos) {
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.put("operacion", "generar_comprobante");
        cuerpo.put("tipo_de_comprobante", codigoTipo(datos.tipo()));
        cuerpo.put("serie", datos.serie());
        cuerpo.put("numero", datos.numero());
        cuerpo.put("sunat_transaction", 1);
        DatosComprobante.Cliente cliente = datos.cliente();
        cuerpo.put("cliente_tipo_de_documento", cliente.tipoDocumento());
        cuerpo.put("cliente_numero_de_documento", cliente.numeroDocumento());
        cuerpo.put("cliente_denominacion", cliente.denominacion());
        cuerpo.put("cliente_direccion", "-");
        cuerpo.put("cliente_email", cliente.email());
        cuerpo.put("fecha_de_emision", datos.fechaEmision().format(FECHA));
        cuerpo.put("moneda", 1);
        cuerpo.put("porcentaje_de_igv", new BigDecimal("18.00"));
        cuerpo.put("total_exonerada", datos.total().monto());
        cuerpo.put("total_igv", BigDecimal.ZERO.setScale(2));
        cuerpo.put("total", datos.total().monto());
        cuerpo.put("enviar_automaticamente_a_la_sunat", true);
        cuerpo.put("enviar_automaticamente_al_cliente", false);
        ArrayNode items = cuerpo.putArray("items");
        for (DatosComprobante.Item item : datos.items()) {
            ObjectNode fila = items.addObject();
            fila.put("unidad_de_medida", item.unidadMedida());
            fila.put("codigo", item.codigo());
            fila.put("descripcion", item.descripcion());
            fila.put("cantidad", item.cantidad());
            fila.put("valor_unitario", item.valorUnitario().monto());
            fila.put("precio_unitario", item.valorUnitario().monto());
            fila.put("subtotal", item.total().monto());
            fila.put("tipo_de_igv", OPERACION_EXONERADA);
            fila.put("igv", BigDecimal.ZERO.setScale(2));
            fila.put("total", item.total().monto());
            fila.put("anticipo_regularizacion", false);
        }
        return procesar(enviar(cuerpo), datos.tipo(), datos.serie(), datos.numero());
    }

    @Override
    public ResultadoEmision consultar(TipoComprobante tipo, String serie, int numero) {
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.put("operacion", "consultar_comprobante");
        cuerpo.put("tipo_de_comprobante", codigoTipo(tipo));
        cuerpo.put("serie", serie);
        cuerpo.put("numero", numero);
        ClienteHttp.Respuesta respuesta = enviar(cuerpo);
        return procesar(respuesta, tipo, serie, numero);
    }

    private ClienteHttp.Respuesta enviar(ObjectNode cuerpo) {
        String autorizacion = formato == FormatoAutorizacion.TOKEN_TOKEN ? "Token token=\"" + token + "\"" : token;
        try {
            return http.enviar("POST", url, json.writeValueAsString(cuerpo), "Authorization", autorizacion);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResultadoEmision procesar(ClienteHttp.Respuesta respuesta, TipoComprobante tipo, String serie,
                                      int numero) {
        if (respuesta.estado() >= 500 || respuesta.estado() == 429) {
            throw new ServicioExternoNoDisponibleException("El proveedor de comprobantes respondió "
                    + respuesta.estado());
        }
        JsonNode datos = leer(respuesta.cuerpo());
        if (respuesta.estado() == 401 || respuesta.estado() == 403) {
            return new ResultadoEmision(EstadoComprobante.RECHAZADO, null, null,
                    "El proveedor rechazó las credenciales: revisa la URL y el token");
        }
        if (datos.has("errors")) {
            if (datos.path("codigo").asInt(-1) == CODIGO_YA_EXISTE) {
                // Ya se había emitido (por ejemplo, la respuesta anterior se perdió): se recupera el existente.
                return consultar(tipo, serie, numero);
            }
            return new ResultadoEmision(EstadoComprobante.RECHAZADO, null, null, datos.path("errors").asText());
        }
        boolean aceptada = datos.path("aceptada_por_sunat").asBoolean(false);
        String mensaje = datos.path("sunat_description").asText(null);
        if (aceptada) {
            return new ResultadoEmision(EstadoComprobante.ACEPTADO, datos.path("enlace_del_pdf").asText(null),
                    datos.path("enlace_del_xml").asText(null), mensaje);
        }
        if (mensaje != null && mensaje.toLowerCase().contains("observ")) {
            return new ResultadoEmision(EstadoComprobante.OBSERVADO, datos.path("enlace_del_pdf").asText(null),
                    datos.path("enlace_del_xml").asText(null), mensaje);
        }
        return new ResultadoEmision(EstadoComprobante.PENDIENTE, datos.path("enlace_del_pdf").asText(null),
                datos.path("enlace_del_xml").asText(null), mensaje);
    }

    private JsonNode leer(String cuerpo) {
        try {
            return json.readTree(cuerpo == null || cuerpo.isBlank() ? "{}" : cuerpo);
        } catch (JsonProcessingException e) {
            throw new ServicioExternoNoDisponibleException("El proveedor respondió algo que no se entiende", e);
        }
    }

    private static int codigoTipo(TipoComprobante tipo) {
        return tipo == TipoComprobante.FACTURA ? 1 : 2;
    }
}
