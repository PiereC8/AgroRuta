package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.Reporte;

/** Presenta un reporte ya calculado en un formato de archivo. Cada formato es un exportador intercambiable. */
public interface ExportadorReportePort {

    byte[] exportar(Reporte reporte);

    String tipoContenido();

    String extension();
}
