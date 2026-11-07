package pe.agroruta.infrastructure.adapter.out.reporte;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.FilaEntrega;

class ExportadorCsvTest {

    private final ExportadorCsv exportador = new ExportadorCsv();

    /** Lector mínimo de CSV (RFC 4180) para comprobar que las filas no se rompen. */
    private static List<List<String>> leer(byte[] bytes) {
        String texto = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        List<List<String>> filas = new ArrayList<>();
        List<String> fila = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else if (c == '"') {
                    entreComillas = false;
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
            } else if (c == ',') {
                fila.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                fila.add(campo.toString());
                campo.setLength(0);
                filas.add(fila);
                fila = new ArrayList<>();
                i++;
            } else {
                campo.append(c);
            }
        }
        return filas;
    }

    @Test
    void el_archivo_empieza_con_la_marca_de_orden_y_la_primera_linea_son_las_cabeceras() {
        byte[] bytes = exportador.exportar(ReporteDePrueba.reporte(List.of(
                ReporteDePrueba.entrega(1, "Mercado Central", "1000.00", "200.00"))));

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        List<String> cabeceras = leer(bytes).get(0);
        assertThat(cabeceras).startsWith("Fecha", "Pedido", "Comprador", "Producto", "Destino");
        assertThat(cabeceras).contains("Ganancia real", "Pérdida por merma y rechazo", "Cobrada");
    }

    @Test
    void una_fila_por_entrega_con_decimales_con_punto_y_la_fecha_en_hora_de_lima() {
        byte[] bytes = exportador.exportar(ReporteDePrueba.reporte(List.of(
                ReporteDePrueba.entrega(7, "Mercado Central", "1000.00", "200.00"),
                ReporteDePrueba.entrega(8, "Ana Torres", "500.50", "100.25"))));

        List<List<String>> filas = leer(bytes);

        assertThat(filas).hasSize(3);
        List<String> primera = filas.get(1);
        assertThat(primera.get(0)).isEqualTo("2026-11-05 10:30");
        assertThat(primera).contains("7", "Mercado Central", "Palta Hass", "Mercado Lima", "94.500", "1000.00",
                "200.00", "800.00");
        assertThat(filas.get(2)).contains("400.25");
        assertThat(primera.get(primera.size() - 1)).isEqualTo("No");
        assertThat(filas.get(2).get(filas.get(2).size() - 1)).isEqualTo("Sí");
    }

    @Test
    void los_nombres_con_coma_comillas_o_saltos_de_linea_no_rompen_las_filas() {
        String raro = "Mercado \"El Sol\", SAC\nSucursal 2";
        byte[] bytes = exportador.exportar(ReporteDePrueba.reporte(List.of(
                ReporteDePrueba.entrega(1, raro, "1000.00", "200.00"))));

        List<List<String>> filas = leer(bytes);

        assertThat(filas).hasSize(2);
        assertThat(filas.get(1)).hasSameSizeAs(filas.get(0));
        assertThat(filas.get(1).get(2)).isEqualTo(raro);
    }

    @Test
    void las_celdas_de_texto_que_empiezan_con_igual_mas_menos_o_arroba_se_neutralizan() {
        for (String peligroso : new String[]{"=HYPERLINK(\"http://x\")", "+cmd", "-2+3", "@SUM(A1)"}) {
            FilaEntrega fila = ReporteDePrueba.entrega(1, peligroso, "1000.00", "200.00");

            List<List<String>> filas = leer(exportador.exportar(ReporteDePrueba.reporte(List.of(fila))));

            assertThat(filas.get(1).get(2)).isEqualTo("'" + peligroso);
        }
    }

    @Test
    void un_reporte_vacio_solo_trae_las_cabeceras() {
        List<List<String>> filas = leer(exportador.exportar(ReporteDePrueba.reporte(List.of())));

        assertThat(filas).hasSize(1);
    }

    @Test
    void anuncia_su_tipo_de_contenido_y_su_extension() {
        assertThat(exportador.tipoContenido()).startsWith("text/csv");
        assertThat(exportador.extension()).isEqualTo("csv");
    }
}
