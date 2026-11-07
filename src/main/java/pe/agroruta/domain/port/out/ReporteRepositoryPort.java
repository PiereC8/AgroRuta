package pe.agroruta.domain.port.out;

import java.util.List;

import pe.agroruta.domain.model.FilaEntrega;
import pe.agroruta.domain.model.FiltroReporte;

/** Consultas de solo lectura para los reportes. Las sumas y las comparaciones se hacen en el dominio. */
public interface ReporteRepositoryPort {

    /** Las entregas del filtro (días completos en hora de Lima), de la más reciente a la más antigua. */
    List<FilaEntrega> entregas(FiltroReporte filtro, int limite);

    List<String> productosConEntregas();
}
