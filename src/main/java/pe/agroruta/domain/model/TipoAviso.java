package pe.agroruta.domain.model;

import java.util.Set;

/** Cada clase de aviso por correo y a qué roles les llega. */
public enum TipoAviso {
    ENTREGA_CON_PERDIDA(Set.of(Rol.DUENO)),
    MERMA_ALTA(Set.of(Rol.DUENO)),
    PAGO_RECIBIDO(Set.of(Rol.DUENO)),
    COMPROBANTE_RECHAZADO(Set.of(Rol.DUENO)),
    COMPROBANTE_ATASCADO(Set.of(Rol.DUENO)),
    COBRO_VENCIDO(Set.of(Rol.DUENO)),
    ENVIO_DETENIDO(Set.of(Rol.OPERADOR, Rol.DUENO)),
    GPS_DESACTIVADO(Set.of(Rol.OPERADOR, Rol.DUENO)),
    ENVIO_LLEGADO(Set.of(Rol.OPERADOR)),
    RESUMEN_SEMANAL(Set.of(Rol.DUENO)),
    PRUEBA(Set.of(Rol.DUENO));

    private final Set<Rol> destinatarios;

    TipoAviso(Set<Rol> destinatarios) {
        this.destinatarios = destinatarios;
    }

    public Set<Rol> destinatarios() {
        return destinatarios;
    }
}
