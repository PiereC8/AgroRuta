package pe.agroruta.infrastructure.adapter.out.comprobante;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.lowagie.text.Chunk;
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
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.ComprobanteImpreso;
import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.port.out.RepresentacionComprobantePort;

/**
 * Representación impresa propia de la boleta o factura, con los mismos datos que se enviaron a SUNAT. Se hace aquí y
 * no se usa el PDF del proveedor porque ese suma el IGV a una línea exonerada y lleva la marca de otra empresa.
 */
public class ComprobantePdf implements RepresentacionComprobantePort {

    private static final Color VERDE = new Color(0x1F, 0x7A, 0x4D);
    private static final Color VERDE_SUAVE = new Color(0xE8, 0xF3, 0xEC);
    private static final Color AMBAR_SUAVE = new Color(0xFD, 0xF3, 0xE0);
    private static final Color AMBAR = new Color(0x8A, 0x5A, 0x12);
    private static final Color GRIS = new Color(0x5F, 0x6B, 0x66);
    private static final Color LINEA = new Color(0xD5, 0xDD, 0xD8);
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, VERDE);
    private static final Font NUMERO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
    private static final Font EMISOR = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
    private static final Font TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font TEXTO_GRIS = FontFactory.getFont(FontFactory.HELVETICA, 8, GRIS);
    private static final Font NEGRITA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);
    private static final Font CABECERA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font AVISO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, AMBAR);

    private final DatosEmisor emisor;
    private final String avisoAmbiente;

    /** avisoAmbiente: por qué este comprobante no tiene validez tributaria (simulado o ambiente beta). */
    public ComprobantePdf(DatosEmisor emisor, String avisoAmbiente) {
        this.emisor = emisor;
        this.avisoAmbiente = avisoAmbiente;
    }

    @Override
    public byte[] pdf(ComprobanteImpreso impreso) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 40, 40, 40, 40);
        Comprobante comprobante = impreso.comprobante();
        DatosComprobante datos = impreso.datos();
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle(nombreTipo(comprobante.tipoComprobante()) + " " + comprobante.numeroCompleto());
            documento.addAuthor(emisor.razonSocial());
            documento.open();
            encabezado(documento, comprobante);
            cliente(documento, datos);
            detalle(documento, datos);
            totales(documento, datos);
            estado(documento, comprobante);
            documento.close();
            return salida.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo generar el PDF del comprobante", e);
        }
    }

    private void encabezado(Document documento, Comprobante comprobante) throws DocumentException, IOException {
        PdfPTable tabla = new PdfPTable(new float[]{1.1f, 4.5f, 3.4f});
        tabla.setWidthPercentage(100);
        tabla.addCell(logo());

        Phrase datosEmisor = new Phrase();
        datosEmisor.add(new Chunk(emisor.razonSocial() + "\n", EMISOR));
        if (!emisor.nombreComercial().isBlank() && !emisor.nombreComercial().equals(emisor.razonSocial())) {
            datosEmisor.add(new Chunk(emisor.nombreComercial() + "\n", TEXTO));
        }
        datosEmisor.add(new Chunk(emisor.domicilioFiscal() + "\n", TEXTO_GRIS));
        datosEmisor.add(new Chunk(emisor.distrito() + " - " + emisor.provincia() + " - " + emisor.departamento(),
                TEXTO_GRIS));
        PdfPCell celdaEmisor = sinBorde(datosEmisor);
        celdaEmisor.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(celdaEmisor);

        Paragraph recuadro = new Paragraph();
        recuadro.setAlignment(Element.ALIGN_CENTER);
        recuadro.add(new Chunk("RUC " + emisor.ruc() + "\n", NEGRITA));
        recuadro.add(new Chunk(nombreTipo(comprobante.tipoComprobante()).toUpperCase(Locale.ROOT) + "\n", TITULO));
        recuadro.add(new Chunk(comprobante.numeroCompleto(), NUMERO));
        PdfPCell celdaNumero = new PdfPCell(recuadro);
        celdaNumero.setHorizontalAlignment(Element.ALIGN_CENTER);
        celdaNumero.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celdaNumero.setBorderColor(VERDE);
        celdaNumero.setBorderWidth(1.5f);
        celdaNumero.setPadding(10);
        tabla.addCell(celdaNumero);
        tabla.setSpacingAfter(14);
        documento.add(tabla);
    }

    private PdfPCell logo() throws IOException, DocumentException {
        try (InputStream in = ComprobantePdf.class.getResourceAsStream("/static/img/logo.png")) {
            if (in == null) {
                return sinBorde(new Phrase(""));
            }
            Image imagen = Image.getInstance(in.readAllBytes());
            imagen.scaleToFit(52, 52);
            PdfPCell celda = new PdfPCell(imagen, false);
            celda.setBorder(Rectangle.NO_BORDER);
            celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
            return celda;
        }
    }

    private void cliente(Document documento, DatosComprobante datos) throws DocumentException {
        DatosComprobante.Cliente cliente = datos.cliente();
        PdfPTable tabla = new PdfPTable(new float[]{1.6f, 4.4f, 1.6f, 2.4f});
        tabla.setWidthPercentage(100);
        fila(tabla, "Cliente", cliente.denominacion(), "Fecha de emisión", DIA.format(datos.fechaEmision()));
        fila(tabla, nombreDocumento(cliente.tipoDocumento()), "-".equals(cliente.numeroDocumento()) ? "Sin documento"
                : cliente.numeroDocumento(), "Moneda", "Soles (PEN)");
        fila(tabla, "Correo", cliente.email() == null ? "-" : cliente.email(), "Forma de pago", "Contado");
        tabla.setSpacingAfter(12);
        documento.add(tabla);
    }

    private void fila(PdfPTable tabla, String etiqueta1, String valor1, String etiqueta2, String valor2) {
        for (String[] par : new String[][]{{etiqueta1, valor1}, {etiqueta2, valor2}}) {
            PdfPCell etiqueta = sinBorde(new Phrase(par[0], TEXTO_GRIS));
            etiqueta.setPaddingBottom(4);
            tabla.addCell(etiqueta);
            PdfPCell valor = sinBorde(new Phrase(par[1], NEGRITA));
            valor.setPaddingBottom(4);
            tabla.addCell(valor);
        }
    }

    private void detalle(Document documento, DatosComprobante datos) throws DocumentException {
        PdfPTable tabla = new PdfPTable(new float[]{1.2f, 0.9f, 1.4f, 4f, 1.6f, 1.6f});
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String titulo : new String[]{"Cantidad", "U.M.", "Código", "Descripción", "Valor unitario",
                "Importe"}) {
            PdfPCell celda = new PdfPCell(new Phrase(titulo, CABECERA));
            celda.setBackgroundColor(VERDE);
            celda.setBorderColor(VERDE);
            celda.setPadding(5);
            celda.setHorizontalAlignment(titulo.startsWith("Valor") || titulo.equals("Importe") || titulo.equals(
                    "Cantidad") ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            tabla.addCell(celda);
        }
        for (DatosComprobante.Item item : datos.items()) {
            tabla.addCell(celda(cantidad(item.cantidad()), Element.ALIGN_RIGHT));
            tabla.addCell(celda(item.unidadMedida(), Element.ALIGN_LEFT));
            tabla.addCell(celda(item.codigo(), Element.ALIGN_LEFT));
            tabla.addCell(celda(item.descripcion(), Element.ALIGN_LEFT));
            tabla.addCell(celda(soles(item.valorUnitario().monto()), Element.ALIGN_RIGHT));
            tabla.addCell(celda(soles(item.total().monto()), Element.ALIGN_RIGHT));
        }
        tabla.setSpacingAfter(10);
        documento.add(tabla);
    }

    private void totales(Document documento, DatosComprobante datos) throws DocumentException {
        BigDecimal total = datos.total().monto();
        PdfPTable tabla = new PdfPTable(new float[]{6f, 2.4f, 1.6f});
        tabla.setWidthPercentage(100);
        PdfPCell letras = sinBorde(new Phrase("SON: " + MontoEnLetras.soles(total), NEGRITA));
        letras.setRowspan(4);
        letras.setVerticalAlignment(Element.ALIGN_TOP);
        tabla.addCell(letras);
        lineaTotal(tabla, "Op. exonerada", soles(total), false);
        lineaTotal(tabla, "Op. gravada", soles(BigDecimal.ZERO), false);
        lineaTotal(tabla, "IGV (18 %)", soles(BigDecimal.ZERO), false);
        lineaTotal(tabla, "Importe total", "S/ " + soles(total), true);
        tabla.setSpacingAfter(8);
        documento.add(tabla);
        documento.add(new Paragraph("Operación exonerada del IGV: productos agrícolas en estado natural (Apéndice I "
                + "de la Ley del IGV).", TEXTO_GRIS));
    }

    private void lineaTotal(PdfPTable tabla, String etiqueta, String valor, boolean destacado) {
        PdfPCell izquierda = new PdfPCell(new Phrase(etiqueta, destacado ? NEGRITA : TEXTO));
        PdfPCell derecha = new PdfPCell(new Phrase(valor, destacado ? NEGRITA : TEXTO));
        derecha.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell celda : new PdfPCell[]{izquierda, derecha}) {
            celda.setBorderColor(LINEA);
            celda.setPadding(4);
            if (destacado) {
                celda.setBackgroundColor(VERDE_SUAVE);
            }
            tabla.addCell(celda);
        }
    }

    private void estado(Document documento, Comprobante comprobante) throws DocumentException {
        Paragraph sunat = new Paragraph();
        sunat.setSpacingBefore(14);
        sunat.add(new Chunk("Estado ante SUNAT: ", NEGRITA));
        sunat.add(new Chunk(comprobante.estadoComprobante() == EstadoComprobante.OBSERVADO
                ? "Aceptado con observaciones" : "Aceptado", TEXTO));
        if (comprobante.mensajeSunat() != null && !comprobante.mensajeSunat().isBlank()) {
            sunat.add(new Chunk("\n" + comprobante.mensajeSunat(), TEXTO_GRIS));
        }
        documento.add(sunat);

        PdfPTable aviso = new PdfPTable(1);
        aviso.setWidthPercentage(100);
        aviso.setSpacingBefore(12);
        PdfPCell celda = new PdfPCell(new Phrase(avisoAmbiente, AVISO));
        celda.setBackgroundColor(AMBAR_SUAVE);
        celda.setBorderColor(AMBAR_SUAVE);
        celda.setPadding(8);
        aviso.addCell(celda);
        documento.add(aviso);

        Paragraph pie = new Paragraph("Representación impresa de la " + nombreTipo(comprobante.tipoComprobante())
                .toLowerCase(Locale.ROOT) + ".", TEXTO_GRIS);
        pie.setSpacingBefore(10);
        pie.setAlignment(Element.ALIGN_CENTER);
        documento.add(pie);
    }

    private static PdfPCell celda(String texto, int alineacion) {
        PdfPCell celda = new PdfPCell(new Phrase(texto, TEXTO));
        celda.setBorderColor(LINEA);
        celda.setPadding(5);
        celda.setHorizontalAlignment(alineacion);
        return celda;
    }

    private static PdfPCell sinBorde(Phrase frase) {
        PdfPCell celda = new PdfPCell(frase);
        celda.setBorder(Rectangle.NO_BORDER);
        return celda;
    }

    private static String nombreTipo(TipoComprobante tipo) {
        return tipo == TipoComprobante.FACTURA ? "Factura electrónica" : "Boleta de venta electrónica";
    }

    private static String nombreDocumento(String tipoDocumento) {
        return switch (tipoDocumento) {
            case "6" -> "RUC";
            case "1" -> "DNI";
            default -> "Documento";
        };
    }

    private static String soles(BigDecimal monto) {
        return formato("#,##0.00").format(monto);
    }

    private static String cantidad(BigDecimal cantidad) {
        return formato("#,##0.###").format(cantidad);
    }

    private static DecimalFormat formato(String patron) {
        return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(Locale.US));
    }
}
