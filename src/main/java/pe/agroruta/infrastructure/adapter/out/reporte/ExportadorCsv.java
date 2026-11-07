package pe.agroruta.infrastructure.adapter.out.reporte;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import pe.agroruta.domain.model.FilaEntrega;
import pe.agroruta.domain.model.Reporte;
import pe.agroruta.domain.port.out.ExportadorReportePort;

/** Una fila por entrega, lista para abrir en Excel: UTF-8 con marca de orden, comas y decimales con punto. */
public class ExportadorCsv implements ExportadorReportePort {

    private static final List<String> CABECERAS = List.of("Fecha", "Pedido", "Comprador", "Producto", "Destino",
            "Kg despachados", "Kg recibidos", "Merma kg", "Rechazo kg", "Ingreso real", "Flete", "Ganancia real",
            "Ganancia esperada", "Pérdida por merma y rechazo", "Cobrada");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.of("America/Lima"));

    @Override
    public byte[] exportar(Reporte reporte) {
        StringBuilder texto = new StringBuilder();
        linea(texto, CABECERAS);
        for (FilaEntrega f : reporte.entregas()) {
            linea(texto, List.of(FECHA.format(f.fechaEntrega()), String.valueOf(f.idPedido()),
                    neutralizar(f.comprador()), neutralizar(f.producto()), neutralizar(f.destino()),
                    f.kgDespachados().toPlainString(), f.kgRecibidos().toPlainString(),
                    f.mermaKg().toPlainString(), f.rechazoKg().toPlainString(),
                    f.ingresoReal().monto().toPlainString(), f.fleteTotal().monto().toPlainString(),
                    f.gananciaReal().monto().toPlainString(), f.gananciaEsperada().monto().toPlainString(),
                    f.perdidaMermaRechazo().monto().toPlainString(), f.cobrada() ? "Sí" : "No"));
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        salida.writeBytes(texto.toString().getBytes(StandardCharsets.UTF_8));
        return salida.toByteArray();
    }

    @Override
    public String tipoContenido() {
        return "text/csv; charset=UTF-8";
    }

    @Override
    public String extension() {
        return "csv";
    }

    private static void linea(StringBuilder texto, List<String> celdas) {
        for (int i = 0; i < celdas.size(); i++) {
            if (i > 0) {
                texto.append(',');
            }
            texto.append(celda(celdas.get(i)));
        }
        texto.append("\r\n");
    }

    private static String celda(String valor) {
        if (valor.indexOf(',') < 0 && valor.indexOf('"') < 0 && valor.indexOf('\n') < 0 && valor.indexOf('\r') < 0) {
            return valor;
        }
        return '"' + valor.replace("\"", "\"\"") + '"';
    }

    /** Evita que Excel tome un texto como fórmula. */
    private static String neutralizar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        char primero = texto.charAt(0);
        return primero == '=' || primero == '+' || primero == '-' || primero == '@' || primero == '\t'
                || primero == '\r' ? "'" + texto : texto;
    }
}
