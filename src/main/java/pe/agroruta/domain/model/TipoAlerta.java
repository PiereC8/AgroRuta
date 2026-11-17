package pe.agroruta.domain.model;

public enum TipoAlerta {
    GPS_DESACTIVADO,
    DETENIDO,
    LLEGADA,
    RETRASO,
    /** No se guarda: se calcula al consultar (envío en ruta sin posiciones recientes del conductor). */
    SIN_SENAL
}
