package pe.agroruta.infrastructure.adapter.out.comprobante;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
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
 * Envía el comprobante a SUNAT por la API de facturaciondirecta.com (Facturación Integral): la API arma el XML UBL 2.1,
 * lo firma, lo envía y devuelve los enlaces del XML, la constancia (CDR) y el PDF. Solo se usa el ambiente beta de
 * SUNAT (modo "0", usuario MODDATOS): no tiene validez tributaria y no necesita RUC propio ni certificado digital.
 */
public class FacturacionDirectaAdapter implements ComprobantePort {

    private static final String MODO_BETA = "0";
    private static final String USUARIO_BETA = "MODDATOS";
    // Productos agrícolas en estado natural: exonerados del IGV (Apéndice I de la Ley del IGV).
    private static final String IGV_EXONERADO = "20";
    // Catálogo de productos de SUNAT (UNSPSC): alimentos, bebidas y tabaco.
    private static final String CODIGO_PRODUCTO_SUNAT = "50000000";
    private static final String MONEDA_SOLES = "1";
    private static final String PAGO_CONTADO = "1";
    // Ya enviado antes (por ejemplo, la respuesta se perdió y se reintenta): se mira la constancia guardada.
    private static final String YA_REGISTRADO = "1033";
    private static final Pattern CODIGO_SUNAT = Pattern.compile("(\\d{1,4})$");
    private static final Pattern CODIGO_CDR = Pattern.compile("<cbc:ResponseCode>(\\d+)</cbc:ResponseCode>");
    private static final Pattern DESCRIPCION_CDR = Pattern.compile("<cbc:Description>([^<]*)</cbc:Description>");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ClienteHttp http;
    private final String urlBase;
    private final DatosEmisor emisor;
    private final Clock reloj;
    private final ObjectMapper json = new ObjectMapper();

    public FacturacionDirectaAdapter(ClienteHttp http, String urlBase, DatosEmisor emisor, Clock reloj) {
        this.http = http;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.emisor = emisor;
        this.reloj = reloj;
    }

    @Override
    public ResultadoEmision emitir(DatosComprobante datos) {
        ClienteHttp.Respuesta respuesta = http.enviar("POST", urlBase + "/post.php", trama(datos));
        if (respuesta.estado() != 200) {
            throw new ServicioExternoNoDisponibleException(
                    "El proveedor de comprobantes respondió con el estado " + respuesta.estado());
        }
        JsonNode resultado = leer(respuesta.cuerpo()).path("data");
        String codigo = resultado.path("respuesta_sunat_codigo").asText("");
        String descripcion = resultado.path("respuesta_sunat_descripcion").asText("Sin descripción de SUNAT");
        if (!"true".equals(resultado.path("error").asText())) {
            EstadoComprobante estado = estadoPorCodigo(numeroDe(codigo));
            return new ResultadoEmision(estado, seguro(resultado.path("ruta_pdf").asText(null)),
                    seguro(resultado.path("ruta_xml").asText(null)), descripcion);
        }
        int numero = numeroDe(codigo);
        if (String.valueOf(numero).equals(YA_REGISTRADO)) {
            return consultar(datos.tipo(), datos.serie(), datos.numero());
        }
        return new ResultadoEmision(EstadoComprobante.RECHAZADO, null, null, recortar(descripcion));
    }

    /** No hay un servicio de consulta: se busca la constancia (CDR) que el proveedor guarda para cada comprobante. */
    @Override
    public ResultadoEmision consultar(TipoComprobante tipo, String serie, int numero) {
        String nombre = emisor.ruc() + "-" + codigoTipo(tipo) + "-" + serie + "-" + numero;
        String carpeta = urlBase + "/files/facturacion_electronica/";
        ClienteHttp.Respuesta cdr = http.enviar("GET", carpeta + "CDR/R-" + nombre + ".xml", null);
        if (cdr.estado() == 404) {
            return new ResultadoEmision(EstadoComprobante.PENDIENTE, null, null,
                    "SUNAT todavía no tiene una constancia de este comprobante");
        }
        if (cdr.estado() != 200) {
            throw new ServicioExternoNoDisponibleException(
                    "El proveedor de comprobantes respondió con el estado " + cdr.estado());
        }
        Matcher codigo = CODIGO_CDR.matcher(cdr.cuerpo());
        Matcher descripcion = DESCRIPCION_CDR.matcher(cdr.cuerpo());
        EstadoComprobante estado = codigo.find() ? estadoPorCodigo(Integer.parseInt(codigo.group(1)))
                : EstadoComprobante.PENDIENTE;
        return new ResultadoEmision(estado, seguro(carpeta + "PDF/" + nombre + ".pdf"),
                seguro(carpeta + "FIRMA/" + nombre + ".xml"),
                descripcion.find() ? descripcion.group(1) : "Constancia de SUNAT encontrada");
    }

    String trama(DatosComprobante datos) {
        ObjectNode raiz = json.createObjectNode();
        ObjectNode empresa = raiz.putObject("empresa");
        empresa.put("ruc", emisor.ruc());
        empresa.put("razon_social", emisor.razonSocial());
        empresa.put("nombre_comercial", emisor.nombreComercial());
        empresa.put("domicilio_fiscal", emisor.domicilioFiscal());
        empresa.put("ubigeo", emisor.ubigeo());
        empresa.put("urbanizacion", "");
        empresa.put("distrito", emisor.distrito());
        empresa.put("provincia", emisor.provincia());
        empresa.put("departamento", emisor.departamento());
        empresa.put("modo", MODO_BETA);
        empresa.put("usu_secundario_produccion_user", USUARIO_BETA);
        empresa.put("usu_secundario_produccion_password", USUARIO_BETA);

        // Cliente sin documento (boleta menor a S/ 700): se usa la convención "clientes varios", DNI 00000000.
        DatosComprobante.Cliente cliente = datos.cliente();
        boolean sinDocumento = "-".equals(cliente.tipoDocumento());
        ObjectNode nodoCliente = raiz.putObject("cliente");
        nodoCliente.put("razon_social_nombres", cliente.denominacion());
        nodoCliente.put("numero_documento", sinDocumento ? "00000000" : cliente.numeroDocumento());
        nodoCliente.put("codigo_tipo_entidad", sinDocumento ? "1" : cliente.tipoDocumento());
        nodoCliente.put("cliente_direccion", "");

        ObjectNode venta = raiz.putObject("venta");
        venta.put("serie", datos.serie());
        venta.put("numero", String.valueOf(datos.numero()));
        venta.put("fecha_emision", datos.fechaEmision().toString());
        venta.put("hora_emision", LocalTime.now(reloj.withZone(LIMA)).format(HORA));
        venta.put("fecha_vencimiento", "");
        venta.put("moneda_id", MONEDA_SOLES);
        venta.put("forma_pago_id", PAGO_CONTADO);
        venta.put("total_gravada", "");
        venta.put("total_igv", "");
        venta.put("total_exonerada", datos.total().monto().toPlainString());
        venta.put("total_inafecta", "");
        venta.put("tipo_documento_codigo", codigoTipo(datos.tipo()));
        venta.put("nota", "");

        ArrayNode items = raiz.putArray("items");
        for (DatosComprobante.Item item : datos.items()) {
            ObjectNode nodo = items.addObject();
            nodo.put("producto", item.descripcion());
            nodo.put("cantidad", item.cantidad().stripTrailingZeros().toPlainString());
            nodo.put("precio_base", item.valorUnitario().monto().toPlainString());
            nodo.put("codigo_sunat", CODIGO_PRODUCTO_SUNAT);
            nodo.put("codigo_producto", item.codigo());
            nodo.put("codigo_unidad", item.unidadMedida());
            nodo.put("tipo_igv_codigo", IGV_EXONERADO);
        }
        return raiz.toString();
    }

    /** Códigos de SUNAT: 0 aceptado, desde 4000 aceptado con observaciones, el resto es un rechazo o un error. */
    static EstadoComprobante estadoPorCodigo(int codigo) {
        if (codigo == 0) {
            return EstadoComprobante.ACEPTADO;
        }
        return codigo >= 4000 ? EstadoComprobante.OBSERVADO : EstadoComprobante.RECHAZADO;
    }

    private static String codigoTipo(TipoComprobante tipo) {
        return tipo == TipoComprobante.FACTURA ? "01" : "03";
    }

    /** "0", "4252" o "soap-env:Client.3496" → el número del final. Sin número se toma como error. */
    private static int numeroDe(String codigo) {
        Matcher m = CODIGO_SUNAT.matcher(codigo == null ? "" : codigo.trim());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** El proveedor devuelve enlaces http://, pero también los sirve por https (la pantalla solo muestra https). */
    private static String seguro(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
    }

    private static String recortar(String texto) {
        return texto.length() > 250 ? texto.substring(0, 250) : texto;
    }

    /** La API a veces agrega avisos de PHP en HTML después del JSON: se lee solo el primer objeto. */
    private JsonNode leer(String cuerpo) {
        try (JsonParser lector = json.createParser(cuerpo == null ? "{}" : cuerpo)) {
            JsonNode nodo = lector.readValueAsTree();
            return nodo == null ? json.createObjectNode() : nodo;
        } catch (IOException e) {
            throw new ServicioExternoNoDisponibleException("El proveedor de comprobantes devolvió una respuesta ilegible",
                    e);
        }
    }
}
