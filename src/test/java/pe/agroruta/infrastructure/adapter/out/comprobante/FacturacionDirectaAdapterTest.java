package pe.agroruta.infrastructure.adapter.out.comprobante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.infrastructure.adapter.out.ServidorFalso;
import pe.agroruta.infrastructure.adapter.out.http.ClienteHttp;

class FacturacionDirectaAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-08T04:50:00Z"), ZoneOffset.UTC);
    private static final FacturacionDirectaAdapter.Emisor EMISOR = new FacturacionDirectaAdapter.Emisor(
            "20000000001", "AGRORUTA DEMO", "AGRORUTA", "AV. BOLOGNESI 100", "230101", "TACNA", "TACNA", "TACNA");
    private static final String ACEPTADA = """
            {"data":{"error":"false","respuesta_sunat_codigo":"0",
             "respuesta_sunat_descripcion":"La Boleta numero B001-7, ha sido aceptada",
             "ruta_xml":"http://facturaciondirecta.com/API_SUNAT/files/facturacion_electronica/FIRMA/20000000001-03-B001-7.xml",
             "ruta_cdr":"http://facturaciondirecta.com/API_SUNAT/files/facturacion_electronica/CDR/R-20000000001-03-B001-7.xml",
             "ruta_pdf":"http://facturaciondirecta.com/API_SUNAT/files/facturacion_electronica/PDF/20000000001-03-B001-7.pdf"}}""";

    private FacturacionDirectaAdapter adaptador(ServidorFalso servidor) {
        return new FacturacionDirectaAdapter(new ClienteHttp(Duration.ofSeconds(2), Duration.ofSeconds(5)),
                servidor.url() + "/API_SUNAT/", EMISOR, RELOJ);
    }

    private static DatosComprobante boletaSinDocumento() {
        return new DatosComprobante(TipoComprobante.BOLETA, "B001", 7, LocalDate.of(2026, 11, 7),
                new DatosComprobante.Cliente("-", "-", "CLIENTE VARIOS", "comprador@ejemplo.com"),
                List.of(new DatosComprobante.Item("KGM", "PROD-3", "Orégano", new BigDecimal("95.000"),
                        Dinero.de("5.00"), Dinero.de("475.00"))),
                Dinero.de("475.00"));
    }

    private static DatosComprobante facturaConRuc() {
        return new DatosComprobante(TipoComprobante.FACTURA, "F001", 2, LocalDate.of(2026, 11, 7),
                new DatosComprobante.Cliente("6", "20123456789", "MERCADO MAYORISTA SAC", "compras@ejemplo.com"),
                boletaSinDocumento().items(), Dinero.de("475.00"));
    }

    @Test
    void emitir_envia_la_trama_al_ambiente_beta_con_el_producto_exonerado() throws Exception {
        try (var servidor = new ServidorFalso().responde(200, ACEPTADA)) {
            adaptador(servidor).emitir(boletaSinDocumento());

            var peticion = servidor.ultima();
            assertThat(peticion.metodo()).isEqualTo("POST");
            assertThat(peticion.ruta()).isEqualTo("/API_SUNAT/post.php");
            JsonNode trama = JSON.readTree(peticion.cuerpo());
            assertThat(trama.at("/empresa/ruc").asText()).isEqualTo("20000000001");
            assertThat(trama.at("/empresa/modo").asText()).isEqualTo("0");
            assertThat(trama.at("/empresa/usu_secundario_produccion_user").asText()).isEqualTo("MODDATOS");
            assertThat(trama.at("/venta/tipo_documento_codigo").asText()).isEqualTo("03");
            assertThat(trama.at("/venta/serie").asText()).isEqualTo("B001");
            assertThat(trama.at("/venta/numero").asText()).isEqualTo("7");
            assertThat(trama.at("/venta/fecha_emision").asText()).isEqualTo("2026-11-07");
            assertThat(trama.at("/venta/hora_emision").asText()).isEqualTo("23:50:00");
            assertThat(trama.at("/venta/moneda_id").asText()).isEqualTo("1");
            assertThat(trama.at("/venta/total_exonerada").asText()).isEqualTo("475.00");
            assertThat(trama.at("/venta/total_gravada").asText()).isEmpty();
            JsonNode item = trama.at("/items/0");
            assertThat(item.get("producto").asText()).isEqualTo("Orégano");
            assertThat(item.get("cantidad").asText()).isEqualTo("95");
            assertThat(item.get("precio_base").asText()).isEqualTo("5.00");
            assertThat(item.get("codigo_unidad").asText()).isEqualTo("KGM");
            assertThat(item.get("tipo_igv_codigo").asText()).isEqualTo("20");
            assertThat(item.get("codigo_sunat").asText()).isEqualTo("50000000");
        }
    }

    @Test
    void un_cliente_sin_documento_va_como_clientes_varios_con_dni_de_ceros() throws Exception {
        try (var servidor = new ServidorFalso().responde(200, ACEPTADA)) {
            adaptador(servidor).emitir(boletaSinDocumento());

            JsonNode cliente = JSON.readTree(servidor.ultima().cuerpo()).get("cliente");
            assertThat(cliente.get("codigo_tipo_entidad").asText()).isEqualTo("1");
            assertThat(cliente.get("numero_documento").asText()).isEqualTo("00000000");
            assertThat(cliente.get("razon_social_nombres").asText()).isEqualTo("CLIENTE VARIOS");
        }
    }

    @Test
    void una_factura_lleva_el_ruc_del_cliente_y_el_codigo_01() throws Exception {
        try (var servidor = new ServidorFalso().responde(200, ACEPTADA)) {
            adaptador(servidor).emitir(facturaConRuc());

            JsonNode trama = JSON.readTree(servidor.ultima().cuerpo());
            assertThat(trama.at("/venta/tipo_documento_codigo").asText()).isEqualTo("01");
            assertThat(trama.at("/cliente/codigo_tipo_entidad").asText()).isEqualTo("6");
            assertThat(trama.at("/cliente/numero_documento").asText()).isEqualTo("20123456789");
        }
    }

    @Test
    void aceptado_devuelve_los_enlaces_en_https() {
        try (var servidor = new ServidorFalso().responde(200, ACEPTADA)) {
            ResultadoEmision resultado = adaptador(servidor).emitir(boletaSinDocumento());

            assertThat(resultado.estado()).isEqualTo(EstadoComprobante.ACEPTADO);
            assertThat(resultado.mensajeSunat()).isEqualTo("La Boleta numero B001-7, ha sido aceptada");
            assertThat(resultado.enlacePdf()).isEqualTo(
                    "https://facturaciondirecta.com/API_SUNAT/files/facturacion_electronica/PDF/20000000001-03-B001-7.pdf");
            assertThat(resultado.enlaceXml()).startsWith("https://").endsWith("/FIRMA/20000000001-03-B001-7.xml");
        }
    }

    @Test
    void se_lee_la_respuesta_aunque_traiga_avisos_de_php_despues_del_json() {
        String conAviso = ACEPTADA + "<br />\n<b>Notice</b>:  Undefined variable: tipo_documento_cliente<br />";
        try (var servidor = new ServidorFalso().responde(200, conAviso)) {
            assertThat(adaptador(servidor).emitir(boletaSinDocumento()).estado())
                    .isEqualTo(EstadoComprobante.ACEPTADO);
        }
    }

    @Test
    void un_error_de_sunat_es_un_rechazo_con_su_descripcion() {
        String rechazo = """
                {"data":{"error":"true","respuesta_sunat_codigo":"soap-env:Client.3496",
                 "respuesta_sunat_descripcion":"El Código producto de SUNAT no es válido"}}""";
        try (var servidor = new ServidorFalso().responde(200, rechazo)) {
            ResultadoEmision resultado = adaptador(servidor).emitir(boletaSinDocumento());

            assertThat(resultado.estado()).isEqualTo(EstadoComprobante.RECHAZADO);
            assertThat(resultado.mensajeSunat()).isEqualTo("El Código producto de SUNAT no es válido");
            assertThat(resultado.enlacePdf()).isNull();
        }
    }

    @Test
    void los_codigos_desde_4000_son_aceptados_con_observaciones() {
        assertThat(FacturacionDirectaAdapter.estadoPorCodigo(0)).isEqualTo(EstadoComprobante.ACEPTADO);
        assertThat(FacturacionDirectaAdapter.estadoPorCodigo(4252)).isEqualTo(EstadoComprobante.OBSERVADO);
        assertThat(FacturacionDirectaAdapter.estadoPorCodigo(2800)).isEqualTo(EstadoComprobante.RECHAZADO);
        assertThat(FacturacionDirectaAdapter.estadoPorCodigo(-1)).isEqualTo(EstadoComprobante.RECHAZADO);
    }

    @Test
    void si_el_proveedor_falla_o_responde_algo_ilegible_se_reintentara_despues() {
        try (var servidor = new ServidorFalso().responde(500, "{}")) {
            assertThatThrownBy(() -> adaptador(servidor).emitir(boletaSinDocumento()))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class);
        }
        try (var servidor = new ServidorFalso().responde(200, "<html>Error interno</html>")) {
            assertThatThrownBy(() -> adaptador(servidor).emitir(boletaSinDocumento()))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class);
        }
    }

    @Test
    void consultar_busca_la_constancia_guardada_y_si_no_existe_queda_pendiente() {
        String cdr = "<ar:ApplicationResponse><cbc:ResponseCode>0</cbc:ResponseCode>"
                + "<cbc:Description>La Boleta numero B001-7, ha sido aceptada</cbc:Description></ar:ApplicationResponse>";
        try (var servidor = new ServidorFalso().responde(200, cdr)) {
            ResultadoEmision resultado = adaptador(servidor).consultar(TipoComprobante.BOLETA, "B001", 7);

            assertThat(servidor.ultima().metodo()).isEqualTo("GET");
            assertThat(servidor.ultima().ruta())
                    .isEqualTo("/API_SUNAT/files/facturacion_electronica/CDR/R-20000000001-03-B001-7.xml");
            assertThat(resultado.estado()).isEqualTo(EstadoComprobante.ACEPTADO);
            assertThat(resultado.mensajeSunat()).isEqualTo("La Boleta numero B001-7, ha sido aceptada");
            assertThat(resultado.enlacePdf()).endsWith("/PDF/20000000001-03-B001-7.pdf");
        }
        try (var servidor = new ServidorFalso().responde(404, "")) {
            assertThat(adaptador(servidor).consultar(TipoComprobante.FACTURA, "F001", 2).estado())
                    .isEqualTo(EstadoComprobante.PENDIENTE);
            assertThat(servidor.ultima().ruta()).endsWith("/CDR/R-20000000001-01-F001-2.xml");
        }
    }

    @Test
    void si_sunat_dice_que_ya_fue_registrado_se_toma_la_constancia_existente() {
        String yaRegistrado = """
                {"data":{"error":"true","respuesta_sunat_codigo":"soap-env:Client.1033",
                 "respuesta_sunat_descripcion":"El comprobante fue registrado previamente con otros datos"}}""";
        String cdr = "<cbc:ResponseCode>0</cbc:ResponseCode><cbc:Description>Aceptada</cbc:Description>";
        try (var servidor = new ServidorFalso().responde(200, yaRegistrado).responde(200, cdr)) {
            ResultadoEmision resultado = adaptador(servidor).emitir(boletaSinDocumento());

            assertThat(resultado.estado()).isEqualTo(EstadoComprobante.ACEPTADO);
            assertThat(servidor.peticiones).hasSize(2);
        }
    }
}
