package pe.agroruta.infrastructure.adapter.out.comprobante;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.ComprobanteImpreso;
import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.TipoComprobante;

class ComprobantePdfTest {

    private static final DatosEmisor EMISOR = new DatosEmisor("20000000001", "AGRORUTA DEMO", "AGRORUTA",
            "AV. BOLOGNESI 100", "230101", "TACNA", "TACNA", "TACNA");
    private static final String AVISO = "Emitido en el ambiente de pruebas (beta) de SUNAT: no tiene validez tributaria.";
    private static final Instant AHORA = Instant.parse("2026-11-08T05:00:00Z");

    private static String texto(byte[] pdf) throws Exception {
        PdfReader lector = new PdfReader(pdf);
        String texto = new PdfTextExtractor(lector).getTextFromPage(1);
        lector.close();
        return texto;
    }

    private static List<DatosComprobante.Item> oregano() {
        return List.of(new DatosComprobante.Item("KGM", "PROD-1", "Orégano", new BigDecimal("95.000"),
                Dinero.de("5.00"), Dinero.de("475.00")));
    }

    private static ComprobanteImpreso boleta() {
        Comprobante comprobante = Comprobante.nuevo(1L, TipoComprobante.BOLETA, "B771", 1, LocalDate.of(2026, 11, 8),
                AHORA).conResultado(new ResultadoEmision(EstadoComprobante.ACEPTADO, null, null,
                "La Boleta numero B771-1, ha sido aceptada"), AHORA);
        DatosComprobante datos = new DatosComprobante(TipoComprobante.BOLETA, "B771", 1, LocalDate.of(2026, 11, 8),
                new DatosComprobante.Cliente("-", "-", "CLIENTE VARIOS", "comprador@ejemplo.com"), oregano(),
                Dinero.de("475.00"));
        return new ComprobanteImpreso(comprobante, datos);
    }

    @Test
    void el_pdf_tiene_el_emisor_el_numero_el_detalle_y_el_total_sin_igv() throws Exception {
        byte[] pdf = new ComprobantePdf(EMISOR, AVISO).pdf(boleta());

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String texto = texto(pdf);
        assertThat(texto).contains("RUC 20000000001").contains("AGRORUTA DEMO").contains("B771-1")
                .contains("08/11/2026").contains("CLIENTE VARIOS").contains("Sin documento");
        assertThat(texto).containsIgnoringCase("boleta de venta electr");
        assertThat(texto).contains("Or").contains("gano").contains("KGM").contains("5.00");
        // El importe de la línea es el exonerado, sin el IGV que el PDF del proveedor le suma.
        assertThat(texto).contains("475.00").doesNotContain("560.50");
        assertThat(texto).contains("CUATROCIENTOS SETENTA Y CINCO CON 00/100 SOLES");
        assertThat(texto).contains("La Boleta numero B771-1, ha sido aceptada").contains("no tiene validez tributaria");
    }

    @Test
    void una_factura_muestra_el_ruc_del_cliente() throws Exception {
        DatosComprobante datos = new DatosComprobante(TipoComprobante.FACTURA, "F001", 3, LocalDate.of(2026, 11, 8),
                new DatosComprobante.Cliente("6", "20123456789", "MERCADO CENTRAL SAC", null), oregano(),
                Dinero.de("475.00"));
        Comprobante comprobante = Comprobante.nuevo(2L, TipoComprobante.FACTURA, "F001", 3, LocalDate.of(2026, 11, 8),
                AHORA);

        String texto = texto(new ComprobantePdf(EMISOR, AVISO).pdf(new ComprobanteImpreso(comprobante, datos)));

        assertThat(texto).containsIgnoringCase("factura electr").contains("F001-3").contains("20123456789")
                .contains("MERCADO CENTRAL SAC");
    }
}
