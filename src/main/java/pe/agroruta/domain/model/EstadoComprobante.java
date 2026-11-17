package pe.agroruta.domain.model;

public enum EstadoComprobante {
    PENDIENTE,
    ACEPTADO,
    OBSERVADO,
    RECHAZADO,
    /** Emitido por el simulador: no se envió a SUNAT y no tiene validez tributaria. */
    SIMULADO;

    /** Ya tiene representación impresa (PDF): SUNAT lo recibió o lo emitió el simulador. */
    public boolean emitido() {
        return this == ACEPTADO || this == OBSERVADO || this == SIMULADO;
    }

    /** No hace falta volver a enviarlo ni consultarlo. */
    public boolean definitivo() {
        return this == ACEPTADO || this == SIMULADO;
    }
}
