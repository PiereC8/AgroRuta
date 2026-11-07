package pe.agroruta.infrastructure.adapter.out.reporte;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import pe.agroruta.domain.model.FilaEntrega;

class ExportadorPdfTest {

    private final ExportadorPdf exportador = new ExportadorPdf();

    private static String textoDe(byte[] pdf) throws IOException {
        PdfReader lector = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(lector);
        StringBuilder todo = new StringBuilder();
        for (int pagina = 1; pagina <= lector.getNumberOfPages(); pagina++) {
            todo.append(extractor.getTextFromPage(pagina)).append('\n');
        }
        return todo.toString();
    }

    @Test
    void el_pdf_es_valido_y_contiene_negocio_veredicto_y_cifras() throws IOException {
        byte[] pdf = exportador.exportar(ReporteDePrueba.reporte(List.of(
                ReporteDePrueba.entrega(1, "Mercado Central", "1000.00", "200.00"),
                ReporteDePrueba.entrega(2, "Ana Torres", "2000.00", "300.00"))));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String texto = textoDe(pdf);
        assertThat(texto).contains("Empresa Demo SAC").contains("Reporte financiero")
                .contains("dueno.empresa").contains("S/ 2,500.00").contains("Mercado Central")
                .contains("01/11/2026").contains("07/11/2026");
        assertThat(texto).containsIgnoringCase("ganando");
    }

    @Test
    void un_reporte_con_150_entregas_ocupa_varias_hojas_y_repite_el_encabezado_de_la_tabla() throws IOException {
        byte[] pdf = exportador.exportar(ReporteDePrueba.reporte(ReporteDePrueba.variasEntregas(150)));

        PdfReader lector = new PdfReader(pdf);
        int paginas = lector.getNumberOfPages();
        String ultima = new PdfTextExtractor(lector).getTextFromPage(paginas);

        assertThat(paginas).isGreaterThanOrEqualTo(3);
        assertThat(ultima).contains("Comprador").contains("Ganancia").contains("Página " + paginas + " de " + paginas);
        assertThat(ultima).contains("Comprador 150");
    }

    @Test
    void un_reporte_vacio_se_genera_sin_error_y_dice_que_no_hay_entregas() throws IOException {
        byte[] pdf = exportador.exportar(ReporteDePrueba.reporte(List.of()));

        assertThat(textoDe(pdf)).contains("No hay entregas en este período");
    }

    @Test
    void las_tildes_y_la_enie_se_ven_bien() throws IOException {
        FilaEntrega fila = ReporteDePrueba.entrega(1, "Ñandú Peña Muñoz SAC", "1000.00", "200.00");

        String texto = textoDe(exportador.exportar(ReporteDePrueba.reporte(List.of(fila))));

        assertThat(texto).contains("Ñandú Peña Muñoz SAC").contains("Pérdida");
    }

    @Test
    void anuncia_su_tipo_de_contenido_y_su_extension() {
        assertThat(exportador.tipoContenido()).isEqualTo("application/pdf");
        assertThat(exportador.extension()).isEqualTo("pdf");
    }
}
