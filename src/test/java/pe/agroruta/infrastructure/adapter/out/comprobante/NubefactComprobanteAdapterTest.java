package pe.agroruta.infrastructure.adapter.out.comprobante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
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
import pe.agroruta.infrastructure.adapter.out.comprobante.NubefactComprobanteAdapter.FormatoAutorizacion;
import pe.agroruta.infrastructure.adapter.out.http.ClienteHttp;

class NubefactComprobanteAdapterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ACEPTADA = """
            {"aceptada_por_sunat":true,"sunat_description":"La Factura numero F001-1, ha sido aceptada",
             "enlace_del_pdf":"https://nubefact.test/pdf","enlace_del_xml":"https://nubefact.test/xml"}""";

    private NubefactComprobanteAdapter adaptador(ServidorFalso servidor, FormatoAutorizacion formato) {
        return new NubefactComprobanteAdapter(new ClienteHttp(Duration.ofSeconds(2), Duration.ofSeconds(5)),
                servidor.url() + "/api/v1/cuenta", "token-de-prueba", formato);
    }

    private DatosComprobante factura() {
        return new DatosComprobante(TipoComprobante.FACTURA, "F001", 1, LocalDate.of(2026, 11, 6),
                new DatosComprobante.Cliente("6", "20123456789", "Mercado Central SAC", "comprador@ejemplo.com"),
                List.of(new DatosComprobante.Item("KGM", "PROD-1", "Palta Hass", new BigDecimal("1180.000"),
                        Dinero.de("5.40"), Dinero.de("6372.00"))), Dinero.de("6372.00"));
    }

    @Test
    void emitir_envia_la_factura_exonerada_con_los_campos_de_la_api() throws Exception {
        try (var servidor = new ServidorFalso().responde(200, ACEPTADA)) {
            ResultadoEmision resultado = adaptador(servidor, FormatoAutorizacion.SOLO_TOKEN).emitir(factura());

            assertThat(resultado.estado()).isEqualTo(EstadoComprobante.ACEPTADO);
            assertThat(resultado.enlacePdf()).isEqualTo("https://nubefact.test/pdf");
            assertThat(resultado.enlaceXml()).isEqualTo("https://nubefact.test/xml");

            var peticion = servidor.ultima();
            assertThat(peticion.metodo()).isEqualTo("POST");
            assertThat(peticion.ruta()).isEqualTo("/api/v1/cuenta");
            assertThat(peticion.cabecera("Authorization")).isEqualTo("token-de-prueba");
            JsonNode cuerpo = JSON.readTree(peticion.cuerpo());
            assertThat(cuerpo.get("operacion").asText()).isEqualTo("generar_comprobante");
            assertThat(cuerpo.get("tipo_de_comprobante").asInt()).isEqualTo(1);
            assertThat(cuerpo.get("serie").asText()).isEqualTo("F001");
            assertThat(cuerpo.get("numero").asInt()).isEqualTo(1);
            assertThat(cuerpo.get("cliente_tipo_de_documento").asText()).isEqualTo("6");
            assertThat(cuerpo.get("cliente_numero_de_documento").asText()).isEqualTo("20123456789");
            assertThat(cuerpo.get("fecha_de_emision").asText()).isEqualTo("06-11-2026");
            assertThat(cuerpo.get("moneda").asInt()).isEqualTo(1);
            assertThat(cuerpo.get("total_exonerada").decimalValue()).isEqualByComparingTo("6372.00");
            assertThat(cuerpo.get("total_igv").decimalValue()).isEqualByComparingTo("0");
            assertThat(cuerpo.get("total").decimalValue()).isEqualByComparingTo("6372.00");
            JsonNode item = cuerpo.get("items").get(0);
            assertThat(item.get("unidad_de_medida").asText()).isEqualTo("KGM");
            assertThat(item.get("cantidad").decimalValue()).isEqualByComparingTo("1180");
            assertThat(item.get("valor_unitario").decimalValue()).isEqualByComparingTo("5.40");
            assertThat(item.get("tipo_de_igv").asInt()).isEqualTo(8);
            assertThat(item.get("igv").decimalValue()).isEqualByComparingTo("0");
        }
    }

    @Test
    void una_boleta_usa_el_tipo_2() throws Exception {
        try (var servidor = new ServidorFalso().responde(200, ACEPTADA)) {
            DatosComprobante boleta = new DatosComprobante(TipoComprobante.BOLETA, "B001", 3, LocalDate.of(2026, 11, 6),
                    new DatosComprobante.Cliente("-", "-", "CLIENTE VARIOS", "a@b.co"), factura().items(),
                    Dinero.de("6372.00"));

            adaptador(servidor, FormatoAutorizacion.SOLO_TOKEN).emitir(boleta);

            assertThat(JSON.readTree(servidor.ultima().cuerpo()).get("tipo_de_comprobante").asInt()).isEqualTo(2);
        }
    }

    @Test
    void el_token_puede_enviarse_con_el_formato_token_token() {
        try (var servidor = new ServidorFalso().responde(200, ACEPTADA)) {
            adaptador(servidor, FormatoAutorizacion.TOKEN_TOKEN).emitir(factura());

            assertThat(servidor.ultima().cabecera("Authorization")).isEqualTo("Token token=\"token-de-prueba\"");
        }
    }

    @Test
    void si_sunat_aun_no_lo_acepta_queda_pendiente_y_si_lo_observa_queda_observado() {
        try (var servidor = new ServidorFalso().responde(200,
                "{\"aceptada_por_sunat\":false,\"sunat_description\":\"En proceso\"}")) {
            assertThat(adaptador(servidor, FormatoAutorizacion.SOLO_TOKEN).emitir(factura()).estado())
                    .isEqualTo(EstadoComprobante.PENDIENTE);
        }
        try (var servidor = new ServidorFalso().responde(200,
                "{\"aceptada_por_sunat\":false,\"sunat_description\":\"Aceptada con observaciones\"}")) {
            assertThat(adaptador(servidor, FormatoAutorizacion.SOLO_TOKEN).emitir(factura()).estado())
                    .isEqualTo(EstadoComprobante.OBSERVADO);
        }
    }

    @Test
    void un_error_de_validacion_del_proveedor_queda_rechazado_con_su_mensaje() {
        try (var servidor = new ServidorFalso().responde(400, "{\"errors\":\"La serie no existe\",\"codigo\":10}")) {
            ResultadoEmision resultado = adaptador(servidor, FormatoAutorizacion.SOLO_TOKEN).emitir(factura());

            assertThat(resultado.estado()).isEqualTo(EstadoComprobante.RECHAZADO);
            assertThat(resultado.mensajeSunat()).isEqualTo("La serie no existe");
        }
    }

    @Test
    void si_el_comprobante_ya_existia_se_recupera_consultandolo_sin_emitir_otro() throws Exception {
        try (var servidor = new ServidorFalso()
                .responde(400, "{\"errors\":\"Documento ya existe\",\"codigo\":23}")
                .responde(200, ACEPTADA)) {
            ResultadoEmision resultado = adaptador(servidor, FormatoAutorizacion.SOLO_TOKEN).emitir(factura());

            assertThat(resultado.estado()).isEqualTo(EstadoComprobante.ACEPTADO);
            assertThat(servidor.peticiones).hasSize(2);
            assertThat(JSON.readTree(servidor.peticiones.get(1).cuerpo()).get("operacion").asText())
                    .isEqualTo("consultar_comprobante");
        }
    }

    @Test
    void consultar_pide_el_documento_por_tipo_serie_y_numero() throws Exception {
        try (var servidor = new ServidorFalso().responde(200, ACEPTADA)) {
            adaptador(servidor, FormatoAutorizacion.SOLO_TOKEN).consultar(TipoComprobante.BOLETA, "B001", 7);

            JsonNode cuerpo = JSON.readTree(servidor.ultima().cuerpo());
            assertThat(cuerpo.get("operacion").asText()).isEqualTo("consultar_comprobante");
            assertThat(cuerpo.get("tipo_de_comprobante").asInt()).isEqualTo(2);
            assertThat(cuerpo.get("serie").asText()).isEqualTo("B001");
            assertThat(cuerpo.get("numero").asInt()).isEqualTo(7);
        }
    }

    @Test
    void credenciales_malas_quedan_rechazadas_con_un_mensaje_claro() {
        try (var servidor = new ServidorFalso().responde(401, "{}")) {
            ResultadoEmision resultado = adaptador(servidor, FormatoAutorizacion.SOLO_TOKEN).emitir(factura());

            assertThat(resultado.estado()).isEqualTo(EstadoComprobante.RECHAZADO);
            assertThat(resultado.mensajeSunat()).contains("credenciales");
        }
    }

    @Test
    void si_el_proveedor_cae_o_responde_algo_ilegible_se_puede_reintentar_despues() {
        for (int estado : new int[]{500, 503, 429}) {
            try (var servidor = new ServidorFalso().responde(estado, "{}")) {
                assertThatThrownBy(() -> adaptador(servidor, FormatoAutorizacion.SOLO_TOKEN).emitir(factura()))
                        .as("estado " + estado).isInstanceOf(ServicioExternoNoDisponibleException.class);
            }
        }
        try (var servidor = new ServidorFalso().responde(200, "<html>no es json</html>")) {
            assertThatThrownBy(() -> adaptador(servidor, FormatoAutorizacion.SOLO_TOKEN).emitir(factura()))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class);
        }
        ServidorFalso apagado = new ServidorFalso();
        NubefactComprobanteAdapter adaptador = adaptador(apagado, FormatoAutorizacion.SOLO_TOKEN);
        apagado.close();
        assertThatThrownBy(() -> adaptador.emitir(factura())).isInstanceOf(ServicioExternoNoDisponibleException.class);
    }
}
