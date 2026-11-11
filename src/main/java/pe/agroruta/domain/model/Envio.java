package pe.agroruta.domain.model;

import java.time.Instant;

public record Envio(Long idEnvio, Long idPedido, Long idLote, Long idVehiculo, Long idTransportista,
                    EstadoEnvio estadoEnvio, Instant fechaProgramada, Instant fechaSalida, Instant fechaEntrega,
                    String usuarioDespacho) {

    public static Envio programar(Pedido pedido, Vehiculo vehiculo, String usuarioDespacho, Instant ahora) {
        if (usuarioDespacho == null || usuarioDespacho.isBlank()) {
            throw new DominioException("El envío necesita el usuario que lo despacha");
        }
        return new Envio(null, pedido.idPedido(), pedido.idLote(), vehiculo.idVehiculo(),
                vehiculo.idTransportista(), EstadoEnvio.PROGRAMADO, ahora, null, null, usuarioDespacho);
    }

    public Envio iniciarRuta(Instant ahora) {
        exigirEstado(EstadoEnvio.PROGRAMADO, "salir en ruta");
        return new Envio(idEnvio, idPedido, idLote, idVehiculo, idTransportista, EstadoEnvio.EN_RUTA,
                fechaProgramada, ahora, null, usuarioDespacho);
    }

    public Envio marcarEntregado(Instant ahora) {
        exigirEstado(EstadoEnvio.EN_RUTA, "entregarse");
        return new Envio(idEnvio, idPedido, idLote, idVehiculo, idTransportista, EstadoEnvio.ENTREGADO,
                fechaProgramada, fechaSalida, ahora, usuarioDespacho);
    }

    /** Se cancela un envío programado o en ruta (por ejemplo, si el camión falla). */
    public Envio cancelar() {
        if (estadoEnvio != EstadoEnvio.PROGRAMADO && estadoEnvio != EstadoEnvio.EN_RUTA) {
            throw new ConflictoDominioException("El envío " + idEnvio + " está " + estadoEnvio
                    + " y solo se cancela si está programado o en ruta");
        }
        return new Envio(idEnvio, idPedido, idLote, idVehiculo, idTransportista, EstadoEnvio.CANCELADO,
                fechaProgramada, fechaSalida, fechaEntrega, usuarioDespacho);
    }

    /** Una posición solo vale si fue tomada mientras el envío estaba en ruta. */
    public boolean admitePosicionEn(Instant fecha) {
        if (estadoEnvio == EstadoEnvio.CANCELADO || fechaSalida == null || fecha.isBefore(fechaSalida)) {
            return false;
        }
        return fechaEntrega == null || !fecha.isAfter(fechaEntrega);
    }

    public Envio conId(Long nuevoIdEnvio) {
        return new Envio(nuevoIdEnvio, idPedido, idLote, idVehiculo, idTransportista, estadoEnvio,
                fechaProgramada, fechaSalida, fechaEntrega, usuarioDespacho);
    }

    private void exigirEstado(EstadoEnvio esperado, String accion) {
        if (estadoEnvio != esperado) {
            throw new ConflictoDominioException("El envío " + idEnvio + " está " + estadoEnvio
                    + " y solo puede " + accion + " desde " + esperado);
        }
    }
}
