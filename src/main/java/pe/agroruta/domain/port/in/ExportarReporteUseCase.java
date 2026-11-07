package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Archivo;
import pe.agroruta.domain.model.Formato;
import pe.agroruta.domain.model.FiltroReporte;

public interface ExportarReporteUseCase {

    Archivo exportar(FiltroReporte filtro, Formato formato, String usuario, String identificadorNegocio);
}
