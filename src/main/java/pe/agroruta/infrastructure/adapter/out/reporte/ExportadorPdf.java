package pe.agroruta.infrastructure.adapter.out.reporte;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import pe.agroruta.domain.model.Comparacion;
import pe.agroruta.domain.model.Dimension;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.FilaEntrega;
import pe.agroruta.domain.model.FilaRanking;
import pe.agroruta.domain.model.Indicadores;
import pe.agroruta.domain.model.PuntoSerie;
import pe.agroruta.domain.model.Reporte;
import pe.agroruta.domain.model.Tendencia;
import pe.agroruta.domain.model.Unidad;
import pe.agroruta.domain.model.Veredicto;
import pe.agroruta.domain.port.out.ExportadorReportePort;

/** Reporte para imprimir o enviar: resumen, comparación, gráfico, rankings y el detalle entrega por entrega. */
public class ExportadorPdf implements ExportadorReportePort {

    private static final Color VERDE = new Color(0x1F, 0x7A, 0x4D);
    private static final Color VERDE_SUAVE = new Color(0xE8, 0xF3, 0xEC);
    private static final Color ROJO = new Color(0xB4, 0x2B, 0x2B);
    private static final Color ROJO_SUAVE = new Color(0xFB, 0xEA, 0xEA);
    private static final Color AMBAR = new Color(0xB7, 0x79, 0x1F);
    private static final Color AMBAR_SUAVE = new Color(0xFD, 0xF3, 0xE0);
    private static final Color GRIS = new Color(0x5F, 0x6B, 0x66);
    private static final Color LINEA = new Color(0xD5, 0xDD, 0xD8);
    private static final int TOP_RANKING = 10;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(LIMA);

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
    private static final Font SECCION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, VERDE);
    private static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font TEXTO_GRIS = FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
    private static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font CELDA = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);

    @Override
    public byte[] exportar(Reporte reporte) {
        int paginas = generar(reporte, 0).paginas;
        return generar(reporte, paginas).bytes;
    }

    @Override
    public String tipoContenido() {
        return "application/pdf";
    }

    @Override
    public String extension() {
        return "pdf";
    }

    private record Resultado(byte[] bytes, int paginas) {
    }

    private Resultado generar(Reporte reporte, int totalPaginas) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4.rotate(), 36, 36, 36, 46);
        try {
            PdfWriter escritor = PdfWriter.getInstance(documento, salida);
            escritor.setPageEvent(new PiePagina(reporte, totalPaginas));
            documento.addTitle("Reporte financiero - " + reporte.negocio());
            documento.open();
            encabezado(documento, reporte);
            veredicto(documento, reporte);
            indicadores(documento, reporte);
            if (reporte.resumen().actual().entregas() > 0) {
                comparacion(documento, reporte);
                grafico(documento, reporte);
                for (Dimension dimension : Dimension.values()) {
                    ranking(documento, dimension, reporte.rankings().get(dimension));
                }
                detalle(documento, reporte.entregas());
            }
            int paginas = escritor.getPageNumber();
            documento.close();
            return new Resultado(salida.toByteArray(), paginas);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF del reporte", e);
        }
    }

    private void encabezado(Document documento, Reporte reporte) throws DocumentException, IOException {
        PdfPTable tabla = new PdfPTable(new float[]{1, 8});
        tabla.setWidthPercentage(100);
        PdfPCell logo = sinBorde(new Phrase(""));
        try (InputStream in = ExportadorPdf.class.getResourceAsStream("/static/img/logo.png")) {
            if (in != null) {
                Image imagen = Image.getInstance(in.readAllBytes());
                imagen.scaleToFit(48, 48);
                logo = new PdfPCell(imagen, false);
                logo.setBorder(Rectangle.NO_BORDER);
            }
        }
        tabla.addCell(logo);
        Phrase titulo = new Phrase();
        titulo.add(new com.lowagie.text.Chunk("Reporte financiero\n", TITULO));
        titulo.add(new com.lowagie.text.Chunk(reporte.negocio() + "\n", NEGRITA));
        titulo.add(new com.lowagie.text.Chunk(filtros(reporte), TEXTO_GRIS));
        tabla.addCell(sinBorde(titulo));
        tabla.setSpacingAfter(10);
        documento.add(tabla);
    }

    private String filtros(Reporte reporte) {
        var filtro = reporte.resumen().filtro();
        StringBuilder texto = new StringBuilder("Período: ").append(DIA.format(filtro.desde())).append(" al ")
                .append(DIA.format(filtro.hasta()));
        var anterior = reporte.resumen().filtro().periodoAnterior();
        texto.append("  ·  Comparado con: ").append(DIA.format(anterior.desde())).append(" al ")
                .append(DIA.format(anterior.hasta()));
        if (filtro.producto() != null) {
            texto.append("  ·  Producto: ").append(filtro.producto());
        }
        if (filtro.idDestino() != null) {
            texto.append("  ·  Destino: ").append(reporte.nombreDestino() != null ? reporte.nombreDestino()
                    : "#" + filtro.idDestino());
        }
        return texto.toString();
    }

    private void veredicto(Document documento, Reporte reporte) throws DocumentException {
        Veredicto veredicto = reporte.resumen().veredicto();
        Color fondo;
        Color borde;
        String titulo;
        switch (veredicto) {
            case GANANDO -> {
                fondo = VERDE_SUAVE;
                borde = VERDE;
                titulo = "Estás ganando";
            }
            case GANANDO_MENOS -> {
                fondo = AMBAR_SUAVE;
                borde = AMBAR;
                titulo = "Estás ganando, pero menos que antes";
            }
            case PERDIENDO -> {
                fondo = ROJO_SUAVE;
                borde = ROJO;
                titulo = "Estás perdiendo dinero";
            }
            default -> {
                fondo = Color.WHITE;
                borde = LINEA;
                titulo = "Sin datos";
            }
        }
        Phrase texto = new Phrase();
        texto.add(new com.lowagie.text.Chunk(titulo + "\n",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, borde)));
        texto.add(new com.lowagie.text.Chunk(reporte.resumen().explicacion(), TEXTO));
        PdfPCell celda = new PdfPCell(texto);
        celda.setBackgroundColor(fondo);
        celda.setBorderColor(borde);
        celda.setBorderWidth(1f);
        celda.setPadding(10);
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        tabla.addCell(celda);
        tabla.setSpacingAfter(10);
        documento.add(tabla);
    }

    private void indicadores(Document documento, Reporte reporte) throws DocumentException {
        Indicadores a = reporte.resumen().actual();
        PdfPTable tabla = new PdfPTable(5);
        tabla.setWidthPercentage(100);
        tabla.addCell(tarjeta("Ganancia real", soles(a.gananciaReal())));
        tabla.addCell(tarjeta("Ingreso real", soles(a.ingresoReal())));
        tabla.addCell(tarjeta("Flete", soles(a.fleteTotal())));
        tabla.addCell(tarjeta("Merma y rechazo", soles(a.perdidaMermaRechazo())));
        tabla.addCell(tarjeta("Margen", a.margenPorcentaje() == null ? "—" : a.margenPorcentaje() + " %"));
        tabla.setSpacingAfter(4);
        documento.add(tabla);
        PdfPTable cobros = new PdfPTable(3);
        cobros.setWidthPercentage(100);
        cobros.addCell(tarjeta("Entregas", String.valueOf(a.entregas())));
        cobros.addCell(tarjeta("Cobrado", soles(a.cobrado())));
        cobros.addCell(tarjeta("Por cobrar", soles(a.porCobrar())));
        cobros.setSpacingAfter(12);
        documento.add(cobros);
    }

    private PdfPCell tarjeta(String etiqueta, String valor) {
        Phrase texto = new Phrase();
        texto.add(new com.lowagie.text.Chunk(etiqueta + "\n", TEXTO_GRIS));
        texto.add(new com.lowagie.text.Chunk(valor, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.BLACK)));
        PdfPCell celda = new PdfPCell(texto);
        celda.setBorderColor(LINEA);
        celda.setPadding(8);
        return celda;
    }

    private void comparacion(Document documento, Reporte reporte) throws DocumentException {
        documento.add(seccion("Comparación con el período anterior"));
        PdfPTable tabla = new PdfPTable(new float[]{3, 2, 2, 2, 1.5f});
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String c : List.of("Concepto", "Este período", "Anterior", "Diferencia", "Cambio")) {
            tabla.addCell(cabecera(c));
        }
        for (Comparacion c : reporte.resumen().comparaciones()) {
            tabla.addCell(celda(c.concepto(), Element.ALIGN_LEFT, null));
            tabla.addCell(celda(valor(c.unidad(), c.actual()), Element.ALIGN_RIGHT, null));
            tabla.addCell(celda(valor(c.unidad(), c.anterior()), Element.ALIGN_RIGHT, null));
            Color color = c.tendencia() == Tendencia.MEJORA ? VERDE : c.tendencia() == Tendencia.EMPEORA ? ROJO : null;
            tabla.addCell(celda(valor(c.unidad(), c.diferencia()), Element.ALIGN_RIGHT, color));
            tabla.addCell(celda(c.porcentaje() == null ? "—" : c.porcentaje() + " %", Element.ALIGN_RIGHT, color));
        }
        tabla.setSpacingAfter(12);
        documento.add(tabla);
    }

    private void grafico(Document documento, Reporte reporte) throws DocumentException {
        documento.add(seccion("Ganancia en el tiempo"));
        List<PuntoSerie> serie = reporte.serie();
        PdfPCell celda = new PdfPCell(new Phrase(" "));
        celda.setFixedHeight(120);
        celda.setBorderColor(LINEA);
        celda.setCellEvent(new Barras(serie));
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        tabla.addCell(celda);
        tabla.setSpacingAfter(12);
        documento.add(tabla);
    }

    private void ranking(Document documento, Dimension dimension, List<FilaRanking> filas) throws DocumentException {
        if (filas == null || filas.isEmpty()) {
            return;
        }
        String titulo = switch (dimension) {
            case DESTINO -> "Ganancia por destino";
            case PRODUCTO -> "Ganancia por producto";
            case COMPRADOR -> "Ganancia por comprador";
        };
        documento.add(seccion(titulo));
        PdfPTable tabla = new PdfPTable(new float[]{4, 1.5f, 2, 1.5f, 1.5f});
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String c : List.of("Nombre", "Entregas", "Ganancia", "Margen", "Merma kg")) {
            tabla.addCell(cabecera(c));
        }
        for (FilaRanking f : filas.stream().limit(TOP_RANKING).toList()) {
            tabla.addCell(celda(f.nombre(), Element.ALIGN_LEFT, null));
            tabla.addCell(celda(String.valueOf(f.entregas()), Element.ALIGN_RIGHT, null));
            tabla.addCell(celda(soles(f.ganancia()), Element.ALIGN_RIGHT, f.ganancia().esNegativo() ? ROJO : null));
            tabla.addCell(celda(f.margenPorcentaje() == null ? "—" : f.margenPorcentaje() + " %",
                    Element.ALIGN_RIGHT, null));
            tabla.addCell(celda(f.mermaKg().toPlainString(), Element.ALIGN_RIGHT, null));
        }
        tabla.setSpacingAfter(12);
        documento.add(tabla);
    }

    private void detalle(Document documento, List<FilaEntrega> entregas) throws DocumentException {
        documento.add(seccion("Detalle de entregas"));
        PdfPTable tabla = new PdfPTable(new float[]{2.2f, 1, 3, 2, 2.5f, 1.6f, 2, 1.8f, 1.8f, 2, 1.2f});
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String c : List.of("Fecha", "Pedido", "Comprador", "Producto", "Destino", "Kg recibidos", "Ingreso",
                "Flete", "Pérdida", "Ganancia", "Cobrada")) {
            tabla.addCell(cabecera(c));
        }
        for (FilaEntrega f : entregas) {
            tabla.addCell(celda(FECHA_HORA.format(f.fechaEntrega()), Element.ALIGN_LEFT, null));
            tabla.addCell(celda(String.valueOf(f.idPedido()), Element.ALIGN_RIGHT, null));
            tabla.addCell(celda(f.comprador(), Element.ALIGN_LEFT, null));
            tabla.addCell(celda(f.producto(), Element.ALIGN_LEFT, null));
            tabla.addCell(celda(f.destino(), Element.ALIGN_LEFT, null));
            tabla.addCell(celda(f.kgRecibidos().toPlainString(), Element.ALIGN_RIGHT, null));
            tabla.addCell(celda(soles(f.ingresoReal()), Element.ALIGN_RIGHT, null));
            tabla.addCell(celda(soles(f.fleteTotal()), Element.ALIGN_RIGHT, null));
            tabla.addCell(celda(soles(f.perdidaMermaRechazo()), Element.ALIGN_RIGHT, null));
            tabla.addCell(celda(soles(f.gananciaReal()), Element.ALIGN_RIGHT,
                    f.gananciaReal().esNegativo() ? ROJO : null));
            tabla.addCell(celda(f.cobrada() ? "Sí" : "No", Element.ALIGN_CENTER, null));
        }
        documento.add(tabla);
    }

    private Paragraph seccion(String titulo) {
        Paragraph p = new Paragraph(titulo, SECCION);
        p.setSpacingAfter(4);
        p.setKeepTogether(true);
        return p;
    }

    private PdfPCell sinBorde(Phrase texto) {
        PdfPCell celda = new PdfPCell(texto);
        celda.setBorder(Rectangle.NO_BORDER);
        return celda;
    }

    private PdfPCell cabecera(String texto) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, CABECERA));
        celda.setBackgroundColor(VERDE);
        celda.setBorderColor(VERDE);
        celda.setPadding(4);
        return celda;
    }

    private PdfPCell celda(String texto, int alineacion, Color color) {
        Font fuente = color == null ? CELDA : FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, color);
        PdfPCell celda = new PdfPCell(new Phrase(texto == null ? "" : texto, fuente));
        celda.setHorizontalAlignment(alineacion);
        celda.setBorderColor(LINEA);
        celda.setPadding(3);
        return celda;
    }

    private static String soles(Dinero dinero) {
        return String.format(Locale.US, "S/ %,.2f", dinero.monto());
    }

    private static String valor(Unidad unidad, BigDecimal numero) {
        return switch (unidad) {
            case SOLES -> soles(new Dinero(numero));
            case KILOS -> numero.toPlainString() + " kg";
            case CANTIDAD -> numero.toPlainString();
        };
    }

    /** Barras de ganancia dibujadas dentro de una celda: verdes si se ganó, rojas si se perdió. */
    private static final class Barras implements PdfPCellEvent {

        private final List<PuntoSerie> serie;

        Barras(List<PuntoSerie> serie) {
            this.serie = serie;
        }

        @Override
        public void cellLayout(PdfPCell celda, Rectangle posicion, PdfContentByte[] lienzos) {
            if (serie.isEmpty()) {
                return;
            }
            PdfContentByte lienzo = lienzos[PdfPTable.TEXTCANVAS];
            float izquierda = posicion.getLeft() + 10;
            float ancho = posicion.getWidth() - 20;
            float abajo = posicion.getBottom() + 18;
            float alto = posicion.getHeight() - 28;
            double maximo = serie.stream().mapToDouble(p -> Math.max(0, p.ganancia().monto().doubleValue())).max()
                    .orElse(0);
            double minimo = serie.stream().mapToDouble(p -> Math.min(0, p.ganancia().monto().doubleValue())).min()
                    .orElse(0);
            double rango = maximo - minimo;
            if (rango == 0) {
                rango = 1;
            }
            float cero = abajo + (float) (-minimo / rango * alto);
            float paso = ancho / serie.size();
            float barra = Math.max(2, Math.min(paso * 0.7f, 40));
            int cadaCuantas = Math.max(1, (int) Math.ceil(serie.size() / 12.0));
            try {
                BaseFont fuente = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
                lienzo.setLineWidth(0.5f);
                lienzo.setColorStroke(LINEA);
                lienzo.moveTo(izquierda, cero);
                lienzo.lineTo(izquierda + ancho, cero);
                lienzo.stroke();
                for (int i = 0; i < serie.size(); i++) {
                    double ganancia = serie.get(i).ganancia().monto().doubleValue();
                    float x = izquierda + i * paso + (paso - barra) / 2;
                    float altura = (float) (Math.abs(ganancia) / rango * alto);
                    lienzo.setColorFill(ganancia < 0 ? ROJO : VERDE);
                    lienzo.rectangle(x, ganancia < 0 ? cero - altura : cero, barra, Math.max(altura, 0.5f));
                    lienzo.fill();
                    if (i % cadaCuantas == 0) {
                        lienzo.beginText();
                        lienzo.setFontAndSize(fuente, 6);
                        lienzo.setColorFill(GRIS);
                        lienzo.showTextAligned(PdfContentByte.ALIGN_CENTER, serie.get(i).etiqueta(),
                                x + barra / 2, posicion.getBottom() + 6, 0);
                        lienzo.endText();
                    }
                }
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException("No se pudo dibujar el gráfico", e);
            }
        }
    }

    /** Pie de cada hoja: quién y cuándo generó el reporte y el número de página. */
    private static final class PiePagina extends PdfPageEventHelper {

        private final String texto;
        private final int total;
        private BaseFont fuente;

        PiePagina(Reporte reporte, int total) {
            this.texto = "Generado el " + FECHA_HORA.format(reporte.generadoEn()) + " por " + reporte.generadoPor();
            this.total = total;
        }

        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            try {
                if (fuente == null) {
                    fuente = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
                }
                PdfContentByte lienzo = escritor.getDirectContent();
                float y = documento.bottom() - 18;
                lienzo.beginText();
                lienzo.setFontAndSize(fuente, 8);
                lienzo.setColorFill(GRIS);
                lienzo.showTextAligned(PdfContentByte.ALIGN_LEFT, texto, documento.left(), y, 0);
                lienzo.showTextAligned(PdfContentByte.ALIGN_RIGHT,
                        "Página " + escritor.getPageNumber() + " de " + (total == 0 ? escritor.getPageNumber() : total),
                        documento.right(), y, 0);
                lienzo.endText();
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException("No se pudo dibujar el pie de página", e);
            }
        }
    }
}
